package db.migration;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 在Java生成随机公开标识，避免UUID()表达式默认值在开启binlog的MySQL中被拒绝。 */
public class V2__public_account_identifier extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection db = context.getConnection();
    try (Statement statement = db.createStatement()) {
      statement.execute(
          "ALTER TABLE app_user ADD public_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL");
    }
    // 分批读取旧账号，限制迁移内存；不把主键或微信标识作为公开账号标识。
    long cursor = 0;
    while (true) {
      java.util.List<Long> ids = new java.util.ArrayList<>();
      try (PreparedStatement select =
          db.prepareStatement("SELECT id FROM app_user WHERE id>? ORDER BY id LIMIT 500")) {
        select.setLong(1, cursor);
        try (ResultSet rows = select.executeQuery()) {
          while (rows.next()) ids.add(rows.getLong(1));
        }
      }
      if (ids.isEmpty()) break;
      try (PreparedStatement update =
          db.prepareStatement("UPDATE app_user SET public_uuid=? WHERE id=?")) {
        for (Long id : ids) {
          update.setString(1, UUID.randomUUID().toString());
          update.setLong(2, id);
          update.addBatch();
          cursor = id;
        }
        update.executeBatch();
      }
    }
    try (Statement statement = db.createStatement()) {
      statement.execute(
          "ALTER TABLE app_user MODIFY public_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, ADD UNIQUE KEY uk_user_public_uuid(public_uuid), ADD KEY ix_user_created(created_at,id)");
    }
  }
}
