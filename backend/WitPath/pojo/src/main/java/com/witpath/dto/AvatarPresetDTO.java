package com.witpath.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 应用内置头像预设请求
 */
@Data
public class AvatarPresetDTO implements Serializable {

    @NotBlank(message = "请选择要使用的头像")
    private String presetId;
}
