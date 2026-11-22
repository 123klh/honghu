package com.honghu.mapper;

import com.honghu.pojo.Discussion;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 社区问答帖。列表带上作者名与回复数（LEFT JOIN + 子查询），
 * 只统计 status=1 的回复。分页由 PageHelper 拦截 listView 完成。
 */
@Mapper
public interface DiscussionMapper {

    @Insert("INSERT INTO discussion (question_id, question_type, user_id, title, content, is_pinned, status) "
            + "VALUES (#{questionId}, #{questionType}, #{userId}, #{title}, #{content}, "
            + "COALESCE(#{isPinned}, 0), 1)")
    @Options(useGeneratedKeys = true, keyProperty = "discussionId")
    int insert(Discussion discussion);

    @Select("SELECT * FROM discussion WHERE discussion_id = #{id} AND status = 1")
    Discussion findById(Long id);

    @Select("""
            SELECT d.*, u.user_name AS authorName,
                   (SELECT COUNT(*) FROM discussion_reply r
                    WHERE r.discussion_id = d.discussion_id AND r.status = 1) AS replyCount
            FROM discussion d LEFT JOIN user u ON u.user_id = d.user_id
            WHERE d.discussion_id = #{id} AND d.status = 1
            """)
    Discussion findViewById(Long id);

    /**
     * 帖子列表视图：置顶优先、然后按更新时间倒序。keyword 命中标题或正文。
     * 返回 Map 以便一次带出作者名和回复数，避免前端 N+1。
     */
    @Select("""
            <script>
            SELECT d.discussion_id AS discussionId, d.title, d.content,
                   d.question_id AS questionId, d.question_type AS questionType,
                   d.user_id AS userId, u.user_name AS authorName,
                   d.is_pinned AS isPinned, d.created_at AS createdAt, d.updated_at AS updatedAt,
                   (SELECT COUNT(*) FROM discussion_reply r
                    WHERE r.discussion_id = d.discussion_id AND r.status = 1) AS replyCount
            FROM discussion d LEFT JOIN user u ON u.user_id = d.user_id
            WHERE d.status = 1
            <if test="keyword != null and keyword != ''">
                AND (d.title LIKE CONCAT('%', #{keyword}, '%') OR d.content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY d.is_pinned DESC, d.updated_at DESC
            </script>
            """)
    List<Map<String, Object>> listView(@Param("keyword") String keyword);

    @Update("UPDATE discussion SET status = 0 WHERE discussion_id = #{id}")
    int softDelete(Long id);

    @Update("UPDATE discussion SET is_pinned = #{pinned} WHERE discussion_id = #{id}")
    int setPinned(@Param("id") Long id, @Param("pinned") boolean pinned);

    /** 帖子被回复时更新时间戳，使其在列表里冒泡到前面。 */
    @Update("UPDATE discussion SET updated_at = CURRENT_TIMESTAMP WHERE discussion_id = #{id}")
    int touch(Long id);
}
