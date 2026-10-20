package com.chunyu.pregnancy.service.impl;

import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.mapper.AdminUserReadMapper;
import com.chunyu.pregnancy.service.AdminUserReadService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 内部只读服务，不提供账号封禁或健康数据访问。北京时间统计边界显式转换为UTC。 */
@Service
public class AdminUserReadServiceImpl implements AdminUserReadService {
  private final AdminUserReadMapper data;
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public AdminUserReadServiceImpl(AdminUserReadMapper data) {
    this.data = data;
  }

  public Map<String, Object> list(
      String q, String state, Long before, int limit, String start, String end) {
    if (limit < 1
        || limit > 100
        || before != null && before < 1
        || q != null && q.length() > 100
        || state != null
            && !state.isEmpty()
            && !Arrays.asList("active", "disabled").contains(state))
      throw new BizException(422, "QUERY_INVALID", "查询条件无效");
    Instant from = null, until = null;
    try {
      LocalDate s = start == null || start.isEmpty() ? null : LocalDate.parse(start),
          e = end == null || end.isEmpty() ? null : LocalDate.parse(end);
      if (s != null && e != null && s.isAfter(e)) throw new IllegalArgumentException();
      from = s == null ? null : s.atStartOfDay(ZONE).toInstant();
      until = e == null ? null : e.plusDays(1).atStartOfDay(ZONE).toInstant();
    } catch (Exception e) {
      throw new BizException(422, "DATE_RANGE_INVALID", "日期范围无效");
    }
    List<Map<String, Object>> rows = data.list(q, state, before, limit, from, until);
    boolean more = rows.size() > limit;
    if (more) rows = new ArrayList<>(rows.subList(0, limit));
    Long next = more ? ((Number) rows.get(rows.size() - 1).get("cursorId")).longValue() : null;
    rows.forEach(r -> r.remove("cursorId"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("records", rows);
    out.put("nextBeforeId", next);
    return out;
  }

  public Map<String, Object> detail(String uuid) {
    List<Map<String, Object>> rows = data.detail(uuid);
    if (rows.isEmpty()) throw new BizException(404, "RESOURCE_NOT_FOUND", "用户不存在");
    rows.get(0).remove("cursorId");
    return rows.get(0);
  }

  public Map<String, Object> stats(int days) {
    if (days < 1 || days > 30) throw new BizException(422, "QUERY_INVALID", "统计范围为1–30天");
    LocalDate today = LocalDate.now(ZONE), start = today.minusDays(days - 1);
    Map<String, Long> counts = new HashMap<>();
    for (Map<String, Object> r :
        data.created(
            start.atStartOfDay(ZONE).toInstant(), today.plusDays(1).atStartOfDay(ZONE).toInstant()))
      counts.put(r.get("day").toString(), ((Number) r.get("count")).longValue());
    List<Map<String, Object>> trend = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      String day = start.plusDays(i).toString();
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("date", day);
      point.put("count", counts.getOrDefault(day, 0L));
      trend.add(point);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", data.count());
    out.put("todayNew", counts.getOrDefault(today.toString(), 0L));
    out.put("trend", trend);
    out.put("timezone", "Asia/Shanghai");
    out.put("updatedAt", Instant.now());
    return out;
  }
}
