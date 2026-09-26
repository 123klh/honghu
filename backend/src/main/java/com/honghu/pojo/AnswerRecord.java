package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AnswerRecord {
    private Long recordId;
    private Long examId;
    private Long questionId;
    private Long userId;
    private String userAnswer;

    /** 客观题对错；申论题由 scoreRate 承载得分率，二者语义分离（修正原论文一字多用） */
    private Boolean isCorrect;
    private Double scoreRate;

    /** 作答耗时，秒 */
    private Integer duration;
    private LocalDateTime createdAt;
    private String subject;

    /** 0=无需复核 1=待人工复核 2=已复核 */
    private Integer reviewStatus;
}
