package com.honghu.mapper;

import com.honghu.pojo.KnowledgePoint;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgePointMapper {

    @Select("SELECT * FROM knowledge_point WHERE kp_id = #{kpId}")
    KnowledgePoint findById(Long kpId);

    @Select("SELECT * FROM knowledge_point WHERE kp_name = #{kpName} LIMIT 1")
    KnowledgePoint findByName(String kpName);

    /** 种子数据用 parent_id=0 表示根节点 */
    @Select("SELECT * FROM knowledge_point WHERE parent_id IS NULL OR parent_id = 0 "
            + "ORDER BY sort_order, kp_id")
    List<KnowledgePoint> findRoots();

    @Select("SELECT * FROM knowledge_point WHERE parent_id = #{parentId} ORDER BY sort_order, kp_id")
    List<KnowledgePoint> findByParent(Long parentId);

    @Select("SELECT * FROM knowledge_point WHERE subject = #{subject} ORDER BY sort_order, kp_id")
    List<KnowledgePoint> findBySubject(String subject);

    @Select("SELECT * FROM knowledge_point ORDER BY parent_id, sort_order, kp_id")
    List<KnowledgePoint> findAll();

    @Insert("INSERT INTO knowledge_point (kp_name, parent_id, subject, sort_order) "
            + "VALUES (#{kpName}, #{parentId}, #{subject}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "kpId")
    int insert(KnowledgePoint kp);

    /** 题目挂载的知识点 */
    @Select("SELECT kp.* FROM knowledge_point kp "
            + "JOIN question_knowledge qk ON qk.kp_id = kp.kp_id "
            + "WHERE qk.question_id = #{questionId}")
    List<KnowledgePoint> findByQuestion(Long questionId);

    @Select("SELECT kp_id FROM question_knowledge WHERE question_id = #{questionId}")
    List<Long> findKpIdsByQuestion(Long questionId);

    @Insert("INSERT IGNORE INTO question_knowledge (question_id, kp_id) VALUES (#{questionId}, #{kpId})")
    int linkQuestion(@Param("questionId") Long questionId, @Param("kpId") Long kpId);

    /** 指定知识点下的题目 ID 池，供个性化组卷抽样 */
    @Select("SELECT qk.question_id FROM question_knowledge qk "
            + "JOIN question q ON q.question_id = qk.question_id "
            + "WHERE qk.kp_id = #{kpId} AND q.status = 1")
    List<Long> findQuestionIdsByKp(Long kpId);
}
