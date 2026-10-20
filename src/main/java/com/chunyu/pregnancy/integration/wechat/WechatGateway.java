package com.chunyu.pregnancy.integration.wechat;

/** 微信边界接口，单元测试可替换它；生产实现只能用微信真实响应确定身份。 */
public interface WechatGateway {
  String exchangeOpenId(String code);
  /** 检查不通过或微信不可用都不保存；不信任客户端传来的“已审核”标记。 */
  void checkNickname(String openId, String nickname);
}
