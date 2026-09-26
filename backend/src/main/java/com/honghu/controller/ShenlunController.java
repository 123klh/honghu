package com.honghu.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.honghu.common.PageResult;
import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.mapper.FrQuestionMapper;
import com.honghu.pojo.FrQuestion;
import com.honghu.service.ShenlunScoringService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 申论练习与评分。列表/详情面向登录用户；评分本地计算并返回按点明细。
 */
@RestController
@RequestMapping("/api/shenlun")
@RequiredArgsConstructor
public class ShenlunController {

    private final FrQuestionMapper frQuestionMapper;
    private final ShenlunScoringService scoringService;

    @GetMapping("/questions")
    public Result<PageResult<FrQuestion>> list(@RequestParam(required = false) String subject,
                                               @RequestParam(required = false) String frQuestionType,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 50) pageSize = 10;
        PageHelper.startPage(pageNum, pageSize);
        List<FrQuestion> rows = frQuestionMapper.search(subject, frQuestionType, keyword);
        return Result.success(PageResult.of(new PageInfo<>(rows)));
    }

    @GetMapping("/questions/{id}")
    public Result<FrQuestion> detail(@PathVariable Long id) {
        return Result.success(frQuestionMapper.findActiveById(id));
    }

    @PostMapping("/questions/{id}/score")
    public Result<ShenlunScoringService.ScoreReport> score(@PathVariable Long id,
                                                           @RequestBody ScoreRequest req) {
        // 评分需登录（也确保是 user 令牌）
        UserContext.requireUserId();
        return Result.success(scoringService.score(id, req.getAnswer()));
    }

    @Data
    public static class ScoreRequest {
        private String answer;
    }
}
