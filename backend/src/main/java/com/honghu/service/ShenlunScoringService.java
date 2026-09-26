package com.honghu.service;

import com.honghu.common.BusinessException;
import com.honghu.mapper.FrQuestionMapper;
import com.honghu.mapper.ShenlunRubricMapper;
import com.honghu.pojo.FrQuestion;
import com.honghu.pojo.ShenlunRubric;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 申论多维可解释评分。全部在本地计算，不接任何外部模型 API。
 *
 * <p>核心是「要点覆盖」：把考生作答与每个评分要点（来自真实参考答案的分点）做
 * 中文二元字组（bigram）重叠度匹配，覆盖率越高得分越高。给分是按点累加的，
 * 因此总分可解释——每个要点命中与否、覆盖率、得了多少分都会返回。
 *
 * <p>另给两个「提示维度」（字数、条理），只作反馈不计入分数，避免把与marking
 * 无关的写作特征混进官方分值。作文题（无分点，单一 holistic 要点）退化为对整篇
 * 参考答案的覆盖度评估，并明确提示不适合按点判分。
 */
@Service
@RequiredArgsConstructor
public class ShenlunScoringService {

    /** 覆盖率达到该值即视为「命中」该要点，同时给满分。 */
    private static final double HIT_THRESHOLD = 0.6;
    /** 低于该覆盖率不给分，避免蒙字堆砌骗取碎分。 */
    private static final double FLOOR_THRESHOLD = 0.15;

    private final FrQuestionMapper frQuestionMapper;
    private final ShenlunRubricMapper rubricMapper;

    public ScoreReport score(Long frQuestionId, String userAnswer) {
        FrQuestion question = frQuestionMapper.findActiveById(frQuestionId);
        if (question == null) {
            throw new BusinessException(404, "申论题不存在或已下架");
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            throw new BusinessException("作答内容不能为空");
        }
        List<ShenlunRubric> rubrics = rubricMapper.findByFrQuestion(frQuestionId);
        if (rubrics.isEmpty()) {
            throw new BusinessException("该题暂无评分标准");
        }

        Set<String> answerBigrams = bigrams(userAnswer);
        double answerLen = cleanLength(userAnswer);

        List<PointResult> pointResults = new ArrayList<>();
        double awarded = 0;
        double fullMark = 0;
        int hitCount = 0;

        for (ShenlunRubric rubric : rubrics) {
            double pointScore = rubric.getPointScore() == null ? 0 : rubric.getPointScore();
            fullMark += pointScore;

            double coverage = coverage(rubric, answerBigrams, userAnswer);
            double got;
            boolean hit;
            if (coverage >= HIT_THRESHOLD) {
                got = pointScore;
                hit = true;
            } else if (coverage >= FLOOR_THRESHOLD) {
                // 部分覆盖给部分分：按覆盖率线性折算，鼓励写全要点
                got = round1(pointScore * coverage);
                hit = false;
            } else {
                got = 0;
                hit = false;
            }
            awarded += got;
            if (hit) hitCount++;

            PointResult pr = new PointResult();
            pr.setPointOrder(rubric.getPointOrder());
            pr.setPointText(rubric.getPointText());
            pr.setPointScore(pointScore);
            pr.setCoverage(round2(coverage));
            pr.setAwarded(got);
            pr.setHit(hit);
            pointResults.add(pr);
        }

        ScoreReport report = new ScoreReport();
        report.setFrQuestionId(frQuestionId);
        report.setQuestionType(question.getFrQuestionType());
        report.setFullMark(round1(fullMark));
        report.setAwarded(round1(awarded));
        report.setScoreRate(fullMark > 0 ? round2(awarded / fullMark) : 0);
        report.setHitPoints(hitCount);
        report.setTotalPoints(rubrics.size());
        report.setPointResults(pointResults);
        report.setDimensions(dimensions(rubrics, question, answerLen, awarded, fullMark));
        report.setEssay(rubrics.size() == 1);
        return report;
    }

