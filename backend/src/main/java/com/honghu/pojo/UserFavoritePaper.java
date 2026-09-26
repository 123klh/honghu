package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserFavoritePaper {
    private Long id;
    private Long userId;
    private Long paperId;
    private String folder;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
