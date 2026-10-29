package com.witpath.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习记录实体（最小学习事实单元）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("study_record")
public class StudyRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long goalId;

    private Long taskId;

    private String title;

    private String content;

    private String subject;

    private LocalDate studyDate;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 学习时长（分钟） */
    private Integer durationMinutes;

    /** 专注度评分 1~5 */
    private Integer focusScore;

    private String outcome;

    /** 标签，逗号分隔 */
    private String tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
