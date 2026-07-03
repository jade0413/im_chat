package com.im.file.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.file.dao.entity.FileTranscodeJobEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileTranscodeJobMapper extends BaseMapper<FileTranscodeJobEntity> {

  @Insert("""
      INSERT IGNORE INTO file_transcode_job (
        id, tenant_id, file_id, source_object_key, source_mime, target_profile,
        status, retry_count, created_at, updated_at
      ) VALUES (
        #{job.id}, #{job.tenantId}, #{job.fileId}, #{job.sourceObjectKey}, #{job.sourceMime},
        #{job.targetProfile}, #{job.status}, #{job.retryCount}, #{job.createdAt}, #{job.updatedAt}
      )
      """)
  int insertIgnore(@Param("job") FileTranscodeJobEntity job);

  @Select("""
      SELECT *
      FROM file_transcode_job
      WHERE retry_count < #{maxAttempts}
        AND (
          status = #{pendingStatus}
          OR (status = #{failedStatus} AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
          OR (status = #{processingStatus} AND claim_until <= #{now})
        )
      ORDER BY id ASC
      LIMIT 1
      """)
  FileTranscodeJobEntity selectNextDue(@Param("now") LocalDateTime now,
      @Param("pendingStatus") int pendingStatus,
      @Param("failedStatus") int failedStatus,
      @Param("processingStatus") int processingStatus,
      @Param("maxAttempts") int maxAttempts);

  @Update("""
      UPDATE file_transcode_job
      SET status = #{processingStatus},
          claim_owner = #{claimOwner},
          claim_until = #{claimUntil},
          updated_at = #{now}
      WHERE id = #{id}
        AND retry_count < #{maxAttempts}
        AND (
          status = #{pendingStatus}
          OR (status = #{failedStatus} AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
          OR (status = #{processingStatus} AND claim_until <= #{now})
        )
      """)
  int claim(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("claimUntil") LocalDateTime claimUntil,
      @Param("now") LocalDateTime now,
      @Param("processingStatus") int processingStatus,
      @Param("pendingStatus") int pendingStatus,
      @Param("failedStatus") int failedStatus,
      @Param("maxAttempts") int maxAttempts);

  @Update("""
      UPDATE file_transcode_job
      SET status = #{succeededStatus},
          target_object_key = #{targetObjectKey},
          error_msg = NULL,
          claim_owner = NULL,
          claim_until = NULL,
          updated_at = #{now}
      WHERE id = #{id}
        AND status = #{processingStatus}
        AND claim_owner = #{claimOwner}
      """)
  int markSucceeded(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("targetObjectKey") String targetObjectKey,
      @Param("now") LocalDateTime now,
      @Param("succeededStatus") int succeededStatus,
      @Param("processingStatus") int processingStatus);

  @Update("""
      UPDATE file_transcode_job
      SET status = #{skippedStatus},
          error_msg = #{reason},
          claim_owner = NULL,
          claim_until = NULL,
          updated_at = #{now}
      WHERE id = #{id}
        AND status = #{processingStatus}
        AND claim_owner = #{claimOwner}
      """)
  int markSkipped(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now,
      @Param("skippedStatus") int skippedStatus,
      @Param("processingStatus") int processingStatus);

  @Update("""
      UPDATE file_transcode_job
      SET status = #{failedStatus},
          error_msg = #{errorMsg},
          retry_count = retry_count + 1,
          next_retry_at = #{nextRetryAt},
          claim_owner = NULL,
          claim_until = NULL,
          updated_at = #{now}
      WHERE id = #{id}
        AND status = #{processingStatus}
        AND claim_owner = #{claimOwner}
      """)
  int markFailed(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("errorMsg") String errorMsg,
      @Param("nextRetryAt") LocalDateTime nextRetryAt,
      @Param("now") LocalDateTime now,
      @Param("failedStatus") int failedStatus,
      @Param("processingStatus") int processingStatus);

  @Select("""
      SELECT target_object_key
      FROM file_transcode_job
      WHERE tenant_id = #{tenantId}
        AND file_id = #{fileId}
        AND target_profile = #{targetProfile}
        AND status = #{succeededStatus}
        AND target_object_key IS NOT NULL
        AND target_object_key <> ''
      LIMIT 1
      """)
  String selectSucceededTargetObjectKey(@Param("tenantId") long tenantId,
      @Param("fileId") long fileId,
      @Param("targetProfile") String targetProfile,
      @Param("succeededStatus") int succeededStatus);
}
