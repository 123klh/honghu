package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KnowledgePoint {
    private Long kpId;
    private String kpName;
    private Long parentId;
    private String subject;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
