package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WrongBook {
    private Long id;
    private Long userId;
    private Long questionId;

    /** OBJECTIVE / SHENLUN */
    private String questionType;

    private LocalDateTime addedAt;
    private LocalDateTime masteredAt;

    /** WRONG / MASTERED */
    private String status;
    private Integer retryCount;
}
