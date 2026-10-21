package com.chunyu.pregnancy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chunyu.pregnancy.entity.AppUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis-Plus负责普通CRUD；并发创建使用MySQL唯一键和upsert，避免重复用户。 */
public interface AppUserMapper extends BaseMapper<AppUser> {
  // 声明插入 SQL；参数通过 MyBatis 绑定，事务由调用它的业务层控制。
  @Insert(
      "INSERT INTO app_user(app_id,subject_hash,public_uuid,account_status,cloud_data_epoch,created_at) "
          + "VALUES(#{appId},#{subjectHash},#{publicUuid},'active',1,UTC_TIMESTAMP(6)) "
          + "ON DUPLICATE KEY UPDATE id=id")
  void insertIdentity(
      // 为 SQL 参数命名，使注解 SQL 中的 #{名称} 能找到对应方法参数。
      @Param("appId") String appId,
      @Param("subjectHash") String subjectHash,
      @Param("publicUuid") String publicUuid);

  default void ensureUser(String appId, String subjectHash) {
    insertIdentity(appId, subjectHash, java.util.UUID.randomUUID().toString());
  }

  // 声明查询 SQL；#{参数} 使用参数绑定，不把外部输入直接拼成 SQL 结构。
  @Select("SELECT * FROM app_user WHERE app_id=#{appId} AND subject_hash=#{subjectHash}")
  AppUser findIdentity(@Param("appId") String appId, @Param("subjectHash") String subjectHash);

  @Select("SELECT avatar_jpeg IS NOT NULL FROM app_user WHERE id=#{id}")
  boolean hasAvatar(@Param("id") Long id);

  @Select("SELECT avatar_jpeg FROM app_user WHERE id=#{id}")
  java.util.Map<String, Object> avatar(@Param("id") Long id);

  /** 图片和昵称一起提交；有并发修改时保留原资料，由客户端明确重新读取。 */
  @org.apache.ibatis.annotations.Update(
      "UPDATE app_user SET nickname=#{nickname}, "
          + "avatar_jpeg=CASE WHEN #{replaceAvatar} THEN #{avatar,jdbcType=LONGVARBINARY} ELSE avatar_jpeg END, "
          + "profile_revision=profile_revision+1 WHERE id=#{id} AND account_status='active' AND profile_revision=#{revision}")
  int updateProfile(
      @Param("id") Long id,
      @Param("nickname") String nickname,
      @Param("replaceAvatar") boolean replaceAvatar,
      @Param("avatar") byte[] avatar,
      @Param("revision") long revision);
}
