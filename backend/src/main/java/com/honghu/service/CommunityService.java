package com.honghu.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.honghu.common.BusinessException;
import com.honghu.common.PageResult;
import com.honghu.mapper.DiscussionMapper;
import com.honghu.mapper.DiscussionReplyMapper;
import com.honghu.pojo.Discussion;
import com.honghu.pojo.DiscussionReply;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 社区问答。帖子与回复的作者身份由 UserContext 提供，删除/采纳做归属校验，
 * 只有楼主能采纳自己帖子的回复，只有作者本人能删自己的帖子/回复。
 * 置顶属于管理操作，由管理端接口另行开放。
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final DiscussionMapper discussionMapper;
    private final DiscussionReplyMapper replyMapper;

    public PageResult<Map<String, Object>> listDiscussions(String keyword, int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        PageHelper.startPage(pageNum, pageSize);
        List<Map<String, Object>> rows = discussionMapper.listView(keyword);
        return PageResult.of(new PageInfo<>(rows));
    }

    @Transactional
    public Discussion createDiscussion(Long userId, String title, String content,
                                       Long questionId, String questionType) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }
        Discussion d = new Discussion();
        d.setUserId(userId);
        d.setTitle(title.trim());
        d.setContent(content.trim());
        d.setQuestionId(questionId);
        d.setQuestionType(questionType == null ? "NONE" : questionType);
        d.setIsPinned(false);
        discussionMapper.insert(d);
        return d;
    }

    public Map<String, Object> getDiscussion(Long id) {
        Discussion d = discussionMapper.findViewById(id);
        if (d == null) {
            throw new BusinessException(404, "帖子不存在或已删除");
        }
        List<Map<String, Object>> replies = replyMapper.listView(id);
        return Map.of("discussion", d, "replies", replies);
    }

    @Transactional
    public void deleteDiscussion(Long userId, Long id) {
        Discussion d = discussionMapper.findById(id);
        if (d == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        if (!userId.equals(d.getUserId())) {
            throw new BusinessException(403, "只能删除自己的帖子");
        }
        discussionMapper.softDelete(id);
    }

    @Transactional
    public DiscussionReply reply(Long userId, Long discussionId, String content) {
        Discussion d = discussionMapper.findById(discussionId);
        if (d == null) {
            throw new BusinessException(404, "帖子不存在或已删除");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }
        DiscussionReply r = new DiscussionReply();
        r.setDiscussionId(discussionId);
        r.setUserId(userId);
        r.setContent(content.trim());
        replyMapper.insert(r);
        // 让被回复的帖子在列表里冒泡到前面
        discussionMapper.touch(discussionId);
        return r;
    }

    @Transactional
    public void like(Long replyId) {
        if (replyMapper.like(replyId) == 0) {
            throw new BusinessException(404, "回复不存在或已删除");
        }
    }

    /** 采纳回复：只有楼主可操作，且一个帖子最多一条被采纳。 */
    @Transactional
    public void acceptReply(Long userId, Long replyId) {
        DiscussionReply reply = replyMapper.findById(replyId);
        if (reply == null) {
            throw new BusinessException(404, "回复不存在或已删除");
        }
        Discussion d = discussionMapper.findById(reply.getDiscussionId());
        if (d == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        if (!userId.equals(d.getUserId())) {
            throw new BusinessException(403, "只有楼主能采纳回复");
        }
        replyMapper.clearAccepted(d.getDiscussionId());
        replyMapper.accept(replyId);
    }

    @Transactional
    public void deleteReply(Long userId, Long replyId) {
        DiscussionReply reply = replyMapper.findById(replyId);
        if (reply == null) {
            throw new BusinessException(404, "回复不存在");
        }
        if (!userId.equals(reply.getUserId())) {
            throw new BusinessException(403, "只能删除自己的回复");
        }
        replyMapper.softDelete(replyId);
    }

    // ---------- 管理端 ----------

    @Transactional
    public void setPinned(Long id, boolean pinned) {
        if (discussionMapper.findById(id) == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        discussionMapper.setPinned(id, pinned);
    }

    @Transactional
    public void adminDeleteDiscussion(Long id) {
        discussionMapper.softDelete(id);
    }
}
