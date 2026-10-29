package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习记录 VO
 */
@Data
public class RecordVO implements Serializable {

    private Long id;

    private Long goalId;

    private String goalTitle;

    private Long taskId;

    private String taskTitle;

    private String title;

    private String content;

    private String subject;

    private LocalDate studyDate;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Integer durationMinutes;

    /** 格式化后的时长文本，如 2 小时 30 分钟 */
    private String durationText;

    private Integer focusScore;

    private String outcome;

    private String tags;

    private LocalDateTime createdAt;
}
