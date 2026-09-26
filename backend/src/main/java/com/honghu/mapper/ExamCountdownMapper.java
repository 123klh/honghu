package com.honghu.mapper;

import com.honghu.pojo.ExamCountdown;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ExamCountdownMapper {

    @Select("SELECT * FROM exam_countdown WHERE countdown_id = #{countdownId}")
    ExamCountdown findById(Long countdownId);

    /** 未过期的倒计时，按考试日期升序 */
    @Select("SELECT * FROM exam_countdown WHERE status = 1 AND exam_date >= CURDATE() "
            + "ORDER BY exam_date ASC, sort_order ASC")
    List<ExamCountdown> findActive();

    @Select("SELECT * FROM exam_countdown ORDER BY exam_date ASC, sort_order ASC")
    List<ExamCountdown> findAll();

    @Insert("INSERT INTO exam_countdown (exam_name, exam_date, sort_order, status) "
            + "VALUES (#{examName}, #{examDate}, #{sortOrder}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "countdownId")
    int insert(ExamCountdown countdown);

    @Update("UPDATE exam_countdown SET exam_name = #{examName}, exam_date = #{examDate}, "
            + "sort_order = #{sortOrder}, status = #{status} WHERE countdown_id = #{countdownId}")
    int update(ExamCountdown countdown);

    @Delete("DELETE FROM exam_countdown WHERE countdown_id = #{countdownId}")
    int deleteById(Long countdownId);
}
