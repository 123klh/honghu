package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Paper {
    private Long paperId;
    private String paperName;
    private String source;
    private String debut;
    private Integer questionsCount;
    private Double avgScore;
    private Integer examDuration;
    private LocalDateTime createdAt;
    private Integer status;
    private String subject;
    private String descr;
}
