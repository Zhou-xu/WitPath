package com.witpath.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习记录新增 / 修改入参
 */
@Data
public class RecordSaveDTO implements Serializable {

    private Long id;

    private Long goalId;

    private Long taskId;

    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    private String content;

    private String subject;

    private LocalDate studyDate;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 直接录入的学习时长（分钟）；为空时由起止时间推导 */
    private Integer durationMinutes;

    /** 专注度评分 1~5 */
    private Integer focusScore;

    @Size(max = 500, message = "学习成果长度不能超过 500")
    private String outcome;

    /** 标签，逗号分隔 */
    private String tags;
}
