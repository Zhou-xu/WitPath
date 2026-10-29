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
 * AI 任务实体（Spring Boot 发起、FastAPI + LangGraph 执行的留痕记录）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_task")
public class AiTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的任务编号 */
    private String taskNo;

    private Long userId;

    /** 任务类型：见 com.witpath.enumeration.AiTaskType */
    private String type;

    private String title;

    private String question;

    /** 聚焦分析的目标 ID，为空表示分析全部进行中目标 */
    private Long focusGoalId;

    /** 状态：见 com.witpath.enumeration.AiTaskStatus */
    private Integer status;

    /** 当前执行阶段 */
    private String stage;

    private String stageMessage;

    /** 总体进度 0~100 */
    private Integer progress;

    private String requestJson;

    private String resultJson;

    private String summary;

    private String suggestion;

    private String model;

    private Integer tokensUsed;

    private Integer latencyMs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
