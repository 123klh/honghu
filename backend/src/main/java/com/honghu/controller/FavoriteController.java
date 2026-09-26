package com.honghu.controller;

import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.pojo.UserFavoriteFrQuestion;
import com.honghu.pojo.UserFavoritePaper;
import com.honghu.pojo.UserFavoriteQuestion;
import com.honghu.service.FavoriteService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收藏夹接口，全部面向登录用户。UserContext.requireUserId() 已拒绝 manager 令牌，
 * 保证收藏归属到正确的用户 ID 空间。
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/questions/{questionId}")
    public Result<Void> addQuestion(@PathVariable Long questionId, @RequestBody(required = false) FavoriteRequest req) {
        favoriteService.addQuestion(UserContext.requireUserId(), questionId,
                folder(req), notes(req));
        return Result.success();
    }

    @DeleteMapping("/questions/{questionId}")
    public Result<Void> removeQuestion(@PathVariable Long questionId) {
        favoriteService.removeQuestion(UserContext.requireUserId(), questionId);
        return Result.success();
    }

    @GetMapping("/questions")
    public Result<List<UserFavoriteQuestion>> listQuestions() {
        return Result.success(favoriteService.listQuestions(UserContext.requireUserId()));
    }

    @PostMapping("/papers/{paperId}")
    public Result<Void> addPaper(@PathVariable Long paperId, @RequestBody(required = false) FavoriteRequest req) {
        favoriteService.addPaper(UserContext.requireUserId(), paperId, folder(req), notes(req));
        return Result.success();
    }

    @DeleteMapping("/papers/{paperId}")
    public Result<Void> removePaper(@PathVariable Long paperId) {
        favoriteService.removePaper(UserContext.requireUserId(), paperId);
        return Result.success();
    }

    @GetMapping("/papers")
    public Result<List<UserFavoritePaper>> listPapers() {
        return Result.success(favoriteService.listPapers(UserContext.requireUserId()));
    }

    @PostMapping("/frquestions/{frQuestionId}")
    public Result<Void> addFrQuestion(@PathVariable Long frQuestionId, @RequestBody(required = false) FavoriteRequest req) {
        favoriteService.addFrQuestion(UserContext.requireUserId(), frQuestionId, folder(req), notes(req));
        return Result.success();
    }

    @DeleteMapping("/frquestions/{frQuestionId}")
    public Result<Void> removeFrQuestion(@PathVariable Long frQuestionId) {
        favoriteService.removeFrQuestion(UserContext.requireUserId(), frQuestionId);
        return Result.success();
    }

    @GetMapping("/frquestions")
    public Result<List<UserFavoriteFrQuestion>> listFrQuestions() {
        return Result.success(favoriteService.listFrQuestions(UserContext.requireUserId()));
    }

    private static String folder(FavoriteRequest req) {
        return req == null ? null : req.getFolder();
    }

    private static String notes(FavoriteRequest req) {
        return req == null ? null : req.getNotes();
    }

    @Data
    public static class FavoriteRequest {
        private String folder;
        private String notes;
    }
}
