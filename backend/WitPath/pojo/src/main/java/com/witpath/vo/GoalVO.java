package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习目标 VO
 */
@Data
public class GoalVO implements Serializable {

    private Long id;

    private String title;

    private String description;

    private String subject;

    private Integer priority;

    private Integer status;

    private String statusText;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal targetHours;

    private BigDecimal actualHours;

    private Integer taskTotal;

    private Integer taskDone;

    private BigDecimal progress;

    /** 距离截止日期的剩余天数，负数表示已逾期 */
    private Long daysLeft;

    /** 是否已逾期且未完成 */
    private Boolean overdue;

    private LocalDateTime createdAt;
}
