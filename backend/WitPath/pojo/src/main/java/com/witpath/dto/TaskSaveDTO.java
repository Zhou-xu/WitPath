package com.witpath.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 学习任务新增 / 修改入参
 */
@Data
public class TaskSaveDTO implements Serializable {

    private Long id;

    private Long goalId;

    @Size(max = 200, message = "任务标题长度不能超过 200")
    private String title;

    @Size(max = 500, message = "任务描述长度不能超过 500")
    private String description;

    /** 状态：见 com.witpath.enumeration.TaskStatus */
    private Integer status = 1;

    /** 优先级：1 低 2 中 3 高 */
    private Integer priority = 2;

    private LocalDate planDate;

    private LocalDate dueDate;

    private Integer estimatedMinutes = 0;

    private Integer sortOrder = 0;
}
