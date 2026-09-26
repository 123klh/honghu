package com.honghu.mapper;

import com.honghu.pojo.Announcement;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AnnouncementMapper {

    @Select("SELECT * FROM announcement WHERE announcement_id = #{announcementId}")
    Announcement findById(Long announcementId);

    @Select("SELECT * FROM announcement WHERE status = 1 ORDER BY created_at DESC")
    List<Announcement> findActive();

    @Select("SELECT * FROM announcement ORDER BY created_at DESC")
    List<Announcement> findAll();

    @Insert("INSERT INTO announcement (title, content, link_url, status) "
            + "VALUES (#{title}, #{content}, #{linkUrl}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "announcementId")
    int insert(Announcement announcement);

    @Update("UPDATE announcement SET title = #{title}, content = #{content}, "
            + "link_url = #{linkUrl}, status = #{status} WHERE announcement_id = #{announcementId}")
    int update(Announcement announcement);

    @Delete("DELETE FROM announcement WHERE announcement_id = #{announcementId}")
    int deleteById(Long announcementId);
}
