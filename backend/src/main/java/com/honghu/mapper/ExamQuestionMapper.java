package com.honghu.mapper;

import com.honghu.pojo.ExamQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamQuestionMapper {

    /**
     * 写入开考快照。uk_exam_question 保证同一场考试同一题只有一行，
     * 因此重复调用（例如重试）不会产生重复题目。
     */
    @Insert("<script>"
            + "INSERT INTO exam_question (exam_id, question_id, question_order, answer_snapshot) "
            + "VALUES "
            + "<foreach collection='rows' item='r' separator=','>"
            + "(#{r.examId}, #{r.questionId}, #{r.questionOrder}, #{r.answerSnapshot})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE question_order = VALUES(question_order), "
            + "answer_snapshot = VALUES(answer_snapshot)"
            + "</script>")
    int insertBatch(@Param("rows") List<ExamQuestion> rows);

    @Select("SELECT * FROM exam_question WHERE exam_id = #{examId} ORDER BY question_order")
    List<ExamQuestion> findByExam(Long examId);

    @Select("SELECT COUNT(*) FROM exam_question WHERE exam_id = #{examId}")
    int countByExam(Long examId);
}
