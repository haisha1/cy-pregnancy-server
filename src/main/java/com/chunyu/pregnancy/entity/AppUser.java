package com.chunyu.pregnancy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 只保存微信身份的散列索引，不把 openid、session_key 返回小程序。 实体只用于持久化，控制器返回独立DTO。 */
@Getter
@Setter
@TableName("app_user")
public class AppUser {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String appId;
  private String subjectHash;
  private String accountStatus;
  private Long cloudDataEpoch;
  private Instant createdAt;
  private String publicUuid;
  private String nickname;
  /** 不自动装载头像二进制，避免每次JWT会话校验读取图片。 */
  @com.baomidou.mybatisplus.annotation.TableField(exist = false)
  private byte[] avatarJpeg;

  private Long profileRevision;
}
