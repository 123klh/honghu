package com.honghu.controller;

import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.pojo.Question;
import com.honghu.service.PracticeService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
@Validated
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping("/module/start")
    public Result<PracticeService.PracticeSession> startModulePractice(
            @RequestBody @Validated StartPracticeRequest request) {
        return Result.success(
                practiceService.startModulePractice(request.getQuestionType(), request.getCount()));
    }

    /** 个性化组卷，mode ∈ {WEAK, FOCUS, SECURE} */
    @PostMapping("/personalized/start")
    public Result<PracticeService.PracticeSession> startPersonalized(
            @RequestBody @Validated PersonalizedRequest request) {
        return Result.success(practiceService.startPersonalizedPractice(
                UserContext.requireUserId(), request.getMode(), request.getCount()));
    }

    @PostMapping("/submit")
    public Result<PracticeService.AnswerResult> submitAnswer(
            @RequestBody @Validated SubmitAnswerRequest request) {
        return Result.success(practiceService.submitAnswer(
                UserContext.requireUserId(),
                request.getQuestionId(),
                request.getUserAnswer(),
                request.getDuration()));
    }

    @GetMapping("/wrong")
    public Result<List<Question>> listWrongQuestions() {
        return Result.success(practiceService.listWrongQuestions(UserContext.requireUserId()));
    }

    @PostMapping("/wrong/start")
    public Result<PracticeService.PracticeSession> startWrongBookPractice(
            @RequestBody @Validated WrongBookRequest request) {
        return Result.success(practiceService.startWrongBookPractice(
                UserContext.requireUserId(), request.getCount()));
    }

    @PostMapping("/wrong/{questionId}/mastered")
    public Result<Void> markMastered(@PathVariable Long questionId) {
        practiceService.markMastered(UserContext.requireUserId(), questionId);
        return Result.success();
    }

    @GetMapping("/wrong/stats")
    public Result<PracticeService.WrongBookStats> wrongBookStats() {
        return Result.success(practiceService.wrongBookStats(UserContext.requireUserId()));
    }

    @Data
    public static class StartPracticeRequest {
        @NotBlank(message = "题型不能为空")
        private String questionType;

        @NotNull(message = "题目数量不能为空")
        @Min(value = 1, message = "题目数量至少为 1")
        private Integer count;
    }

    @Data
    public static class PersonalizedRequest {
        @NotBlank(message = "组卷档位不能为空")
        private String mode;

        @NotNull(message = "题目数量不能为空")
        @Min(value = 1, message = "题目数量至少为 1")
        private Integer count;
    }

    @Data
    public static class WrongBookRequest {
        @NotNull(message = "题目数量不能为空")
        @Min(value = 1, message = "题目数量至少为 1")
        private Integer count;
    }

    @Data
    public static class SubmitAnswerRequest {
        @NotNull(message = "题目ID不能为空")
        private Long questionId;

        @NotBlank(message = "作答内容不能为空")
        private String userAnswer;

        /** 本题耗时，秒，可选 */
        private Integer duration;
    }
}
