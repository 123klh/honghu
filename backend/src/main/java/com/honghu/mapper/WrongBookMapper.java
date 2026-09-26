package com.honghu.mapper;

import com.honghu.pojo.WrongBook;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WrongBookMapper {

    @Select("SELECT * FROM wrong_book WHERE user_id = #{userId} AND question_id = #{questionId} "
            + "AND question_type = #{questionType}")
    WrongBook findByUserAndQuestion(@Param("userId") Long userId,
                                    @Param("questionId") Long questionId,
                                    @Param("questionType") String questionType);

    @Insert("INSERT INTO wrong_book (user_id, question_id, question_type, status, retry_count) "
            + "VALUES (#{userId}, #{questionId}, #{questionType}, #{status}, #{retryCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WrongBook wrongBook);

    /** 再次答错：回到 WRONG 状态并累加重练次数 */
    @Update("UPDATE wrong_book SET status = 'WRONG', mastered_at = NULL, "
            + "retry_count = retry_count + 1 WHERE id = #{id}")
    int markWrongAgain(Long id);

    @Update("UPDATE wrong_book SET status = 'MASTERED', mastered_at = #{masteredAt}, "
            + "retry_count = retry_count + 1 WHERE id = #{id}")
    int markMastered(@Param("id") Long id, @Param("masteredAt") LocalDateTime masteredAt);

    @Select("SELECT * FROM wrong_book WHERE user_id = #{userId} AND status = 'WRONG' "
            + "ORDER BY added_at DESC")
    List<WrongBook> findWrongByUser(Long userId);

    @Select("SELECT question_id FROM wrong_book WHERE user_id = #{userId} AND status = 'WRONG' "
            + "AND question_type = 'OBJECTIVE'")
    List<Long> findWrongQuestionIds(Long userId);

    @Select("SELECT COUNT(*) FROM wrong_book WHERE user_id = #{userId} AND status = 'WRONG'")
    int countWrongByUser(Long userId);

    @Select("SELECT COUNT(*) FROM wrong_book WHERE user_id = #{userId} AND status = 'MASTERED'")
    int countMasteredByUser(Long userId);

    @Delete("DELETE FROM wrong_book WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
