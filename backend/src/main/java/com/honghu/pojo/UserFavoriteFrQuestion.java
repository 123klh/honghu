package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserFavoriteFrQuestion {
    private Long id;
    private Long userId;
    private Long frQuestionId;
    private String folder;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
