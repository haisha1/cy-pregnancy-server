package com.chunyu.pregnancy.service;

import java.util.Map;

public interface AdminUserReadService {
  Map<String, Object> list(
      String q, String state, Long before, int limit, String start, String end);

  Map<String, Object> detail(String uuid);

  Map<String, Object> stats(int days);
}
