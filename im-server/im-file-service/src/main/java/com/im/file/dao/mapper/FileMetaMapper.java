package com.im.file.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.file.dao.entity.FileMetaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileMetaMapper extends BaseMapper<FileMetaEntity> {

  @Select("""
      SELECT *
      FROM file_meta
      WHERE tenant_id = #{tenantId}
        AND object_key = #{objectKey}
      """)
  FileMetaEntity selectByObjectKey(@Param("tenantId") long tenantId,
      @Param("objectKey") String objectKey);

  @Select("""
      SELECT *
      FROM file_meta
      WHERE tenant_id = #{tenantId}
        AND uploader_id = #{uploaderId}
        AND sha256 = #{sha256}
        AND size = #{size}
        AND mime = #{mime}
        AND status = #{status}
      ORDER BY id DESC
      LIMIT 1
      """)
  FileMetaEntity selectConfirmedByUploaderHash(@Param("tenantId") long tenantId,
      @Param("uploaderId") long uploaderId,
      @Param("sha256") String sha256,
      @Param("size") long size,
      @Param("mime") String mime,
      @Param("status") int status);

  @Update("""
      UPDATE file_meta
      SET status = #{toStatus}
      WHERE tenant_id = #{tenantId}
        AND object_key = #{objectKey}
        AND status = #{fromStatus}
      """)
  int updateStatus(@Param("tenantId") long tenantId,
      @Param("objectKey") String objectKey,
      @Param("fromStatus") int fromStatus,
      @Param("toStatus") int toStatus);
}
