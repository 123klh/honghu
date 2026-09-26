package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ShenlunRubric {
    private Long rubricId;
    private Long frQuestionId;
    private Integer pointOrder;
    private String pointText;
    private Double pointScore;
    private LocalDateTime createdAt;

    /** 关联的同义表述，由 shenlun_rubric_synonym 装配，用于要点召回匹配 */
    private List<String> synonyms;
}
