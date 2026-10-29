package com.witpath.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 服务回调的阶段性进度事件
 */
@Data
public class AiProgressDTO implements Serializable {

    /** 任务编号 */
    private String taskNo;

    /** 阶段标识，见 com.witpath.enumeration.AiStage */
    private String stage;

    private String stageMessage;

    /** 阶段内进度百分比（0~100），为空时按阶段默认值计算 */
    private Integer progress;

    /** 已消耗 token */
    private Integer tokensUsed;

    /** 使用的模型标识 */
    private String model;
}
