package com.chunyu.pregnancy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前账户的最小公开信息，与包含身份散列的AppUser实体分离。 */
@Getter
@AllArgsConstructor
public class MeResponse {
  private final String userId;
  private final String accountStatus;
  private final long cloudDataEpoch;
  /** 尚未实现云同步授权，绝不能用登录成功代替同意。 */
  private final boolean cloudSyncEnabled;

  private final String nickname;
  private final boolean hasAvatar;
  private final long profileRevision;
}
