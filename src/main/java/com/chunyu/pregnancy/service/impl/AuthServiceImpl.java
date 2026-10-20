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
@Service
public class AuthServiceImpl implements AuthService {
  private final AppUserMapper users;
  private final AuthSessionMapper sessions;
  private final WechatGateway wechat;
  private final WechatProperties wechatProperties;
  private final TokenService tokens;
  private final AuthProperties authProperties;
  private final TransactionTemplate transactions;

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
  public TokenResponse login(LoginRequest request) {
    String openId = wechat.exchangeOpenId(request.code);
    // 单小程序首期不需要保存openid明文。未来多平台身份独立迁移，不能复用不同AppID的openid。
    String identityHash = DigestUtils.sha256(wechatProperties.getAppId() + ":" + openId);
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
  public TokenResponse refresh(String refreshToken) {
    String hash = DigestUtils.sha256(refreshToken);
    TokenResponse result =
        transactions.execute(
            status -> {
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
  public void logout(Long userId, String sessionUuid) {
    sessions.revokeOwned(sessionUuid, userId);
  }

  @Override
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
