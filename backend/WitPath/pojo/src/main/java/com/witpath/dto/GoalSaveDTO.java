package com.witpath.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学习目标新增 / 修改入参
 */
@Data
public class GoalSaveDTO implements Serializable {

    private Long id;

    @NotNull(message = "目标标题不能为空")
    @Size(max = 120, message = "标题长度不能超过 120")
    private String title;

    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    private String subject;

    /** 优先级：1 低 2 中 3 高 */
    private Integer priority = 2;

    /** 状态：见 com.witpath.enumeration.GoalStatus */
    private Integer status = 1;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal targetHours = BigDecimal.ZERO;
}
