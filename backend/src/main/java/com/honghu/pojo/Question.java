package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Question {
    private Long questionId;
    private String questionCode;
    private String subject;
    private String questionStem;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private Boolean enclosure;
    private String answer;
    private String questionType;
    private Double avgAccuracyRate;
    private Long paperId;
    private LocalDateTime createdAt;
    private Integer status;
    private String explanation;
}
