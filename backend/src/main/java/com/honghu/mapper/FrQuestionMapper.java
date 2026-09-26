package com.honghu.mapper;

import com.honghu.pojo.FrQuestion;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FrQuestionMapper {

    @Select("SELECT * FROM fr_question WHERE fr_question_id = #{id} AND status = 1")
    FrQuestion findActiveById(Long id);

    @Select("SELECT fr_question_id FROM fr_question WHERE status = 1")
    List<Long> findAllActiveIds();

    @Select("""
            <script>
            SELECT * FROM fr_question WHERE status = 1
            <if test="subject != null and subject != ''">AND subject = #{subject}</if>
            <if test="frQuestionType != null and frQuestionType != ''">AND fr_question_type = #{frQuestionType}</if>
            <if test="keyword != null and keyword != ''">AND fr_question_stem LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY fr_question_id DESC
            </script>
            """)
    List<FrQuestion> search(@Param("subject") String subject,
                            @Param("frQuestionType") String frQuestionType,
                            @Param("keyword") String keyword);

    @Select("SELECT * FROM fr_question WHERE paper_id = #{paperId} AND status = 1 ORDER BY fr_question_id")
    List<FrQuestion> findByPaper(Long paperId);
}
