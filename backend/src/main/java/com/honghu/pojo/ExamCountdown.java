package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExamCountdown {
    private Long countdownId;
    private String examName;
    private LocalDate examDate;
    private Integer sortOrder;
    private Integer status;
}
