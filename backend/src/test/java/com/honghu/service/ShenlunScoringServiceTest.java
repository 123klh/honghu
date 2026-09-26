package com.honghu.service;

import com.honghu.common.BusinessException;
import com.honghu.mapper.FrQuestionMapper;
import com.honghu.mapper.ShenlunRubricMapper;
import com.honghu.pojo.FrQuestion;
import com.honghu.pojo.ShenlunRubric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShenlunScoringServiceTest {

    private final FrQuestionMapper frQuestionMapper = mock(FrQuestionMapper.class);
    private final ShenlunRubricMapper rubricMapper = mock(ShenlunRubricMapper.class);
    private final ShenlunScoringService service = new ShenlunScoringService(frQuestionMapper, rubricMapper);

    /** 抄全参考答案要点应接近满分，且每个要点都命中。 */
    @Test
    void coveringAllRubricPointsScoresNearFull() {
        FrQuestion q = frQuestion(1L, 20);
        when(frQuestionMapper.findActiveById(1L)).thenReturn(q);
        when(rubricMapper.findByFrQuestion(1L)).thenReturn(List.of(
                rubric(1, "立即暂停使用和销售含PPA的药品制剂", 10.0),
                rubric(2, "药品管理部门及新闻媒体应耐心解答群众咨询", 10.0)));
        when(rubricMapper.findSynonyms(anyLong())).thenReturn(List.of());

        String answer = "立即暂停使用和销售含PPA的药品制剂。"
                + "药品管理部门及新闻媒体应耐心解答群众咨询。";
        ShenlunScoringService.ScoreReport report = service.score(1L, answer);

        assertThat(report.getFullMark()).isEqualTo(20.0);
        assertThat(report.getHitPoints()).isEqualTo(2);
        assertThat(report.getAwarded()).isEqualTo(20.0);
        assertThat(report.getScoreRate()).isEqualTo(1.0);
    }

    /** 完全离题应得 0 分，且没有要点命中。 */
    @Test
    void offTopicAnswerScoresZero() {
        FrQuestion q = frQuestion(1L, 20);
        when(frQuestionMapper.findActiveById(1L)).thenReturn(q);
        when(rubricMapper.findByFrQuestion(1L)).thenReturn(List.of(
                rubric(1, "立即暂停使用和销售含PPA的药品制剂", 20.0)));
        when(rubricMapper.findSynonyms(anyLong())).thenReturn(List.of());

        ShenlunScoringService.ScoreReport report = service.score(1L,
                "今天天气很好我去公园散步看到许多花草树木心情十分愉快");

        assertThat(report.getAwarded()).isEqualTo(0.0);
        assertThat(report.getHitPoints()).isEqualTo(0);
    }

    /** 命中同义表述即判满覆盖。 */
    @Test
    void synonymMatchGivesFullCoverage() {
        FrQuestion q = frQuestion(1L, 10);
        when(frQuestionMapper.findActiveById(1L)).thenReturn(q);
        when(rubricMapper.findByFrQuestion(1L)).thenReturn(List.of(
                rubric(1, "完善相关法律法规体系建设", 10.0)));
        when(rubricMapper.findSynonyms(anyLong())).thenReturn(List.of("健全法治"));

        ShenlunScoringService.ScoreReport report = service.score(1L, "应当健全法治");

        assertThat(report.getHitPoints()).isEqualTo(1);
        assertThat(report.getAwarded()).isEqualTo(10.0);
    }

    @Test
    void emptyAnswerIsRejected() {
        when(frQuestionMapper.findActiveById(1L)).thenReturn(frQuestion(1L, 20));
        assertThatThrownBy(() -> service.score(1L, "   "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unknownQuestionIsRejected() {
        when(frQuestionMapper.findActiveById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.score(9L, "answer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    private static FrQuestion frQuestion(long id, int points) {
        FrQuestion q = new FrQuestion();
        q.setFrQuestionId(id);
        q.setPoints(points);
        q.setFrQuestionType("综合分析");
        q.setAnswer("(1)aaa(2)bbb");
        return q;
    }

    private static ShenlunRubric rubric(int order, String text, double score) {
        ShenlunRubric r = new ShenlunRubric();
        r.setRubricId((long) order);
        r.setPointOrder(order);
        r.setPointText(text);
        r.setPointScore(score);
        return r;
    }
}
