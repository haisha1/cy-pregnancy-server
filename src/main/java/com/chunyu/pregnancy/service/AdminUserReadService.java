package com.chunyu.pregnancy.service;

import java.util.Map;

/** 用户侧向管理服务提供的只读业务契约：控制可查询的匿名账号字段和统计范围，不读取健康档案。 */
public interface AdminUserReadService {
  Map<String, Object> list(
      String q, String state, Long before, int limit, String start, String end);

  Map<String, Object> detail(String uuid);

  Map<String, Object> stats(int days);
}
