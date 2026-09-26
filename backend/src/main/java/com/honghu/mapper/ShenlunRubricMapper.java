package com.honghu.mapper;

import com.honghu.pojo.ShenlunRubric;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShenlunRubricMapper {

    @Select("SELECT rubric_id, fr_question_id, point_order, point_text, point_score, created_at "
            + "FROM shenlun_rubric WHERE fr_question_id = #{frQuestionId} ORDER BY point_order")
    List<ShenlunRubric> findByFrQuestion(Long frQuestionId);

    @Select("SELECT synonym_text FROM shenlun_rubric_synonym WHERE rubric_id = #{rubricId}")
    List<String> findSynonyms(Long rubricId);
}
