package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Manager {
    private Long managerId;
    private String managerName;
    private String passwordHash;
    private String role;
    private Integer status;
    private String descr;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
