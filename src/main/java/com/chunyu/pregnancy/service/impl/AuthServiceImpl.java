package com.chunyu.pregnancy.service.impl;

import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.common.util.DigestUtils;
import com.chunyu.pregnancy.config.AuthProperties;
import com.chunyu.pregnancy.config.WechatProperties;
import com.chunyu.pregnancy.dto.request.*;
import com.chunyu.pregnancy.dto.response.*;
import com.chunyu.pregnancy.entity.*;
import com.chunyu.pregnancy.integration.wechat.WechatGateway;
import com.chunyu.pregnancy.mapper.*;
import com.chunyu.pregnancy.security.TokenService;
import com.chunyu.pregnancy.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 登录应用服务：控制业务顺序和事务边界。 微信网络调用放在事务外，避免等待微信时长时间占用数据库锁。 token只在事务提交成功后返回；本服务完全不读取或上传孕期健康记录。 */
// 业务层组件：由 Spring 管理，负责组织业务步骤；事务边界仍需由事务代码明确控制。
@Service
public class AuthServiceImpl implements AuthService {
  private final AppUserMapper users;
  private final AuthSessionMapper sessions;
  private final WechatGateway wechat;
  private final WechatProperties wechatProperties;
  private final TokenService tokens;
  private final AuthProperties authProperties;
  private final TransactionTemplate transactions;

  // 单一构造器由 Spring 自动注入依赖；final 字段表明这些依赖在构造后不再替换。
  public AuthServiceImpl(
      AppUserMapper users,
      AuthSessionMapper sessions,
      WechatGateway wechat,
      WechatProperties wechatProperties,
      TokenService tokens,
      AuthProperties authProperties,
      TransactionTemplate transactions) {
    this.users = users;
    this.sessions = sessions;
    this.wechat = wechat;
    this.wechatProperties = wechatProperties;
    this.tokens = tokens;
    this.authProperties = authProperties;
    this.transactions = transactions;
  }

  @Override
  // 登录主流程：向微信确认身份 → 查找或创建用户 → 保存可撤销会话 → 返回访问与刷新令牌。
  public TokenResponse login(LoginRequest request) {
    // 外部 HTTP 调用先完成，再进入数据库事务；微信响应慢时不会一直占用数据库事务。
    String openId = wechat.exchangeOpenId(request.code);
    // 单小程序首期不需要保存openid明文。未来多平台身份独立迁移，不能复用不同AppID的openid。
    String identityHash = DigestUtils.sha256(wechatProperties.getAppId() + ":" + openId);
    // TransactionTemplate 管理事务：回调成功后提交；未处理的运行时异常会触发回滚。
    return transactions.execute(
        status -> {
          users.ensureUser(wechatProperties.getAppId(), identityHash);
          AppUser user = users.findIdentity(wechatProperties.getAppId(), identityHash);
          requireActive(user);
          String refreshToken = tokens.randomRefreshToken();
          AuthSession session = new AuthSession();
          session.setClientUuid(UUID.randomUUID().toString());
          session.setUserId(user.getId());
          session.setInstallationId(request.installationId);
          session.setRefreshTokenHash(DigestUtils.sha256(refreshToken));
          session.setCreatedAt(Instant.now());
          session.setExpiresAt(Instant.now().plusSeconds(authProperties.getRefreshSeconds()));
          sessions.insert(session);
          return tokenResponse(session, refreshToken);
        });
  }

  @Override
  // 续期主流程：锁定刷新凭据对应的会话 → 校验状态 → 记下旧凭据 → 轮换新凭据。
  public TokenResponse refresh(String refreshToken) {
    String hash = DigestUtils.sha256(refreshToken);
    TokenResponse result =
        transactions.execute(
            status -> {
              // Mapper 内部 SELECT FOR UPDATE 加行锁，使并发请求不能同时消费同一刷新令牌。
              AuthSession session = sessions.lockByRefreshHash(hash);
              if (session == null) {
                // 旧refresh再次出现时撤销整条会话。返回null后在事务外抛错，保证撤销不会被回滚。
                Long replayedId = sessions.findReplayedSession(hash);
                if (replayedId != null) {
                  sessions.revokeById(replayedId);
                }
                return null;
              }
              if (session.getRevokedAt() != null
                  || !session.getExpiresAt().isAfter(Instant.now())) {
                return null;
              }
              AppUser user = users.selectById(session.getUserId());
              if (user == null || !"active".equals(user.getAccountStatus())) {
                return null;
              }
              // 保存已消费凭据的散列，用于识别重放；数据库中不保存刷新令牌明文。
              sessions.recordUsedRefresh(hash, session.getId());
              String nextToken = tokens.randomRefreshToken();
              session.setRefreshTokenHash(DigestUtils.sha256(nextToken));
              // 绝对到期时间保持原值，不能每次刷新让会话无限续命。
              sessions.updateById(session);
              return tokenResponse(session, nextToken);
            });
    if (result == null) {
      throw new BizException(401, "AUTH_EXPIRED", "会话无效，请重新登录");
    }
    return result;
  }

  @Override
  // 退出只撤销当前用户拥有的这条会话，不删除用户，也不影响其他设备的合法会话。
  public void logout(Long userId, String sessionUuid) {
    sessions.revokeOwned(sessionUuid, userId);
  }

  @Override
  // 从数据库读取可公开的账号资料，再组装 DTO，避免把身份散列等内部字段返回客户端。
  public MeResponse me(Long userId) {
    AppUser user = users.selectById(userId);
    requireActive(user);
    return new MeResponse(
        user.getPublicUuid(),
        user.getAccountStatus(),
        user.getCloudDataEpoch(),
        false,
        user.getNickname(),
        users.hasAvatar(userId),
        user.getProfileRevision());
  }

  private TokenResponse tokenResponse(AuthSession session, String refreshToken) {
    return new TokenResponse(
        tokens.accessToken(session.getUserId(), session.getClientUuid()),
        refreshToken,
        "Bearer",
        authProperties.getAccessSeconds(),
        session.getExpiresAt());
  }

  private void requireActive(AppUser user) {
    if (user == null || !"active".equals(user.getAccountStatus())) {
      throw new BizException(401, "AUTH_EXPIRED", "账户不可用，请重新登录");
    }
  }
}
