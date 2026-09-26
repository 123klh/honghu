package com.honghu.mapper;

import com.honghu.pojo.Paper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PaperMapper {

    @Select("SELECT * FROM paper WHERE paper_id = #{paperId}")
    Paper findById(Long paperId);

    @Select("SELECT * FROM paper WHERE paper_id = #{paperId} AND status = 1")
    Paper findActiveById(Long paperId);

    @Insert("INSERT INTO paper (paper_name, source, debut, questions_count, exam_duration, "
            + "status, subject, descr) "
            + "VALUES (#{paperName}, #{source}, #{debut}, #{questionsCount}, #{examDuration}, "
            + "#{status}, #{subject}, #{descr})")
    @Options(useGeneratedKeys = true, keyProperty = "paperId")
    int insert(Paper paper);

    @Update("UPDATE paper SET paper_name = #{paperName}, source = #{source}, debut = #{debut}, "
            + "exam_duration = #{examDuration}, subject = #{subject}, descr = #{descr}, status = #{status} "
            + "WHERE paper_id = #{paperId}")
    int update(Paper paper);

    @Select("SELECT DISTINCT p.* FROM paper p " +
            "INNER JOIN question q ON p.paper_id = q.paper_id " +
            "WHERE p.status = 1 AND q.status = 1 " +
            "ORDER BY p.paper_id DESC")
    List<Paper> findAllActive();

    @Select("SELECT DISTINCT p.* FROM paper p " +
            "INNER JOIN question q ON p.paper_id = q.paper_id " +
            "WHERE p.subject = #{subject} AND p.status = 1 AND q.status = 1 " +
            "ORDER BY p.paper_id DESC")
    List<Paper> findBySubject(String subject);

    @Select("""
            <script>
            SELECT * FROM paper WHERE status = 1
            <if test="subject != null and subject != ''">AND subject = #{subject}</if>
            <if test="keyword != null and keyword != ''">AND paper_name LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY paper_id DESC
            </script>
            """)
    List<Paper> search(@Param("subject") String subject, @Param("keyword") String keyword);

    @Update("UPDATE paper SET status = 0 WHERE paper_id = #{paperId}")
    int softDelete(Long paperId);

    @Select("SELECT COUNT(*) FROM paper WHERE status = 1")
    long countActive();
}
