package com.chunyu.pregnancy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 只保存微信身份的散列索引，不把 openid、session_key 返回小程序。 实体只用于持久化，控制器返回独立DTO。 */
// Lombok 在编译期生成 getter；IDE 需要启用注解处理，运行时不靠反射生成方法。
@Getter
// Lombok 在编译期生成 setter，减少字段赋值方法的重复代码。
@Setter
// 指定实体对应的数据库表名；这是映射声明，不负责自动创建表。
@TableName("app_user")
public class AppUser {
  // 标记数据库主键及生成策略；AUTO 表示使用数据库自增主键。
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
