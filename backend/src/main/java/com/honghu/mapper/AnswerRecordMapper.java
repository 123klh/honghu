package com.honghu.mapper;

import com.honghu.pojo.AnswerRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnswerRecordMapper {

    @Insert("INSERT INTO answer_record (exam_id, question_id, user_id, user_answer, is_correct, "
            + "score_rate, duration, subject, review_status) "
            + "VALUES (#{examId}, #{questionId}, #{userId}, #{userAnswer}, #{isCorrect}, "
            + "#{scoreRate}, #{duration}, #{subject}, #{reviewStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "recordId")
    int insert(AnswerRecord record);

    @Select("SELECT * FROM answer_record WHERE record_id = #{recordId}")
    AnswerRecord findById(Long recordId);

    @Select("SELECT * FROM answer_record WHERE user_id = #{userId} AND question_id = #{questionId} "
            + "ORDER BY created_at DESC LIMIT 1")
    AnswerRecord findLatestByUserAndQuestion(@Param("userId") Long userId,
                                            @Param("questionId") Long questionId);

    @Select("SELECT * FROM answer_record WHERE exam_id = #{examId} ORDER BY record_id")
    List<AnswerRecord> findByExam(Long examId);

    @Select("SELECT * FROM answer_record WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AnswerRecord> findByUser(Long userId);

    @Select("SELECT COUNT(*) FROM answer_record WHERE user_id = #{userId}")
    int countByUser(Long userId);

    @Select("SELECT COUNT(*) FROM answer_record WHERE user_id = #{userId} AND is_correct = 1")
    int countCorrectByUser(Long userId);

    /** 按学科聚合的答题统计，供学习数据可视化使用 */
    @Select("SELECT subject, COUNT(*) AS total, SUM(is_correct = 1) AS correct "
            + "FROM answer_record WHERE user_id = #{userId} AND subject IS NOT NULL GROUP BY subject")
    List<Map<String, Object>> statsBySubject(Long userId);

    /** 近 N 天每日答题量与正确率 */
    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS total, SUM(is_correct = 1) AS correct "
            + "FROM answer_record WHERE user_id = #{userId} "
            + "AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> statsByDay(@Param("userId") Long userId, @Param("days") int days);

    /** 待人工复核的申论作答 */
    @Select("SELECT * FROM answer_record WHERE review_status = 1 ORDER BY created_at")
    List<AnswerRecord> findPendingReview();

    @Update("UPDATE answer_record SET score_rate = #{scoreRate}, review_status = 2 WHERE record_id = #{recordId}")
    int applyReview(@Param("recordId") Long recordId, @Param("scoreRate") Double scoreRate);
}
