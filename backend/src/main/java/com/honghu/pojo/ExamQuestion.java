package com.honghu.pojo;

import lombok.Data;

/**
 * 考试题目快照。
 *
 * <p>开考瞬间固定题目集合、顺序与正确答案。提交时以快照判分，
 * 这样管理员中途改题或下架题目都不会影响正在进行的考试。
 */
@Data
public class ExamQuestion {
    private Long id;
    private Long examId;
    private Long questionId;
    private Integer questionOrder;

    /** 开考瞬间的正确答案 */
    private String answerSnapshot;
}
