package com.chunyu.pregnancy.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 微信登录入参。只接收code，不允许客户端直接指定openid或本系统用户ID。 */
public class LoginRequest {
    // 要求文本非 null 且不能全是空白字符，用于必填文本。
    @NotBlank @Size(max = 256)
    public String code;

    /** 每次安装随机生成并持久化；不是硬件序列号。 */
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,64}")
    public String installationId;
}
