package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QuestionDifficulty {
    private Long questionId;

    /** IRT 难度 b，logit 尺度 */
    private Double difficultyValue;

    /** 题源给出的真实正确率（如粉笔大数据），0-1 */
    private Double sourceAccuracyRate;

    /** 题源标注的易错项 */
    private String commonMistake;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
