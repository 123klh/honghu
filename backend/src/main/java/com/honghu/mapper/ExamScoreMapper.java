package com.honghu.mapper;

import com.honghu.pojo.ExamScore;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ExamScoreMapper {

    @Insert("INSERT INTO exam_score (paper_id, user_id, score, total_score, duration, "
            + "start_time, submit_time, status, descr) "
            + "VALUES (#{paperId}, #{userId}, #{score}, #{totalScore}, #{duration}, "
            + "#{startTime}, #{submitTime}, #{status}, #{descr})")
    @Options(useGeneratedKeys = true, keyProperty = "examId")
    int insert(ExamScore examScore);

    @Select("SELECT * FROM exam_score WHERE exam_id = #{examId}")
    ExamScore findById(Long examId);

    /**
     * 提交前加行锁读取。并发的两次提交里，后到的那次会在这里等待，
     * 拿到锁时读到的已经是 SUBMITTED，从而被状态检查拦下。
     */
    @Select("SELECT * FROM exam_score WHERE exam_id = #{examId} FOR UPDATE")
    ExamScore findByIdForUpdate(Long examId);

    /**
     * 以 ONGOING 为前提的条件更新，返回 0 表示这场考试已被别的请求提交。
     * 行锁之外再加一层状态条件，避免依赖隔离级别的假设。
     */
    @Update("UPDATE exam_score SET score = #{score}, total_score = #{totalScore}, "
            + "duration = #{duration}, submit_time = #{submitTime}, status = 'SUBMITTED' "
            + "WHERE exam_id = #{examId} AND status = 'ONGOING'")
    int submitIfOngoing(ExamScore examScore);

    @Update("UPDATE exam_score SET score = #{score}, total_score = #{totalScore}, "
            + "duration = #{duration}, submit_time = #{submitTime}, status = #{status} "
            + "WHERE exam_id = #{examId}")
    int update(ExamScore examScore);

    @Select("SELECT * FROM exam_score WHERE user_id = #{userId} ORDER BY exam_id DESC")
    List<ExamScore> findByUser(Long userId);

    @Select("SELECT * FROM exam_score WHERE user_id = #{userId} AND paper_id = #{paperId} "
            + "ORDER BY exam_id DESC")
    List<ExamScore> findByUserAndPaper(@Param("userId") Long userId, @Param("paperId") Long paperId);

    @Select("SELECT AVG(score) FROM exam_score WHERE user_id = #{userId} AND status = 'SUBMITTED'")
    Double avgScoreByUser(Long userId);

    @Select("SELECT COUNT(*) FROM exam_score WHERE user_id = #{userId} AND status = 'SUBMITTED'")
    int countSubmittedByUser(Long userId);
}
