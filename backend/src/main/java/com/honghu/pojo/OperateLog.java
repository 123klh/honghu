package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OperateLog {
    private Long id;
    private Long operateUserId;
    private String operateUserType;
    private LocalDateTime operateTime;
    private String className;
    private String methodName;
    private String methodParams;
    private String returnValue;
    private Long costTime;
}
