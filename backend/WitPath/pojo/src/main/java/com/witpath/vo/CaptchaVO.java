package com.witpath.vo;

import lombok.Data;

/**
 * 图形验证码 VO
 * <p>
 * captchaKey 即 LoginInitId（雪花 ID，19 位大整数）。为避免 JS 端
 * Number 精度丢失（安全整数上限 2^53-1），必须以字符串返回，
 * 端到端全程按字符串透传。
 * </p>
 */
@Data
public class CaptchaVO {

    private String captchaKey;

    private String captchaImage;

    public CaptchaVO(String captchaKey, String captchaImage) {
        this.captchaKey = captchaKey;
        this.captchaImage = captchaImage;
    }
}
