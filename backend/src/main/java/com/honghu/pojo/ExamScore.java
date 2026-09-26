package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExamScore {
    private Long examId;
    private Long paperId;
    private Long userId;
    private Double score;
    private Double totalScore;

    /** 实际用时，秒 */
    private Integer duration;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;

    /** ONGOING / SUBMITTED */
    private String status;
    private String descr;
}
