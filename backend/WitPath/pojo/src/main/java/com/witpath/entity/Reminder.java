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
import java.time.LocalDateTime;

/**
 * 提醒实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("reminder")
public class Reminder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long goalId;

    private Long taskId;

    /** 类型：TASK_DUE / GOAL_DEADLINE / AI_SUGGESTION / DAILY */
    private String type;

    /** 级别：1 普通 2 重要 3 紧急 */
    private Integer level;

    private String title;

    private String content;

    /** 来源：SYSTEM / AI */
    private String source;

    /** 状态：见 com.witpath.enumeration.ReminderStatus */
    private Integer status;

    private LocalDateTime triggerTime;

    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
