package com.honghu.mapper;

import com.honghu.pojo.UserFavoriteFrQuestion;
import com.honghu.pojo.UserFavoritePaper;
import com.honghu.pojo.UserFavoriteQuestion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 三类收藏（客观题 / 申论题 / 试卷）。每类都以 (user_id, 目标id) 唯一，
 * 重复收藏走 ON DUPLICATE KEY 更新备注与分组，不产生重复行。
 */
@Mapper
public interface FavoriteMapper {

    // ---------- 客观题 ----------

    @Insert("INSERT INTO user_favorite_question (user_id, question_id, folder, notes) "
            + "VALUES (#{userId}, #{questionId}, #{folder}, #{notes}) "
            + "ON DUPLICATE KEY UPDATE folder = VALUES(folder), notes = VALUES(notes), "
            + "updated_at = CURRENT_TIMESTAMP")
    int upsertQuestion(UserFavoriteQuestion favorite);

    @Delete("DELETE FROM user_favorite_question WHERE user_id = #{userId} AND question_id = #{questionId}")
    int deleteQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select("SELECT * FROM user_favorite_question WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<UserFavoriteQuestion> listQuestions(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM user_favorite_question WHERE user_id = #{userId} AND question_id = #{questionId}")
    int existsQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    // ---------- 申论题 ----------

    @Insert("INSERT INTO user_favorite_frquestion (user_id, fr_question_id, folder, notes) "
            + "VALUES (#{userId}, #{frQuestionId}, #{folder}, #{notes}) "
            + "ON DUPLICATE KEY UPDATE folder = VALUES(folder), notes = VALUES(notes), "
            + "updated_at = CURRENT_TIMESTAMP")
    int upsertFrQuestion(UserFavoriteFrQuestion favorite);

    @Delete("DELETE FROM user_favorite_frquestion WHERE user_id = #{userId} AND fr_question_id = #{frQuestionId}")
    int deleteFrQuestion(@Param("userId") Long userId, @Param("frQuestionId") Long frQuestionId);

    @Select("SELECT * FROM user_favorite_frquestion WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<UserFavoriteFrQuestion> listFrQuestions(@Param("userId") Long userId);

    // ---------- 试卷 ----------

    @Insert("INSERT INTO user_favorite_paper (user_id, paper_id, folder, notes) "
            + "VALUES (#{userId}, #{paperId}, #{folder}, #{notes}) "
            + "ON DUPLICATE KEY UPDATE folder = VALUES(folder), notes = VALUES(notes), "
            + "updated_at = CURRENT_TIMESTAMP")
    int upsertPaper(UserFavoritePaper favorite);

    @Delete("DELETE FROM user_favorite_paper WHERE user_id = #{userId} AND paper_id = #{paperId}")
    int deletePaper(@Param("userId") Long userId, @Param("paperId") Long paperId);

    @Select("SELECT * FROM user_favorite_paper WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<UserFavoritePaper> listPapers(@Param("userId") Long userId);
}
