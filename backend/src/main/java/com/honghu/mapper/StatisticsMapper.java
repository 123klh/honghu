package com.honghu.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 统计聚合查询。全部走真实明细表，不预存汇总值（除既有的冗余维护字段）。
 */
@Mapper
public interface StatisticsMapper {

    // ---------- 用户维度 ----------

    @Select("SELECT COUNT(*) FROM answer_record WHERE user_id = #{userId}")
    long totalAnswered(Long userId);

    @Select("SELECT COUNT(*) FROM answer_record WHERE user_id = #{userId} AND is_correct = 1")
    long totalCorrect(Long userId);

    /** 近 N 天每天的作答数与正确数，用于折线图。 */
    @Select("""
            SELECT DATE(created_at) AS day,
                   COUNT(*) AS answered,
                   SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correct
            FROM answer_record
            WHERE user_id = #{userId} AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY day
            """)
    List<Map<String, Object>> dailyActivity(@Param("userId") Long userId, @Param("days") int days);

    /** 各科目作答数与正确率，用于雷达图/柱状图。 */
    @Select("""
            SELECT COALESCE(subject, '未分类') AS subject,
                   COUNT(*) AS answered,
                   ROUND(AVG(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 4) AS accuracy
            FROM answer_record
            WHERE user_id = #{userId} AND is_correct IS NOT NULL
            GROUP BY subject ORDER BY answered DESC
            """)
    List<Map<String, Object>> subjectBreakdown(Long userId);

    @Select("SELECT COUNT(*) FROM exam_score WHERE user_id = #{userId} AND status = 'SUBMITTED'")
    long examsTaken(Long userId);

    @Select("SELECT ROUND(AVG(score), 2) FROM exam_score "
            + "WHERE user_id = #{userId} AND status = 'SUBMITTED' AND score IS NOT NULL")
    Double avgExamScore(Long userId);

    // ---------- 管理端维度 ----------
    // 字段别名与前端 Overview.vue 读取的键名保持一致，避免展示层取不到值。

    @Select("SELECT COUNT(*) FROM user")
    long userCount();

    @Select("SELECT COUNT(*) FROM user WHERE DATE(created_at) = CURDATE()")
    long newUsersToday();

    @Select("SELECT COUNT(*) FROM question WHERE status = 1")
    long questionCount();

    @Select("SELECT COUNT(*) FROM fr_question WHERE status = 1")
    long shenlunCount();

    @Select("SELECT COUNT(*) FROM answer_record WHERE DATE(created_at) = CURDATE()")
    long todayAnswers();

    @Select("SELECT COUNT(*) FROM answer_record "
            + "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)")
    long weekAnswers();

    @Select("SELECT COUNT(*) FROM discussion WHERE status = 1")
    long discussionCount();

    @Select("SELECT COUNT(*) FROM discussion_reply WHERE status = 1")
    long replyCount();

    /** 各题型题量分布，用于管理端概览饼图。 */
    @Select("""
            SELECT COALESCE(question_type, '未分类') AS questionType, COUNT(*) AS count
            FROM question WHERE status = 1 GROUP BY question_type ORDER BY count DESC
            """)
    List<Map<String, Object>> questionTypeStats();

    /** 近 N 天注册用户数，用于用户增长折线图。 */
    @Select("""
            SELECT DATE(created_at) AS date, COUNT(*) AS count
            FROM user WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY date
            """)
    List<Map<String, Object>> userGrowth(@Param("days") int days);

    /** 近 N 天每日活跃用户（有作答记录的去重用户数）。 */
    @Select("""
            SELECT DATE(created_at) AS date, COUNT(DISTINCT user_id) AS activeUsers
            FROM answer_record WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY date
            """)
    List<Map<String, Object>> dailyActive(@Param("days") int days);

    /** 热门题目排行：按作答次数取前 10。 */
    @Select("""
            SELECT q.question_stem AS questionStem, COUNT(ar.record_id) AS answerCount
            FROM answer_record ar
            JOIN question q ON q.question_id = ar.question_id
            GROUP BY ar.question_id, q.question_stem
            ORDER BY answerCount DESC LIMIT 10
            """)
    List<Map<String, Object>> popularQuestions();

    /** 最近活动：最新 10 条作答记录。 */
    @Select("""
            SELECT u.user_name AS userName,
                   '答题' AS action,
                   COALESCE(ar.subject, q.question_type, '') AS detail,
                   ar.created_at AS createdAt
            FROM answer_record ar
            JOIN user u ON u.user_id = ar.user_id
            LEFT JOIN question q ON q.question_id = ar.question_id
            ORDER BY ar.created_at DESC LIMIT 10
            """)
    List<Map<String, Object>> recentActivities();
}
