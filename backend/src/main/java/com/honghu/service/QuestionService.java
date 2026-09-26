package com.honghu.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.honghu.common.BusinessException;
import com.honghu.common.PageResult;
import com.honghu.mapper.QuestionMapper;
import com.honghu.pojo.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionMapper questionMapper;

    public PracticeService.QuestionView getForUser(Long questionId) {
        Question question = questionMapper.findActiveById(questionId);
        if (question == null) {
            throw new BusinessException(404, "题目不存在");
        }
        return PracticeService.QuestionView.of(question);
    }

    /**
     * 管理端题库分页查询。PageHelper 拦截紧随其后的第一条 MyBatis 查询改写为
     * COUNT + LIMIT，因此 startPage 必须紧贴 mapper 调用，中间不得插入其它查询。
     */
    public PageResult<Question> search(String subject, String questionType, String keyword,
                                       int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 200) pageSize = 20;
        PageHelper.startPage(pageNum, pageSize);
        List<Question> rows = questionMapper.search(subject, questionType, keyword);
        return PageResult.of(new PageInfo<>(rows));
    }

    @Transactional
    public Question create(Question question) {
        if (question.getQuestionCode() == null || question.getQuestionCode().isBlank()) {
            throw new BusinessException("题目编码不能为空");
        }
        validateContent(question);
        question.setStatus(1);
        question.setEnclosure(Boolean.TRUE.equals(question.getEnclosure()));
        questionMapper.insert(question);
        return question;
    }

    @Transactional
    public Question update(Long questionId, Question question) {
        if (questionMapper.findById(questionId) == null) {
            throw new BusinessException(404, "题目不存在");
        }
        // 与 create 相同的内容校验：改题同样不能把题干清空或写入非法答案，
        // 否则更新接口成了绕过校验往题库塞脏数据的后门。
        validateContent(question);
        question.setQuestionId(questionId);
        if (question.getStatus() == null) question.setStatus(1);
        if (question.getEnclosure() == null) question.setEnclosure(false);
        questionMapper.update(question);
        return questionMapper.findById(questionId);
    }

    /** 题干与答案的公共校验，create/update 共用。questionCode 不在 update 的更新范围，故不在此校验。 */
    private void validateContent(Question question) {
        if (question.getQuestionStem() == null || question.getQuestionStem().isBlank()) {
            throw new BusinessException("题干不能为空");
        }
        if (question.getAnswer() == null || !question.getAnswer().matches("[A-D]{1,4}")) {
            throw new BusinessException("答案必须由 A-D 组成");
        }
    }

    @Transactional
    public void delete(Long questionId) {
        if (questionMapper.softDelete(questionId) == 0) {
            throw new BusinessException(404, "题目不存在");
        }
    }
}
