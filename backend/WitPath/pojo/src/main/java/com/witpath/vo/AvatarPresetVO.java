package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 内置在线头像预设
 * <p>
 * svg 字段为完整的 SVG 源码，前端可直接编码为 data URI 预览；
 * 选中后由后端用同一份 SVG 上传至 OSS，保证预览与最终头像完全一致。
 * </p>
 */
@Data
public class AvatarPresetVO implements Serializable {

    /** 预设标识，如 amber-book */
    private String id;

    /** 预设名称，如「暖阳·书」 */
    private String name;

    /** SVG 源码（不含 XML 声明，UTF-8） */
    private String svg;
}
