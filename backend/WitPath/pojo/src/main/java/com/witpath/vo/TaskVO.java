package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习任务 VO
 */
@Data
public class TaskVO implements Serializable {

    private Long id;

    private Long goalId;

    private String goalTitle;

    private String title;

    private String description;

    private Integer status;

    private String statusText;

    private Integer priority;

    private LocalDate planDate;

    private LocalDate dueDate;

    private Integer estimatedMinutes;

    private Integer actualMinutes;

    private LocalDateTime completedAt;

    private Integer sortOrder;

    /** 是否已逾期（截止日期早于今天且未完成） */
    private Boolean overdue;

    /** 距离截止日期剩余天数 */
    private Long daysLeft;

    private LocalDateTime createdAt;
}
