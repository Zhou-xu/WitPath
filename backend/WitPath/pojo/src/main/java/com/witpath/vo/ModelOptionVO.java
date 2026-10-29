package com.witpath.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型下拉选项 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelOptionVO {

    /** 模型标识（提交保存时使用） */
    private String id;

    /** 展示名（部分服务商带别名，缺省回落到 id） */
    private String name;
}
