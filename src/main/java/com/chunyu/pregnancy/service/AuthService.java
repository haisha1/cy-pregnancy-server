package com.chunyu.pregnancy.service;

import com.chunyu.pregnancy.dto.request.LoginRequest;
import com.chunyu.pregnancy.dto.response.MeResponse;
import com.chunyu.pregnancy.dto.response.TokenResponse;

/** 账号业务契约。Controller依赖接口，具体事务与业务步骤由impl中的实现承担。 */
public interface AuthService {
    /** 使用真实微信code建立会话，不自动上传健康数据。 */
    TokenResponse login(LoginRequest request);

    /** 轮换续期凭据；发现旧凭据重放时撤销对应会话。 */
    TokenResponse refresh(String refreshToken);

    /** 仅撤销当前用户所属的指定会话。 */
    void logout(Long userId, String sessionUuid);

    /** 返回当前账户最小信息，不能直接暴露持久化实体。 */
    MeResponse me(Long userId);
}
