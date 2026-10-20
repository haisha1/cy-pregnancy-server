package com.chunyu.pregnancy.mapper;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 内部管理查询严格投影最小账号字段，禁止SELECT *带出微信索引和刷新凭据。 */
@Repository
public class AdminUserReadMapper {
  private final JdbcTemplate db;

  public AdminUserReadMapper(DataSource source) {
    db = new JdbcTemplate(source);
    db.setQueryTimeout(3);
  }

  private final String select =
      "SELECT u.id cursorId,u.public_uuid uuid,u.account_status state,u.created_at createdAt,(SELECT MAX(s.created_at) FROM auth_session s WHERE s.user_id=u.id) lastLoginAt,(SELECT COUNT(*) FROM auth_session s WHERE s.user_id=u.id AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6)) activeSessions FROM app_user u ";

  public List<Map<String, Object>> list(
      String q, String state, Long before, int limit, Instant start, Instant end) {
    StringBuilder sql = new StringBuilder(select + "WHERE 1=1");
    List<Object> p = new ArrayList<>();
    if (q != null && !q.trim().isEmpty()) {
      sql.append(" AND CONVERT(u.public_uuid USING utf8mb4) LIKE ? ESCAPE '!'");
      p.add("%" + q.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
    if (state != null && !state.isEmpty()) {
      sql.append(" AND u.account_status=?");
      p.add(state);
    }
    if (before != null) {
      sql.append(" AND u.id<?");
      p.add(before);
    }
    if (start != null) {
      sql.append(" AND u.created_at>=?");
      p.add(Timestamp.from(start));
    }
    if (end != null) {
      sql.append(" AND u.created_at<?");
      p.add(Timestamp.from(end));
    }
    sql.append(" ORDER BY u.id DESC LIMIT ?");
    p.add(limit + 1);
    return normalize(db.queryForList(sql.toString(), p.toArray()));
  }

  public List<Map<String, Object>> detail(String uuid) {
    return normalize(db.queryForList(select + "WHERE u.public_uuid=?", uuid));
  }

  public long count() {
    return db.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
  }

  public List<Map<String, Object>> created(Instant from, Instant until) {
    return db.queryForList(
        "SELECT DATE(DATE_ADD(created_at,INTERVAL 8 HOUR)) day,COUNT(*) count FROM app_user WHERE created_at>=? AND created_at<? GROUP BY day ORDER BY day",
        Timestamp.from(from),
        Timestamp.from(until));
  }

  private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
    for (Map<String, Object> r : rows)
      for (Map.Entry<String, Object> e : r.entrySet()) {
        if (e.getValue() instanceof Timestamp) e.setValue(((Timestamp) e.getValue()).toInstant());
        else if (e.getValue() instanceof LocalDateTime)
          e.setValue(((LocalDateTime) e.getValue()).toInstant(ZoneOffset.UTC));
      }
    return rows;
  }
}
