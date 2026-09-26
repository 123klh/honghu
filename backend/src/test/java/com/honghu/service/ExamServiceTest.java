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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamServiceTest {

    private final PaperMapper paperMapper = mock(PaperMapper.class);
    private final QuestionMapper questionMapper = mock(QuestionMapper.class);
    private final ExamScoreMapper examScoreMapper = mock(ExamScoreMapper.class);
    private final ExamQuestionMapper examQuestionMapper = mock(ExamQuestionMapper.class);
    private final PracticeService practiceService = mock(PracticeService.class);
    private final ExamService examService = new ExamService(
            paperMapper, questionMapper, examScoreMapper, examQuestionMapper, practiceService);

    @Test
    void userCanStartAndSubmitACompletePaper() {
        Paper paper = new Paper();
        paper.setPaperId(3L);
        paper.setPaperName("2025 national exam");
        paper.setStatus(1);
        when(paperMapper.findActiveById(3L)).thenReturn(paper);

        Question first = question(11L, "A");
        Question second = question(12L, "B");
        when(questionMapper.findIdsByPaper(3L)).thenReturn(List.of(11L, 12L));
        when(questionMapper.findByIds(List.of(11L, 12L))).thenReturn(List.of(first, second));
        doAnswer(invocation -> {
            ExamScore score = invocation.getArgument(0);
            score.setExamId(99L);
            return 1;
        }).when(examScoreMapper).insert(any(ExamScore.class));

        ExamService.ExamSession session = examService.start(8L, 3L);

        assertThat(session.getExamId()).isEqualTo(99L);
        assertThat(session.getQuestions()).hasSize(2);

        // 开考不得写入 0 分，否则 avg_score 触发器会把它算成一次成绩
        verify(examScoreMapper).insert(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.getScore() == null));

        ExamScore ongoing = ongoing();
        when(examScoreMapper.findByIdForUpdate(99L)).thenReturn(ongoing);
        when(examQuestionMapper.findByExam(99L)).thenReturn(List.of(
                snapshot(11L, 1, "A"), snapshot(12L, 2, "B")));
        when(examScoreMapper.submitIfOngoing(ongoing)).thenReturn(1);
        when(practiceService.submitExamAnswer(8L, 99L, 11L, "A", 20, "A"))
                .thenReturn(answerResult(11L, true));
        when(practiceService.submitExamAnswer(8L, 99L, 12L, "C", 30, "B"))
                .thenReturn(answerResult(12L, false));

        ExamService.ExamResult result = examService.submit(8L, 99L, List.of(
                new ExamService.AnswerSubmission(11L, "A", 20),
                new ExamService.AnswerSubmission(12L, "C", 30)), 50);

        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getAnsweredCount()).isEqualTo(2);
        assertThat(result.getScore()).isEqualTo(50.0);
        assertThat(ongoing.getTotalScore()).isEqualTo(100.0);
        verify(examScoreMapper).submitIfOngoing(ongoing);
    }

    /** 分母是整卷题量：只答对一题不该拿满分。 */
    @Test
    void scoreDenominatorIsThePaperSizeNotTheSubmittedCount() {
        ExamScore ongoing = ongoing();
        when(examScoreMapper.findByIdForUpdate(99L)).thenReturn(ongoing);
        when(examQuestionMapper.findByExam(99L)).thenReturn(List.of(
                snapshot(11L, 1, "A"), snapshot(12L, 2, "B"),
                snapshot(13L, 3, "C"), snapshot(14L, 4, "D")));
        when(examScoreMapper.submitIfOngoing(ongoing)).thenReturn(1);
        when(practiceService.submitExamAnswer(8L, 99L, 11L, "A", 20, "A"))
                .thenReturn(answerResult(11L, true));

        ExamService.ExamResult result = examService.submit(8L, 99L, List.of(
                new ExamService.AnswerSubmission(11L, "A", 20)), 10);

        assertThat(result.getScore()).isEqualTo(25.0);
        assertThat(result.getAnsweredCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(4);
    }

    /** 不在快照里的题目一律拒收，否则可以提交别的试卷的题来刷分。 */
    @Test
    void submittingAQuestionOutsideTheSnapshotIsRejected() {
        ExamScore ongoing = ongoing();
        when(examScoreMapper.findByIdForUpdate(99L)).thenReturn(ongoing);
        when(examQuestionMapper.findByExam(99L)).thenReturn(List.of(snapshot(11L, 1, "A")));

        assertThatThrownBy(() -> examService.submit(8L, 99L, List.of(
                new ExamService.AnswerSubmission(777L, "A", 20)), 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("777");
    }

    /** 并发/重复提交：条件更新返回 0 时必须失败，不能覆盖已有成绩。 */
    @Test
    void duplicateSubmitIsRejectedWhenTheConditionalUpdateMatchesNothing() {
        ExamScore ongoing = ongoing();
        when(examScoreMapper.findByIdForUpdate(99L)).thenReturn(ongoing);
        when(examQuestionMapper.findByExam(99L)).thenReturn(List.of(snapshot(11L, 1, "A")));
        when(examScoreMapper.submitIfOngoing(ongoing)).thenReturn(0);
        when(practiceService.submitExamAnswer(8L, 99L, 11L, "A", 20, "A"))
                .thenReturn(answerResult(11L, true));

        assertThatThrownBy(() -> examService.submit(8L, 99L, List.of(
                new ExamService.AnswerSubmission(11L, "A", 20)), 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已提交");
    }

    private static ExamScore ongoing() {
        ExamScore score = new ExamScore();
        score.setExamId(99L);
        score.setPaperId(3L);
        score.setUserId(8L);
        score.setStatus("ONGOING");
        return score;
    }

    private static ExamQuestion snapshot(long questionId, int order, String answer) {
        ExamQuestion row = new ExamQuestion();
        row.setExamId(99L);
        row.setQuestionId(questionId);
        row.setQuestionOrder(order);
        row.setAnswerSnapshot(answer);
        return row;
    }

    private static Question question(long id, String answer) {
        Question question = new Question();
        question.setQuestionId(id);
        question.setQuestionStem("Question " + id);
        question.setAnswer(answer);
        return question;
    }

    private static PracticeService.AnswerResult answerResult(long id, boolean correct) {
        PracticeService.AnswerResult result = new PracticeService.AnswerResult();
        result.setQuestionId(id);
        result.setCorrect(correct);
        return result;
    }
}
