package com.honghu.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserMastery {
    private Long id;
    private Long userId;
    private Long kpId;

    /** IRT 能力值 θ，logit 尺度，初值 0 */
    private Double masteryTheta;

    /** SM-2 下次复习到期时间 */
    private LocalDateTime reviewDueAt;

    /** SM-2 间隔天数 */
    private Integer sm2Interval;

    /** SM-2 easiness factor，初值 2.5 */
    private Double sm2Ef;

    private LocalDateTime lastAnsweredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
