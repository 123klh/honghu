package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DiscussionReply {
    private Long replyId;
    private Long discussionId;
    private Long userId;
    private String content;
    private Boolean isAccepted;
    private Integer likesCount;
    private LocalDateTime createdAt;

    /** 1=正常 0=已删除 */
    private Integer status;
}
