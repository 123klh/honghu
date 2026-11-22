package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Discussion {
    private Long discussionId;
    private Long questionId;

    /** OBJECTIVE / SHENLUN / NONE（纯提问帖） */
    private String questionType;

    private Long userId;
    private String title;
    private String content;
    private Boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 详情视图的作者名与有效回复数量。 */
    private String authorName;
    private Long replyCount;

    /** 1=正常 0=已删除 */
    private Integer status;
}
