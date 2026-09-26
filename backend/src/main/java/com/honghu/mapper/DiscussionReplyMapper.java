package com.honghu.mapper;

import com.honghu.pojo.DiscussionReply;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 帖子回复。楼层按时间正序，被采纳的回复置顶。点赞是幂等自增（前端去重不可靠，
 * 这里只保证不越界为负），采纳由楼主触发且一个帖子只能采纳一条。
 */
@Mapper
public interface DiscussionReplyMapper {

    @Insert("INSERT INTO discussion_reply (discussion_id, user_id, content, is_accepted, likes_count, status) "
            + "VALUES (#{discussionId}, #{userId}, #{content}, 0, 0, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "replyId")
    int insert(DiscussionReply reply);

    @Select("SELECT * FROM discussion_reply WHERE reply_id = #{id} AND status = 1")
    DiscussionReply findById(Long id);

    @Select("""
            SELECT r.reply_id AS replyId, r.discussion_id AS discussionId, r.user_id AS userId,
                   u.user_name AS authorName, r.content, r.is_accepted AS isAccepted,
                   r.likes_count AS likesCount, r.created_at AS createdAt
            FROM discussion_reply r LEFT JOIN user u ON u.user_id = r.user_id
            WHERE r.discussion_id = #{discussionId} AND r.status = 1
            ORDER BY r.is_accepted DESC, r.created_at ASC
            """)
    List<Map<String, Object>> listView(@Param("discussionId") Long discussionId);

    @Update("UPDATE discussion_reply SET likes_count = likes_count + 1 WHERE reply_id = #{id} AND status = 1")
    int like(Long id);

    /** 先清掉本帖已采纳标记，保证一个帖子最多一条被采纳。 */
    @Update("UPDATE discussion_reply SET is_accepted = 0 WHERE discussion_id = #{discussionId}")
    int clearAccepted(Long discussionId);

    @Update("UPDATE discussion_reply SET is_accepted = 1 WHERE reply_id = #{id}")
    int accept(Long id);

    @Update("UPDATE discussion_reply SET status = 0 WHERE reply_id = #{id}")
    int softDelete(Long id);
}
