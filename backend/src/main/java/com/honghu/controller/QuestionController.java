package com.honghu.controller;

import com.honghu.annotation.RequireManager;
import com.honghu.common.PageResult;
import com.honghu.common.Result;
import com.honghu.pojo.Question;
import com.honghu.service.PracticeService;
import com.honghu.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;

    @GetMapping("/api/questions/{questionId}")
    public Result<PracticeService.QuestionView> get(@PathVariable Long questionId) {
        return Result.success(questionService.getForUser(questionId));
    }

    @RequireManager
    @GetMapping("/api/admin/questions")
    public Result<PageResult<Question>> search(@RequestParam(required = false) String subject,
                                               @RequestParam(required = false) String questionType,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(questionService.search(subject, questionType, keyword, pageNum, pageSize));
    }

    @RequireManager
    @PostMapping("/api/admin/questions")
    public Result<Question> create(@RequestBody Question question) {
        return Result.success(questionService.create(question));
    }

    @RequireManager
    @PutMapping("/api/admin/questions/{questionId}")
    public Result<Question> update(@PathVariable Long questionId, @RequestBody Question question) {
        return Result.success(questionService.update(questionId, question));
    }

    @RequireManager
    @DeleteMapping("/api/admin/questions/{questionId}")
    public Result<Void> delete(@PathVariable Long questionId) {
        questionService.delete(questionId);
        return Result.success();
    }
}
