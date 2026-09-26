package com.honghu.service;

import com.honghu.common.BusinessException;
import com.honghu.mapper.*;
import com.honghu.pojo.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 练习与判分。
 *
 * <p>抽题不使用 ORDER BY RAND()（原论文自述该写法在大数据量下有性能问题）：
 * 改为只取候选主键池，在应用层洗牌后按 ID 批量回表。ID 池走覆盖索引，
 * 洗牌保证同一次练习内不重复出题（原论文未处理重复问题）。
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 单次练习/考试的题量上限，防止请求把整个题库拉出来 */
    private static final int MAX_BATCH = 200;

    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final WrongBookMapper wrongBookMapper;
    private final QuestionDifficultyMapper questionDifficultyMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final UserMasteryMapper userMasteryMapper;
    private final MasteryService masteryService;

    // ---------- 模块刷题 ----------

    /** 按题型抽题。返回的题目不含答案与解析，避免前端直接读到正确答案。 */
    public PracticeSession startModulePractice(String questionType, int count) {
        int size = normalizeCount(count);
        List<Long> pool = questionMapper.findIdsByType(questionType);
        if (pool.isEmpty()) {
            throw new BusinessException("该模块暂无题目：" + questionType);
        }
        List<Long> picked = sample(pool, size);
        List<Question> questions = loadInOrder(picked);

        PracticeSession session = new PracticeSession();
        session.setQuestionType(questionType);
        session.setTotalCount(questions.size());
        session.setAvailableCount(pool.size());
        session.setQuestions(questions.stream().map(QuestionView::of).toList());
        return session;
    }

    /**
     * 个性化组卷：由掌握度模型驱动，三档位只是不同的目标难度偏移。
     * 优先取「到期复习」的知识点，不足再按掌握度排序补齐。
     */
    public PracticeSession startPersonalizedPractice(Long userId, String mode, int count) {
        int size = normalizeCount(count);
        double offset = switch (mode == null ? "WEAK" : mode.toUpperCase()) {
            // 薄弱倾向：出比当前水平略简单的题，先把基础补上
            case "WEAK" -> -0.5;
            // 重点突破：出与当前水平相当的题，信息量最大
            case "FOCUS" -> 0.0;
            // 保分倾向：出略难的题，冲刺上限
            case "SECURE" -> 0.5;
            default -> throw new BusinessException("未知的组卷档位：" + mode);
        };

        List<UserMastery> due = userMasteryMapper.findDueForReview(userId, 12);
        List<UserMastery> weakest = userMasteryMapper.findWeakest(userId, 12);

        // 到期复习的排在前面，其余按掌握度从低到高补齐，去重保持顺序
        LinkedHashMap<Long, UserMastery> byKp = new LinkedHashMap<>();
        due.forEach(m -> byKp.put(m.getKpId(), m));
        weakest.forEach(m -> byKp.putIfAbsent(m.getKpId(), m));

        List<Question> selected = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        for (UserMastery mastery : byKp.values()) {
            if (selected.size() >= size) {
                break;
            }
            double targetDifficulty =
                    (mastery.getMasteryTheta() == null ? 0.0 : mastery.getMasteryTheta()) + offset;
            List<Long> pool = knowledgePointMapper.findQuestionIdsByKp(mastery.getKpId());
            pool.removeIf(used::contains);
            if (pool.isEmpty()) {
                continue;
            }
            // 每个知识点最多取总量的 1/3，保证组卷覆盖面
            int quota = Math.max(1, Math.min(size / 3, size - selected.size()));
            for (Question q : pickNearestDifficulty(pool, targetDifficulty, quota)) {
                if (used.add(q.getQuestionId())) {
                    selected.add(q);
                }
            }
        }

        // 新用户还没有任何掌握度记录，退化为全库随机，保证功能可用
        if (selected.size() < size) {
            List<Long> fallback = new ArrayList<>(questionMapper.findAllActiveIds());
            fallback.removeIf(used::contains);
            for (Question q : loadInOrder(sample(fallback, size - selected.size()))) {
                if (used.add(q.getQuestionId())) {
                    selected.add(q);
                }
            }
        }

        PracticeSession session = new PracticeSession();
        session.setQuestionType("PERSONALIZED:" + mode);
        session.setTotalCount(selected.size());
        session.setAvailableCount(selected.size());
        session.setQuestions(selected.stream().map(QuestionView::of).toList());
        return session;
    }

    /**
     * 从候选池里挑难度最接近目标的题。先随机取一个不超过 300 的子池再排序，
     * 避免热门知识点下每次都返回同样那几道题。
     */
    private List<Question> pickNearestDifficulty(List<Long> pool, double target, int quota) {
        List<Long> candidates = sample(pool, Math.min(pool.size(), 300));
        List<Question> questions = questionMapper.findByIds(candidates);
        if (questions.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> difficulties = new HashMap<>();
        for (QuestionDifficulty qd : questionDifficultyMapper.findByQuestionIds(candidates)) {
            Double value = qd.getDifficultyValue() != null
                    ? qd.getDifficultyValue()
                    : (qd.getSourceAccuracyRate() != null
                        ? MasteryService.difficultyFromAccuracy(qd.getSourceAccuracyRate())
                        : null);
            if (value != null) {
                difficulties.put(qd.getQuestionId(), value);
            }
        }
        questions.sort(Comparator.comparingDouble(
                q -> Math.abs(difficulties.getOrDefault(q.getQuestionId(), 0.0) - target)));
        return questions.subList(0, Math.min(quota, questions.size()));
    }

    // ---------- 判分 ----------

    /** 单题即时判分（模块刷题场景），同时更新错题本与掌握度。 */
    @Transactional
    public AnswerResult submitAnswer(Long userId, Long questionId, String userAnswer, Integer duration) {
        return submitAnswer(userId, null, questionId, userAnswer, duration);
    }

    /**
     * 整卷考试答题，记录 exam_id 并复用即时判分、错题本和掌握度更新。
     *
     * @param snapshotAnswer 开考快照里的正确答案；非空时以它为判分依据，
     *                       这样管理员在考试进行中改动题目也不会改变判分结果
     */
    @Transactional
    public AnswerResult submitExamAnswer(Long userId, Long examId, Long questionId,
                                         String userAnswer, Integer duration,
                                         String snapshotAnswer) {
        return submitAnswer(userId, examId, questionId, userAnswer, duration, snapshotAnswer);
    }

    private AnswerResult submitAnswer(Long userId, Long examId, Long questionId,
                                      String userAnswer, Integer duration) {
        return submitAnswer(userId, examId, questionId, userAnswer, duration, null);
    }

    private AnswerResult submitAnswer(Long userId, Long examId, Long questionId,
                                      String userAnswer, Integer duration,
                                      String snapshotAnswer) {
        Question question = questionMapper.findById(questionId);
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }

        String authority = snapshotAnswer != null && !snapshotAnswer.isBlank()
                ? snapshotAnswer
                : question.getAnswer();
        boolean correct = isCorrect(authority, userAnswer);

        AnswerRecord record = new AnswerRecord();
        record.setExamId(examId);
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setUserAnswer(userAnswer);
        record.setIsCorrect(correct);
        // score_rate 专用于申论按点给分的得分率；客观题只有对错，
        // 写 1.0/0.0 会把客观题混进申论得分率统计里。
        record.setScoreRate(null);
        record.setDuration(duration);
        record.setSubject(question.getSubject());
        record.setReviewStatus(0);
        answerRecordMapper.insert(record);

        syncWrongBook(userId, question, correct);
        masteryService.recordAnswer(userId, questionId, correct);

        AnswerResult result = new AnswerResult();
        result.setQuestionId(questionId);
        result.setCorrect(correct);
        result.setCorrectAnswer(authority);
        result.setExplanation(question.getExplanation());
        QuestionDifficulty qd = questionDifficultyMapper.findByQuestionId(questionId);
        if (qd != null) {
            result.setSourceAccuracyRate(qd.getSourceAccuracyRate());
            result.setCommonMistake(qd.getCommonMistake());
        }
        return result;
    }

    /**
     * 答案比对。题库里多选题的答案形如 "ABD"，顺序不应影响判定，
     * 因此按字符集合比较而不是字符串相等。
     */
    static boolean isCorrect(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalizeAnswer(expected).equals(normalizeAnswer(actual));
    }

    private static String normalizeAnswer(String answer) {
        return answer.toUpperCase()
                .chars()
                .filter(c -> c >= 'A' && c <= 'Z')
                .distinct()
                .sorted()
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * 错题本状态流转：答错则加入或退回 WRONG；答对且已在错题本中则标记 MASTERED。
     * 显式建表比原论文靠查询推导更容易表达「已掌握」这个状态。
     */
    private void syncWrongBook(Long userId, Question question, boolean correct) {
        WrongBook existing = wrongBookMapper.findByUserAndQuestion(
                userId, question.getQuestionId(), "OBJECTIVE");

        if (!correct) {
            if (existing == null) {
                WrongBook wb = new WrongBook();
                wb.setUserId(userId);
                wb.setQuestionId(question.getQuestionId());
                wb.setQuestionType("OBJECTIVE");
                wb.setStatus("WRONG");
                wb.setRetryCount(0);
                wrongBookMapper.insert(wb);
            } else {
                wrongBookMapper.markWrongAgain(existing.getId());
            }
        } else if (existing != null && "WRONG".equals(existing.getStatus())) {
            wrongBookMapper.markMastered(existing.getId(), LocalDateTime.now());
        }
    }

    // ---------- 错题本 ----------

    public List<Question> listWrongQuestions(Long userId) {
        List<Long> ids = wrongBookMapper.findWrongQuestionIds(userId);
        return ids.isEmpty() ? List.of() : questionMapper.findByIds(ids);
    }

    /** 错题重练：只从错题本里抽题，同样避免重复。 */
    public PracticeSession startWrongBookPractice(Long userId, int count) {
        List<Long> ids = wrongBookMapper.findWrongQuestionIds(userId);
        if (ids.isEmpty()) {
            throw new BusinessException("错题本为空，先去刷题吧");
        }
        List<Long> picked = sample(ids, normalizeCount(count));
        PracticeSession session = new PracticeSession();
        session.setQuestionType("WRONG_BOOK");
        session.setAvailableCount(ids.size());
        List<Question> questions = loadInOrder(picked);
        session.setTotalCount(questions.size());
        session.setQuestions(questions.stream().map(QuestionView::of).toList());
        return session;
    }

    @Transactional
    public void markMastered(Long userId, Long questionId) {
        WrongBook wb = wrongBookMapper.findByUserAndQuestion(userId, questionId, "OBJECTIVE");
        if (wb == null) {
            throw new BusinessException(404, "该题不在错题本中");
        }
        wrongBookMapper.markMastered(wb.getId(), LocalDateTime.now());
    }

    public WrongBookStats wrongBookStats(Long userId) {
        WrongBookStats stats = new WrongBookStats();
        stats.setWrongCount(wrongBookMapper.countWrongByUser(userId));
        stats.setMasteredCount(wrongBookMapper.countMasteredByUser(userId));
        return stats;
    }

    // ---------- 抽样工具 ----------

    /**
     * Fisher-Yates 部分洗牌：只洗前 n 个位置，O(n) 而非 O(池大小)。
     * 返回的列表元素必然互不重复，这是「同一场考试不出重复题」的保证。
     */
    private List<Long> sample(List<Long> pool, int n) {
        if (pool.isEmpty()) {
            return List.of();
        }
        List<Long> copy = new ArrayList<>(pool);
        int size = Math.min(n, copy.size());
        for (int i = 0; i < size; i++) {
            int j = i + RANDOM.nextInt(copy.size() - i);
            Collections.swap(copy, i, j);
        }
        return new ArrayList<>(copy.subList(0, size));
    }

    /** 按给定 ID 顺序返回题目（IN 查询的结果顺序不保证与 ID 列表一致）。 */
    private List<Question> loadInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> byId = new HashMap<>();
        questionMapper.findByIds(ids).forEach(q -> byId.put(q.getQuestionId(), q));
        List<Question> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Question q = byId.get(id);
            if (q != null) {
                ordered.add(q);
            }
        }
        return ordered;
    }

    private int normalizeCount(int count) {
        if (count <= 0) {
            throw new BusinessException("题目数量必须大于 0");
        }
        return Math.min(count, MAX_BATCH);
    }

    // ---------- 传输对象 ----------

    /** 下发给前端的题目视图：刻意不含 answer / explanation。 */
    @Data
    public static class QuestionView {
        private Long questionId;
        private String questionCode;
        private String subject;
        private String questionType;
        private String questionStem;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private Boolean enclosure;

        static QuestionView of(Question q) {
            QuestionView v = new QuestionView();
            v.questionId = q.getQuestionId();
            v.questionCode = q.getQuestionCode();
            v.subject = q.getSubject();
            v.questionType = q.getQuestionType();
            v.questionStem = q.getQuestionStem();
            v.optionA = q.getOptionA();
            v.optionB = q.getOptionB();
            v.optionC = q.getOptionC();
            v.optionD = q.getOptionD();
            v.enclosure = q.getEnclosure();
            return v;
        }
    }

    @Data
    public static class PracticeSession {
        private String questionType;
        private int totalCount;
        private int availableCount;
        private List<QuestionView> questions;
    }

    @Data
    public static class AnswerResult {
        private Long questionId;
        private boolean correct;
        private String correctAnswer;
        private String explanation;
        private Double sourceAccuracyRate;
        private String commonMistake;
    }

    @Data
    public static class WrongBookStats {
        private int wrongCount;
        private int masteredCount;
    }
}
