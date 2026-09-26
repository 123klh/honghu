package com.honghu.controller;

import com.honghu.annotation.RequireManager;
import com.honghu.common.PageResult;
import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.pojo.Discussion;
import com.honghu.pojo.DiscussionReply;
import com.honghu.service.CommunityService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 社区问答接口。浏览（GET）对所有登录用户开放；发帖、回复、采纳需登录用户身份；
 * 置顶与强制删帖走 @RequireManager 的管理端接口。
 */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping("/discussions")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(communityService.listDiscussions(keyword, pageNum, pageSize));
    }

    @GetMapping("/discussions/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(communityService.getDiscussion(id));
    }

    @PostMapping("/discussions")
    public Result<Discussion> create(@RequestBody DiscussionRequest req) {
        return Result.success(communityService.createDiscussion(
                UserContext.requireUserId(), req.getTitle(), req.getContent(),
                req.getQuestionId(), req.getQuestionType()));
    }

    @DeleteMapping("/discussions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        communityService.deleteDiscussion(UserContext.requireUserId(), id);
        return Result.success();
    }

    @PostMapping("/discussions/{id}/replies")
    public Result<DiscussionReply> reply(@PathVariable Long id, @RequestBody ReplyRequest req) {
        return Result.success(communityService.reply(UserContext.requireUserId(), id, req.getContent()));
    }

    @PostMapping("/replies/{replyId}/like")
    public Result<Void> like(@PathVariable Long replyId) {
        // 点赞需要登录，但不绑定具体用户（表结构无点赞明细）；requireUserId 兜住鉴权
        UserContext.requireUserId();
        communityService.like(replyId);
        return Result.success();
    }

    @PostMapping("/replies/{replyId}/accept")
    public Result<Void> accept(@PathVariable Long replyId) {
        communityService.acceptReply(UserContext.requireUserId(), replyId);
        return Result.success();
    }

    @DeleteMapping("/replies/{replyId}")
    public Result<Void> deleteReply(@PathVariable Long replyId) {
        communityService.deleteReply(UserContext.requireUserId(), replyId);
        return Result.success();
    }

    // ---------- 管理端 ----------

    @RequireManager
    @PutMapping("/admin/discussions/{id}/pin")
    public Result<Void> pin(@PathVariable Long id, @RequestParam boolean pinned) {
        communityService.setPinned(id, pinned);
        return Result.success();
    }

    @RequireManager
    @DeleteMapping("/admin/discussions/{id}")
    public Result<Void> adminDelete(@PathVariable Long id) {
        communityService.adminDeleteDiscussion(id);
        return Result.success();
    }

    @Data
    public static class DiscussionRequest {
        private String title;
        private String content;
        private Long questionId;
        private String questionType;
    }

    @Data
    public static class ReplyRequest {
        private String content;
    }
}
