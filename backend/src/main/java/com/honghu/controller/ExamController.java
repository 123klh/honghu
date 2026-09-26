package com.honghu.controller;

import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.pojo.ExamScore;
import com.honghu.pojo.Paper;
import com.honghu.mapper.PaperMapper;
import com.honghu.service.ExamService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {
    private final ExamService examService;
    private final PaperMapper paperMapper;

    @GetMapping("/papers")
    public Result<List<Paper>> papers(@RequestParam(required = false) String subject) {
        return Result.success(subject == null || subject.isBlank()
                ? paperMapper.findAllActive() : paperMapper.findBySubject(subject));
    }

    @PostMapping("/papers/{paperId}/start")
    public Result<ExamService.ExamSession> start(@PathVariable Long paperId) {
        return Result.success(examService.start(UserContext.requireUserId(), paperId));
    }

    @PostMapping("/{examId}/submit")
    public Result<ExamService.ExamResult> submit(@PathVariable Long examId,
                                                 @RequestBody SubmitExamRequest request) {
        return Result.success(examService.submit(UserContext.requireUserId(), examId,
                request.getAnswers(), request.getDuration()));
    }

    @GetMapping("/history")
    public Result<List<ExamScore>> history() {
        return Result.success(examService.history(UserContext.requireUserId()));
    }

    @Data
    public static class SubmitExamRequest {
        private List<ExamService.AnswerSubmission> answers;
        private Integer duration;
    }
}
