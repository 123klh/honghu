package com.honghu.mapper;

import com.honghu.pojo.UserMastery;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMasteryMapper {

    @Select("SELECT * FROM user_mastery WHERE user_id = #{userId} AND kp_id = #{kpId}")
    UserMastery findByUserAndKp(@Param("userId") Long userId, @Param("kpId") Long kpId);

    @Insert("INSERT INTO user_mastery (user_id, kp_id, mastery_theta, review_due_at, "
            + "sm2_interval, sm2_ef, last_answered_at) "
            + "VALUES (#{userId}, #{kpId}, #{masteryTheta}, #{reviewDueAt}, "
            + "#{sm2Interval}, #{sm2Ef}, #{lastAnsweredAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserMastery mastery);

    @Update("UPDATE user_mastery SET mastery_theta = #{masteryTheta}, review_due_at = #{reviewDueAt}, "
            + "sm2_interval = #{sm2Interval}, sm2_ef = #{sm2Ef}, last_answered_at = #{lastAnsweredAt} "
            + "WHERE id = #{id}")
    int update(UserMastery mastery);

    @Select("SELECT * FROM user_mastery WHERE user_id = #{userId} ORDER BY mastery_theta ASC")
    List<UserMastery> findByUser(Long userId);

    /** 掌握度最低的 N 个知识点，薄弱倾向档位用 */
    @Select("SELECT * FROM user_mastery WHERE user_id = #{userId} "
            + "ORDER BY mastery_theta ASC LIMIT #{limit}")
    List<UserMastery> findWeakest(@Param("userId") Long userId, @Param("limit") int limit);

    /** 间隔复习到期的知识点 */
    @Select("SELECT * FROM user_mastery WHERE user_id = #{userId} "
            + "AND review_due_at IS NOT NULL AND review_due_at <= NOW() "
            + "ORDER BY review_due_at ASC LIMIT #{limit}")
    List<UserMastery> findDueForReview(@Param("userId") Long userId, @Param("limit") int limit);
}
