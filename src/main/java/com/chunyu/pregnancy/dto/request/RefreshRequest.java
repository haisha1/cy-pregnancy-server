package com.chunyu.pregnancy.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 续期入参，与登录code和业务接口Bearer凭据分开。 */
public class RefreshRequest {
    // 要求文本非 null 且不能全是空白字符，用于必填文本。
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}")
    public String refreshToken;
}
