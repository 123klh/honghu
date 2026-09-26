package com.honghu.service;

import com.honghu.mapper.KnowledgePointMapper;
import com.honghu.mapper.QuestionDifficultyMapper;
import com.honghu.mapper.UserMasteryMapper;
import com.honghu.pojo.QuestionDifficulty;
import com.honghu.pojo.UserMastery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 掌握度模型：IRT/Elo 双向更新 + SM-2 间隔重复。
 *
 * <p>取代原论文「按题型正确率算固定权重 w = 1 - accuracy/100」的做法。那种做法有两个问题：
 * 一是粒度太粗（只到题型，不到知识点），二是不区分「答对一道难题」和「答对一道简单题」。
 *
 * <p>这里对每个 (用户, 知识点) 维护能力值 θ，对每道题维护难度 b，都在 logit 尺度上。
 * 单参数 IRT 下答对概率 P = 1 / (1 + e^(b - θ))。答题后按实际结果与预测的偏差更新：
 * θ' = θ + K * (实际 - P)，b' = b - K_b * (实际 - P)。答对一道预测会做错的题，θ 涨得多；
 * 答对一道本就该做对的题，θ 几乎不动。这正是 Elo 的思想。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasteryService {

    /** 用户能力更新步长。取 0.24 使约 15 题内可从 0 收敛到 ±1.5 的合理区间 */
    private static final double K_THETA = 0.24;

    /** 题目难度更新步长。远小于 K_THETA：题目难度是群体属性，不应被单个用户带偏 */
    private static final double K_DIFFICULTY = 0.02;

    private static final double DEFAULT_THETA = 0.0;
    private static final double DEFAULT_DIFFICULTY = 0.0;
    private static final double DEFAULT_EF = 2.5;

    /** θ 截断范围，防止连续答对/答错把能力值推到无意义的极端 */
    private static final double THETA_MIN = -4.0;
    private static final double THETA_MAX = 4.0;

    private final UserMasteryMapper userMasteryMapper;
    private final QuestionDifficultyMapper questionDifficultyMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    /**
     * 一次作答后同时更新用户能力与题目难度。
     * 题目可能挂多个知识点，每个都更新（能力在知识点间不共享）。
     */
    @Transactional
    public void recordAnswer(Long userId, Long questionId, boolean correct) {
        double difficulty = resolveDifficulty(questionId);
        List<Long> kpIds = knowledgePointMapper.findKpIdsByQuestion(questionId);

        if (kpIds.isEmpty()) {
            // 题目未挂知识点时只更新题目难度，避免静默丢弃这次作答的信息
            updateDifficulty(questionId, difficulty, DEFAULT_THETA, correct);
            return;
        }

        double avgTheta = 0;
        for (Long kpId : kpIds) {
            avgTheta += updateTheta(userId, kpId, difficulty, correct);
        }
        avgTheta /= kpIds.size();

        updateDifficulty(questionId, difficulty, avgTheta, correct);
    }

    /** 更新单个知识点的 θ 与 SM-2 复习计划，返回更新前的 θ（用于难度反向更新）。 */
    private double updateTheta(Long userId, Long kpId, double difficulty, boolean correct) {
        UserMastery mastery = userMasteryMapper.findByUserAndKp(userId, kpId);
        boolean isNew = mastery == null;

        if (isNew) {
            mastery = new UserMastery();
            mastery.setUserId(userId);
            mastery.setKpId(kpId);
            mastery.setMasteryTheta(DEFAULT_THETA);
            mastery.setSm2Interval(0);
            mastery.setSm2Ef(DEFAULT_EF);
        }

        double theta = mastery.getMasteryTheta() == null ? DEFAULT_THETA : mastery.getMasteryTheta();
        double predicted = probability(theta, difficulty);
        double actual = correct ? 1.0 : 0.0;
        double newTheta = clamp(theta + K_THETA * (actual - predicted), THETA_MIN, THETA_MAX);

        applySm2(mastery, correct, predicted);

        mastery.setMasteryTheta(newTheta);
        mastery.setLastAnsweredAt(LocalDateTime.now());

        if (isNew) {
            userMasteryMapper.insert(mastery);
        } else {
            userMasteryMapper.update(mastery);
        }
        return theta;
    }

    /**
     * SM-2 间隔重复。原始 SM-2 要求用户自评 0-5 的回忆质量，刷题场景下没有这个输入，
     * 这里由「是否答对」和「这道题对该用户有多难」推导：答对且题目偏难 → 质量高；
     * 答对但题目很简单 → 质量中等；答错 → 质量低，间隔重置。
     */
    private void applySm2(UserMastery mastery, boolean correct, double predicted) {
        int quality;
        if (correct) {
            // predicted 越低说明这题越超出当前水平，答对的信息量越大
            quality = predicted < 0.5 ? 5 : (predicted < 0.8 ? 4 : 3);
        } else {
            quality = predicted > 0.5 ? 1 : 2;
        }

        double ef = mastery.getSm2Ef() == null ? DEFAULT_EF : mastery.getSm2Ef();
        int interval = mastery.getSm2Interval() == null ? 0 : mastery.getSm2Interval();

        if (quality < 3) {
            // 答错：间隔归零，隔天必须复习
            interval = 1;
        } else {
            ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
            ef = clamp(ef, 1.3, 2.5);
            interval = switch (interval) {
                case 0 -> 1;
                case 1 -> 6;
                default -> (int) Math.round(interval * ef);
            };
            interval = Math.min(interval, 180);
        }

        mastery.setSm2Ef(ef);
        mastery.setSm2Interval(interval);
        mastery.setReviewDueAt(LocalDateTime.now().plusDays(interval));
    }

    private void updateDifficulty(Long questionId, double difficulty, double theta, boolean correct) {
        double predicted = probability(theta, difficulty);
        double actual = correct ? 1.0 : 0.0;
        // 大家都答对 → 题目变简单（b 下降）；都答错 → 题目变难
        double newDifficulty = clamp(difficulty - K_DIFFICULTY * (actual - predicted), THETA_MIN, THETA_MAX);

        QuestionDifficulty existing = questionDifficultyMapper.findByQuestionId(questionId);
        if (existing == null) {
            QuestionDifficulty qd = new QuestionDifficulty();
            qd.setQuestionId(questionId);
            qd.setDifficultyValue(newDifficulty);
            questionDifficultyMapper.insert(qd);
        } else {
            questionDifficultyMapper.updateDifficulty(questionId, newDifficulty);
        }
    }

    /**
     * 取题目难度 b。优先用已维护的值；没有则从题源真实正确率换算
     * （b = -logit(正确率)，正确率 87% → b ≈ -1.90，即偏简单）；都没有则取默认 0。
     */
    private double resolveDifficulty(Long questionId) {
        QuestionDifficulty qd = questionDifficultyMapper.findByQuestionId(questionId);
        if (qd == null) {
            return DEFAULT_DIFFICULTY;
        }
        if (qd.getDifficultyValue() != null) {
            return qd.getDifficultyValue();
        }
        if (qd.getSourceAccuracyRate() != null) {
            return difficultyFromAccuracy(qd.getSourceAccuracyRate());
        }
        return DEFAULT_DIFFICULTY;
    }

    /** 由正确率反推 IRT 难度：accuracy = 1/(1+e^b) ⇒ b = ln((1-acc)/acc) */
    public static double difficultyFromAccuracy(double accuracy) {
        double acc = clamp(accuracy, 0.02, 0.98);
        return Math.log((1 - acc) / acc);
    }

    /** 单参数 IRT 答对概率 */
    public static double probability(double theta, double difficulty) {
        return 1.0 / (1.0 + Math.exp(difficulty - theta));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
