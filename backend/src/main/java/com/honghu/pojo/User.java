package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class User {
    private Long userId;
    private String userName;
    private String passwordHash;
    private String gender;
    private LocalDate birthday;
    private String address;
    private String email;
    private String descr;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
