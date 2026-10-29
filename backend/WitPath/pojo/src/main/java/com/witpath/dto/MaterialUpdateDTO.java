package com.witpath.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 学习资料元信息修改入参
 */
@Data
public class MaterialUpdateDTO implements Serializable {

    @Size(max = 255, message = "资料名称长度不能超过 255")
    private String name;

    @Size(max = 50, message = "学科长度不能超过 50")
    private String subject;

    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;
}
