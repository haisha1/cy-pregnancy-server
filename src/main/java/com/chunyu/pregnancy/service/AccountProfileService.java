package com.chunyu.pregnancy.service;

import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.common.util.*;
import com.chunyu.pregnancy.config.WechatProperties;
import com.chunyu.pregnancy.dto.response.MeResponse;
import com.chunyu.pregnancy.entity.AppUser;
import com.chunyu.pregnancy.integration.wechat.WechatGateway;
import com.chunyu.pregnancy.mapper.AppUserMapper;
import org.springframework.stereotype.Service;

/** 资料可选，不与登录、健康数据授权捆绑。微信网络检查在数据库更新之前完成。 */
// 业务层组件：由 Spring 管理，负责组织业务步骤；事务边界仍需由事务代码明确控制。
@Service
public class AccountProfileService {
  private final AppUserMapper users;
  private final WechatGateway wechat;
  private final WechatProperties properties;
  private final AuthService auth;

  // 单一构造器由 Spring 自动注入依赖；final 字段表明这些依赖在构造后不再替换。
  public AccountProfileService(
      AppUserMapper users, WechatGateway wechat, WechatProperties properties, AuthService auth) {
    this.users = users;
    this.wechat = wechat;
    this.properties = properties;
    this.auth = auth;
  }

  // 先校验资料版本与当前微信身份，再检查昵称/图片，最后用带 revision 条件的 SQL 保存，防止覆盖并发修改。
  public MeResponse update(
      Long userId, String nickname, String wxCode, long revision, byte[] avatar) {
    if (revision < 0
        || nickname == null
        || nickname.length() > 32
        || nickname.codePoints().anyMatch(Character::isISOControl)) {
      throw new BizException(422, "VALIDATION_FAILED", "昵称最多32个字符，不能包含控制字符");
    }
    nickname = nickname.trim();
    AppUser user = users.selectById(userId);
    if (user == null || !"active".equals(user.getAccountStatus()))
      throw new BizException(401, "AUTH_EXPIRED", "请重新登录");
    if (user.getProfileRevision() != revision) throw conflict();
    if (wxCode == null || wxCode.trim().isEmpty() || wxCode.length() > 256)
      throw new BizException(422, "VALIDATION_FAILED", "缺少微信身份确认凭证");
    // fresh code只在本次请求用于匹配当前身份与内容检查，不增加OpenID明文持久化。
    String openId = wechat.exchangeOpenId(wxCode);
    if (!user.getSubjectHash().equals(DigestUtils.sha256(properties.getAppId() + ":" + openId))) {
      throw new BizException(403, "IDENTITY_MISMATCH", "微信账号已变化，请退出后重新登录");
    }
    byte[] jpeg = avatar == null ? null : AvatarImage.normalize(avatar);
    if (!nickname.isEmpty()) wechat.checkNickname(openId, nickname);
    // 头像只供本人读取，入口使用微信chooseAvatar的原生检查。
    // 服务端校验并重编码文件，不声称它能替代内容审核；将来开放他人可见头像前须接mediaCheckAsync及回调状态。
    // 更新成功应影响恰好一行；零行通常表示其他请求已更新 revision，返回 409 让前端重新加载。
    if (users.updateProfile(
            userId, nickname.isEmpty() ? null : nickname, jpeg != null, jpeg, revision)
        != 1) throw conflict();
    return auth.me(userId);
  }

  // 清空可选昵称和头像，不注销登录账号；仍通过 revision 拒绝覆盖其他请求的新资料。
  public MeResponse clear(Long userId, long revision) {
    if (revision < 0) throw new BizException(422, "VALIDATION_FAILED", "资料版本无效");
    if (users.updateProfile(userId, null, true, null, revision) != 1) throw conflict();
    return auth.me(userId);
  }

  // 读取当前登录账号的私有头像；调用者身份来自 JWT，不能用任意用户 ID 下载他人头像。
  public byte[] avatar(Long userId) {
    java.util.Map<String, Object> row = users.avatar(userId);
    byte[] image = row == null ? null : (byte[]) row.get("avatar_jpeg");
    if (image == null) throw new BizException(404, "AVATAR_NOT_FOUND", "尚未设置头像");
    return image;
  }

  private BizException conflict() {
    return new BizException(409, "PROFILE_CONFLICT", "资料已在其他请求中更新，请重新加载后再保存");
  }
}
