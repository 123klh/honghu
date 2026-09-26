package com.honghu.service;

import com.honghu.common.BusinessException;
import com.honghu.mapper.ExamQuestionMapper;
import com.honghu.mapper.ExamScoreMapper;
import com.honghu.mapper.PaperMapper;
import com.honghu.mapper.QuestionMapper;
import com.honghu.pojo.ExamQuestion;
import com.honghu.pojo.ExamScore;
import com.honghu.pojo.Paper;
import com.honghu.pojo.Question;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 整卷考试。
 *
 * <p>开考时把题目集合、顺序和正确答案写入 exam_question 快照，提交时只认快照：
 * 管理员在考试进行中改题、下架题目，都不会影响已经开考的这一场。
 * 分母也取自快照（整卷题量），只答一题答对不再算满分。
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final PaperMapper paperMapper;
    private final QuestionMapper questionMapper;
    private final ExamScoreMapper examScoreMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final PracticeService practiceService;

    @Transactional
    public ExamSession start(Long userId, Long paperId) {
        Paper paper = paperMapper.findActiveById(paperId);
        if (paper == null) throw new BusinessException(404, "试卷不存在");
        List<Long> ids = questionMapper.findIdsByPaper(paperId);
        if (ids.isEmpty()) throw new BusinessException("该试卷暂无题目");

        Map<Long, Question> byId = new HashMap<>();
        questionMapper.findByIds(ids).forEach(question -> byId.put(question.getQuestionId(), question));
        List<PracticeService.QuestionView> questions = new ArrayList<>();
        for (Long id : ids) {
            Question question = byId.get(id);
            if (question != null) questions.add(PracticeService.QuestionView.of(question));
        }

        ExamScore score = new ExamScore();
        score.setPaperId(paperId);
        score.setUserId(userId);
        // 开考时不写 0 分：avg_score 触发器会把它当成一次 0 分成绩，
        // 「开考即拉低试卷均分」。未交卷就是没有分数，留 NULL。
        score.setScore(null);
        score.setTotalScore(100.0);
        score.setStartTime(LocalDateTime.now());
        score.setStatus("ONGOING");
        examScoreMapper.insert(score);

        List<ExamQuestion> snapshot = new ArrayList<>(questions.size());
        int order = 1;
        for (PracticeService.QuestionView view : questions) {
            Question question = byId.get(view.getQuestionId());
            ExamQuestion row = new ExamQuestion();
            row.setExamId(score.getExamId());
            row.setQuestionId(view.getQuestionId());
            row.setQuestionOrder(order++);
            row.setAnswerSnapshot(question == null ? null : question.getAnswer());
            snapshot.add(row);
        }
        examQuestionMapper.insertBatch(snapshot);

        ExamSession session = new ExamSession();
        session.setExamId(score.getExamId());
        session.setPaper(paper);
        session.setQuestions(questions);
        session.setStartedAt(score.getStartTime());
        return session;
    }

    @Transactional
    public ExamResult submit(Long userId, Long examId, List<AnswerSubmission> submissions,
                             Integer duration) {
        // 加行锁读取：并发的第二次提交会在此阻塞，拿到锁时状态已是 SUBMITTED。
        ExamScore score = examScoreMapper.findByIdForUpdate(examId);
        if (score == null || !userId.equals(score.getUserId())) {
            throw new BusinessException(404, "考试不存在");
        }
        if (!"ONGOING".equals(score.getStatus())) {
            throw new BusinessException("考试已提交");
        }
        if (submissions == null || submissions.isEmpty()) {
            throw new BusinessException("答题内容不能为空");
        }

        // 快照是这场考试的唯一题目依据：不在快照里的 questionId 一律拒收，
        // 否则任何人都能提交别的试卷的题目来刷分。
        Map<Long, ExamQuestion> allowed = new LinkedHashMap<>();
        for (ExamQuestion row : examQuestionMapper.findByExam(examId)) {
            allowed.put(row.getQuestionId(), row);
        }
        if (allowed.isEmpty()) {
            throw new BusinessException("该场考试缺少题目快照，无法判分");
        }

        int correct = 0;
        Set<Long> submitted = new HashSet<>();
        List<PracticeService.AnswerResult> details = new ArrayList<>();
        for (AnswerSubmission answer : submissions) {
            ExamQuestion snapshot = allowed.get(answer.questionId());
            if (snapshot == null) {
                throw new BusinessException("题目不属于本场考试：" + answer.questionId());
            }
            if (!submitted.add(answer.questionId())) continue;
            PracticeService.AnswerResult result = practiceService.submitExamAnswer(
                    userId, examId, answer.questionId(), answer.userAnswer(),
                    answer.duration(), snapshot.getAnswerSnapshot());
            if (result.isCorrect()) correct++;
            details.add(result);
        }

        // 分母是整卷题量，不是本次提交的题数：漏答等同于答错，
        // 否则「只答一题且答对」会拿到 100 分。
        int total = allowed.size();
        double value = correct * 100.0 / total;
        score.setScore(value);
        score.setTotalScore(100.0);
        score.setDuration(duration);
        score.setSubmitTime(LocalDateTime.now());
        if (examScoreMapper.submitIfOngoing(score) == 0) {
            throw new BusinessException("考试已提交");
        }
        score.setStatus("SUBMITTED");

        ExamResult result = new ExamResult();
        result.setExamId(examId);
        result.setCorrectCount(correct);
        result.setAnsweredCount(details.size());
        result.setTotalCount(total);
        result.setScore(value);
        result.setAnswers(details);
        return result;
    }

    public List<ExamScore> history(Long userId) {
        return examScoreMapper.findByUser(userId);
    }

    public record AnswerSubmission(Long questionId, String userAnswer, Integer duration) {}

    @Data
    public static class ExamSession {
        private Long examId;
        private Paper paper;
        private LocalDateTime startedAt;
        private List<PracticeService.QuestionView> questions;
    }

    @Data
    public static class ExamResult {
        private Long examId;
        private int correctCount;

        /** 实际作答题数，与 totalCount（整卷题量）区分开 */
        private int answeredCount;
        private int totalCount;
        private double score;
        private List<PracticeService.AnswerResult> answers;
    }
}
