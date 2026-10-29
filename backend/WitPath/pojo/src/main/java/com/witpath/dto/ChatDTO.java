package com.witpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 助手对话请求入参
 */
@Data
public class ChatDTO implements Serializable {

    /** 会话 ID，为空表示新建会话 */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过 4000 字")
    private String message;

    /** 是否携带个人学习上下文 */
    private Boolean withContext = true;
}
