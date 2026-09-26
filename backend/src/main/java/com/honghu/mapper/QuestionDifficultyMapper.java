package com.honghu.mapper;

import com.honghu.pojo.QuestionDifficulty;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface QuestionDifficultyMapper {

    @Select("SELECT * FROM question_difficulty WHERE question_id = #{questionId}")
    QuestionDifficulty findByQuestionId(Long questionId);

    @Insert("INSERT INTO question_difficulty (question_id, difficulty_value, source_accuracy_rate, common_mistake) "
            + "VALUES (#{questionId}, #{difficultyValue}, #{sourceAccuracyRate}, #{commonMistake}) "
            + "ON DUPLICATE KEY UPDATE difficulty_value = VALUES(difficulty_value)")
    int insert(QuestionDifficulty difficulty);

    @Update("UPDATE question_difficulty SET difficulty_value = #{difficultyValue} WHERE question_id = #{questionId}")
    int updateDifficulty(@Param("questionId") Long questionId,
                         @Param("difficultyValue") Double difficultyValue);

    @Select("""
            <script>
            SELECT * FROM question_difficulty WHERE question_id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<QuestionDifficulty> findByQuestionIds(@Param("ids") List<Long> ids);
}
