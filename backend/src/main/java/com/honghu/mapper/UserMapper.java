package com.honghu.mapper;

import com.honghu.pojo.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE user_name = #{userName}")
    User selectByUserName(@Param("userName") String userName);

    @Select("SELECT * FROM user WHERE user_id = #{userId}")
    User selectById(@Param("userId") Long userId);

    @Select("SELECT * FROM user WHERE email = #{email} LIMIT 1")
    User selectByEmail(@Param("email") String email);

    @Insert("INSERT INTO user (user_name, password_hash, gender, birthday, address, email, descr) "
            + "VALUES (#{userName}, #{passwordHash}, #{gender}, #{birthday}, #{address}, #{email}, #{descr})")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insert(User user);

    @Update("UPDATE user SET password_hash = #{passwordHash} WHERE user_id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}
