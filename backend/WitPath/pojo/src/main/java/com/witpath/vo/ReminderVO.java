package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提醒 VO
 */
@Data
public class ReminderVO implements Serializable {

    private Long id;

    private Long goalId;

    private Long taskId;

    private String type;

    /** 级别：1 普通 2 重要 3 紧急 */
    private Integer level;

    private String title;

    private String content;

    private String source;

    private Integer status;

    private String statusText;

    private LocalDateTime triggerTime;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
