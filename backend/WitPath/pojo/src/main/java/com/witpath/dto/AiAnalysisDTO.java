package com.witpath.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 分析任务提交入参
 */
@Data
public class AiAnalysisDTO implements Serializable {

    /** 任务类型：见 com.witpath.enumeration.AiTaskType */
    private String type = "STUDY_ANALYSIS";

    /** 聚焦的目标 ID，为空表示分析全部进行中目标 */
    private Long goalId;

    /** 分析的学习数据窗口天数，默认 14 天 */
    private Integer rangeDays = 14;

    /** 用户补充的提问 */
    @Size(max = 1000, message = "问题描述不能超过 1000 字")
    private String question;
}
