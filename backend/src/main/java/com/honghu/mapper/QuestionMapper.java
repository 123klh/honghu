package com.honghu.mapper;

import com.honghu.pojo.Question;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface QuestionMapper {

    @Select("SELECT * FROM question WHERE question_id = #{questionId}")
    Question findById(Long questionId);

    @Select("SELECT * FROM question WHERE question_id = #{questionId} AND status = 1")
    Question findActiveById(Long questionId);

    @Select("SELECT * FROM question WHERE question_code = #{questionCode}")
    Question findByCode(String questionCode);

    @Insert("INSERT INTO question (question_code, subject, question_stem, option_a, option_b, "
            + "option_c, option_d, enclosure, answer, question_type, avg_accuracy_rate, paper_id, "
            + "status, explanation) "
            + "VALUES (#{questionCode}, #{subject}, #{questionStem}, #{optionA}, #{optionB}, "
            + "#{optionC}, #{optionD}, #{enclosure}, #{answer}, #{questionType}, #{avgAccuracyRate}, "
            + "#{paperId}, #{status}, #{explanation})")
    @Options(useGeneratedKeys = true, keyProperty = "questionId")
    int insert(Question question);

    @Update("UPDATE question SET subject = #{subject}, question_stem = #{questionStem}, "
            + "option_a = #{optionA}, option_b = #{optionB}, option_c = #{optionC}, option_d = #{optionD}, "
            + "answer = #{answer}, question_type = #{questionType}, explanation = #{explanation}, "
            + "enclosure = #{enclosure}, paper_id = #{paperId}, status = #{status} "
            + "WHERE question_id = #{questionId}")
    int update(Question question);

    @Select("SELECT COUNT(*) FROM question WHERE question_type = #{questionType} AND status = 1")
    int countByType(String questionType);

    @Select("SELECT COUNT(*) FROM question WHERE status = 1")
    long countAll();

    /**
     * 取候选 ID 池，由应用层洗牌后抽样。
     * 替代原论文的 ORDER BY RAND()：大表下 RAND() 要全表扫描 + 排序，而这里只走索引取主键。
     */
    @Select("SELECT question_id FROM question WHERE question_type = #{questionType} AND status = 1")
    List<Long> findIdsByType(@Param("questionType") String questionType);

    @Select("SELECT question_id FROM question WHERE subject = #{subject} AND status = 1")
    List<Long> findIdsBySubject(@Param("subject") String subject);

    @Select("SELECT question_id FROM question WHERE status = 1")
    List<Long> findAllActiveIds();

    @Select("SELECT question_id FROM question WHERE paper_id = #{paperId} AND status = 1 ORDER BY question_id")
    List<Long> findIdsByPaper(@Param("paperId") Long paperId);

    @Select("""
            <script>
            SELECT * FROM question WHERE question_id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Question> findByIds(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM question WHERE paper_id = #{paperId} AND status = 1 ORDER BY question_id")
    List<Question> findByPaper(@Param("paperId") Long paperId);

    @Select("""
            <script>
            SELECT * FROM question WHERE status = 1
            <if test="subject != null and subject != ''">AND subject = #{subject}</if>
            <if test="questionType != null and questionType != ''">AND question_type = #{questionType}</if>
            <if test="keyword != null and keyword != ''">AND question_stem LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY question_id DESC
            </script>
            """)
    List<Question> search(@Param("subject") String subject,
                          @Param("questionType") String questionType,
                          @Param("keyword") String keyword);

    @Update("UPDATE question SET status = 0 WHERE question_id = #{questionId}")
    int softDelete(Long questionId);
}
