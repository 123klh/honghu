package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Announcement {
    private Long announcementId;
    private String title;
    private String content;
    private String linkUrl;
    private LocalDateTime createdAt;
    private Integer status;
}
