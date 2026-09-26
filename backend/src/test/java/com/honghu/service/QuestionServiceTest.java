package com.honghu.service;

import com.honghu.common.BusinessException;
import com.honghu.mapper.QuestionMapper;
import com.honghu.pojo.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceTest {

    private final QuestionMapper questionMapper = mock(QuestionMapper.class);
    private final QuestionService questionService = new QuestionService(questionMapper);

    @Test
    void userCanReadQuestionWithoutAnswerPayload() {
        Question question = new Question();
        question.setQuestionId(7L);
        question.setQuestionCode("gk-7");
        question.setQuestionStem("Which option is correct?");
        question.setAnswer("B");
        question.setExplanation("Because B is correct.");
        question.setStatus(1);
        when(questionMapper.findActiveById(7L)).thenReturn(question);

        PracticeService.QuestionView view = questionService.getForUser(7L);

        assertThat(view.getQuestionId()).isEqualTo(7L);
        assertThat(view.getQuestionStem()).isEqualTo("Which option is correct?");
        assertThat(view.getClass().getDeclaredFields())
                .extracting("name")
                .doesNotContain("answer", "explanation");
    }

    /** 更新接口必须和新增一样校验答案，不能成为绕过校验的后门。 */
    @Test
    void updateRejectsAnIllegalAnswerJustLikeCreate() {
        when(questionMapper.findById(7L)).thenReturn(existing(7L));
        Question edit = new Question();
        edit.setQuestionStem("stem");
        edit.setAnswer("Z"); // 非 A-D

        assertThatThrownBy(() -> questionService.update(7L, edit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A-D");
        verify(questionMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    /** 更新接口不能把题干清空。 */
    @Test
    void updateRejectsABlankStem() {
        when(questionMapper.findById(7L)).thenReturn(existing(7L));
        Question edit = new Question();
        edit.setQuestionStem("   ");
        edit.setAnswer("A");

        assertThatThrownBy(() -> questionService.update(7L, edit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题干");
        verify(questionMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    private static Question existing(long id) {
        Question question = new Question();
        question.setQuestionId(id);
        question.setQuestionCode("gk-" + id);
        question.setQuestionStem("original");
        question.setAnswer("A");
        question.setStatus(1);
        return question;
    }
}
