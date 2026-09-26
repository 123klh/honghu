package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FrQuestion {
    private Long frQuestionId;
    private String questionCode;
    private String material;
    private String frQuestionStem;
    private String answer;
    private String frQuestionType;
    private Integer points;
    private Double avgScore;
    private Long paperId;
    private LocalDateTime createdAt;
    private Integer status;
    private String subject;
    private String descr;
}
