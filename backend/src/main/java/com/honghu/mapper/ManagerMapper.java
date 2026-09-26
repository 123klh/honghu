package com.honghu.mapper;

import com.honghu.pojo.Manager;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ManagerMapper {

    @Select("SELECT * FROM manager WHERE manager_name = #{managerName}")
    Manager selectByManagerName(@Param("managerName") String managerName);

    @Select("SELECT * FROM manager WHERE manager_id = #{managerId}")
    Manager selectById(@Param("managerId") Long managerId);

    @Select("SELECT COUNT(*) FROM manager")
    long countAll();

    @Insert("INSERT INTO manager (manager_name, password_hash, role, status, descr) "
            + "VALUES (#{managerName}, #{passwordHash}, #{role}, 1, #{descr})")
    int insertAdmin(@Param("managerName") String managerName,
                    @Param("passwordHash") String passwordHash,
                    @Param("role") String role,
                    @Param("descr") String descr);

    @Update("UPDATE manager SET password_hash = #{passwordHash} WHERE manager_id = #{managerId}")
    int updatePassword(@Param("managerId") Long managerId, @Param("passwordHash") String passwordHash);
}
