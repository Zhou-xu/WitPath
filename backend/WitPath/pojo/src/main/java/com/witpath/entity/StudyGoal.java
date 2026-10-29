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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习目标实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("study_goal")
public class StudyGoal implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    /** 学科/分类 */
    private String subject;

    /** 优先级：1 低 2 中 3 高 */
    private Integer priority;

    /** 状态：见 com.witpath.enumeration.GoalStatus */
    private Integer status;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 计划投入小时数 */
    private BigDecimal targetHours;

    /** 实际投入小时数（学习记录汇总） */
    private BigDecimal actualHours;

    private Integer taskTotal;

    private Integer taskDone;

    /** 完成进度 0~100 */
    private BigDecimal progress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
