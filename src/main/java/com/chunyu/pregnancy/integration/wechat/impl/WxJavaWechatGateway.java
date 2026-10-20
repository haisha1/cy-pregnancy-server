package com.chunyu.pregnancy.integration.wechat.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.config.WechatProperties;
import com.chunyu.pregnancy.integration.wechat.WechatGateway;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 只取openid用于本系统身份匹配；不缓存或记录code、session_key和第三方错误全文。 */
@Component
public class WxJavaWechatGateway implements WechatGateway {
  private final WxMaService service;
  private final WechatProperties properties;

  @Override
  public void checkNickname(String openId, String nickname) {
    try {
      cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckResponse result =
          service
              .getSecCheckService()
              .checkMessage(
                  cn.binarywang.wx.miniapp.bean.security.WxMaMsgSecCheckCheckRequest.builder()
                      .version("2")
                      .openid(openId)
                      .scene(1)
                      .content(nickname)
                      .nickname(nickname)
                      .build());
      if (result == null
          || result.getResult() == null
          || !"pass".equals(result.getResult().getSuggest())) {
        throw new BizException(422, "PROFILE_REJECTED", "昵称未通过内容检查，请修改后重试");
      }
    } catch (WxErrorException ex) {
      throw moderationError(ex);
    }
  }

  private BizException moderationError(WxErrorException ex) {
    if (ex.getError().getErrorCode() == 87014)
      return new BizException(422, "PROFILE_REJECTED", "资料未通过内容检查，请修改后重试");
    return new BizException(503, "MODERATION_UNAVAILABLE", "微信内容检查暂不可用，资料未保存，请稍后重试");
  }

  public WxJavaWechatGateway(WechatProperties properties) {
    this.properties = properties;
    WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
    config.setAppid(properties.getAppId());
    config.setSecret(properties.getSecret());
    service = new WxMaServiceImpl();
    service.setWxMaConfig(config);
  }

  @Override
  public String exchangeOpenId(String code) {
    if (!properties.isConfigured()) {
      throw new BizException(503, "WECHAT_NOT_CONFIGURED", "微信登录尚未配置");
    }
    try {
      String openId = service.getUserService().getSessionInfo(code).getOpenid();
      if (!StringUtils.hasText(openId)) {
        throw new BizException(502, "WECHAT_RESPONSE_INVALID", "微信登录响应无效");
      }
      return openId;
    } catch (WxErrorException ex) {
      int codeValue = ex.getError().getErrorCode();
      if (codeValue == 40029 || codeValue == 40163 || codeValue == 40226) {
        throw new BizException(401, "WX_CODE_INVALID", "登录凭证无效，请重新发起微信登录");
      }
      throw new BizException(503, "WECHAT_UNAVAILABLE", "微信登录暂不可用，请稍后重试");
    }
  }
}