    /** 单个要点的覆盖率：命中任一同义表述直接判满覆盖，否则用 bigram 重叠。 */
    private double coverage(ShenlunRubric rubric, Set<String> answerBigrams, String rawAnswer) {
        List<String> synonyms = rubricMapper.findSynonyms(rubric.getRubricId());
        for (String syn : synonyms) {
            if (syn != null && !syn.isBlank() && rawAnswer.contains(syn.trim())) {
                return 1.0;
            }
        }
        Set<String> pointBigrams = bigrams(rubric.getPointText());
        if (pointBigrams.isEmpty()) {
            return 0;
        }
        int overlap = 0;
        for (String bg : pointBigrams) {
            if (answerBigrams.contains(bg)) overlap++;
        }
        return (double) overlap / pointBigrams.size();
    }

    /**
     * 三个可解释维度。要点覆盖是主分（计入 marking）；字数、条理仅作反馈。
     */
    private List<Dimension> dimensions(List<ShenlunRubric> rubrics, FrQuestion question,
                                       double answerLen, double awarded, double fullMark) {
        List<Dimension> dims = new ArrayList<>();

        dims.add(dimension("要点覆盖",
                fullMark > 0 ? awarded / fullMark : 0,
                "按参考答案分点匹配，是本题得分依据"));

        // 参考答案长度作为字数基准
        double refLen = 0;
        for (ShenlunRubric r : rubrics) refLen += cleanLength(r.getPointText());
        double lenRatio = refLen > 0 ? Math.min(1.0, answerLen / refLen) : 0;
        dims.add(dimension("字数达标", lenRatio,
                String.format("作答约 %.0f 字，参考约 %.0f 字（仅提示，不计分）", answerLen, refLen)));

        // 条理：是否使用分点/分段表达
        boolean structured = question != null
                && (containsEnumeration(question.getAnswer()) || rubrics.size() > 1);
        double structScore = structured ? 1.0 : 0.5;
        dims.add(dimension("结构条理", structScore,
                "是否分点、分层表达（仅提示，不计分）"));

        return dims;
    }

    private static boolean containsEnumeration(String text) {
        return text != null && text.matches("(?s).*[(（]\\s*[0-9一二三四五六七八九十]+\\s*[)）].*");
    }

    private static Dimension dimension(String name, double ratio, String note) {
        Dimension d = new Dimension();
        d.setName(name);
        d.setRatio(round2(Math.max(0, Math.min(1, ratio))));
        d.setNote(note);
        return d;
    }

    /** 中文二元字组：只保留 CJK 字符，相邻两字成一组。忽略标点空白。 */
    static Set<String> bigrams(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) return result;
        StringBuilder cjk = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (isCjk(cp)) cjk.appendCodePoint(cp);
        });
        for (int i = 0; i + 1 < cjk.length(); i++) {
            result.add(cjk.substring(i, i + 2));
        }
        // 单字答案（极短）也保留单字，避免 bigram 为空
        if (result.isEmpty() && cjk.length() == 1) {
            result.add(cjk.toString());
        }
        return result;
    }

    private static double cleanLength(String text) {
        if (text == null) return 0;
        return text.codePoints().filter(ShenlunScoringService::isCjk).count();
    }

    private static boolean isCjk(int cp) {
        return cp >= 0x4E00 && cp <= 0x9FFF;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ---------- 传输对象 ----------

    @Data
    public static class ScoreReport {
        private Long frQuestionId;
        private String questionType;
        private double fullMark;
        private double awarded;
        private double scoreRate;
        private int hitPoints;
        private int totalPoints;
        private boolean essay;
        private List<PointResult> pointResults;
        private List<Dimension> dimensions;
    }

    @Data
    public static class PointResult {
        private int pointOrder;
        private String pointText;
        private double pointScore;
        private double coverage;
        private double awarded;
        private boolean hit;
    }

    @Data
    public static class Dimension {
        private String name;
        private double ratio;
        private String note;
    }
}
