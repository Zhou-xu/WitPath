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
 * 学习任务实体（目标拆解后的可执行单元）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("study_task")
public class StudyTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long goalId;

    private String title;

    private String description;

    /** 状态：见 com.witpath.enumeration.TaskStatus */
    private Integer status;

    /** 优先级：1 低 2 中 3 高 */
    private Integer priority;

    private LocalDate planDate;

    private LocalDate dueDate;

    private Integer estimatedMinutes;

    private Integer actualMinutes;

    private LocalDateTime completedAt;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
