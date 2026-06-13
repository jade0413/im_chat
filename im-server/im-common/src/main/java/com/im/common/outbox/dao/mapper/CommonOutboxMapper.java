package com.im.common.outbox.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.common.outbox.dao.entity.OutboxEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommonOutboxMapper extends BaseMapper<OutboxEntity> {

  @Select("""
      SELECT *
      FROM outbox
      WHERE retry_count < #{maxRetries}
        AND (
          status IN (0, 1)
          OR (status = 3 AND claim_until < #{now})
        )
      ORDER BY created_at ASC
      LIMIT #{limit}
      """)
  List<OutboxEntity> selectClaimCandidates(@Param("now") LocalDateTime now,
      @Param("maxRetries") int maxRetries,
      @Param("limit") int limit);

  @Update("""
      UPDATE outbox
      SET status = #{processingStatus},
          claim_owner = #{claimOwner},
          claim_until = #{claimUntil}
      WHERE id = #{id}
        AND retry_count < #{maxRetries}
        AND (
          status IN (#{pendingStatus}, #{failedStatus})
          OR (status = #{processingStatus} AND claim_until < #{now})
        )
      """)
  int claim(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("claimUntil") LocalDateTime claimUntil,
      @Param("now") LocalDateTime now,
      @Param("maxRetries") int maxRetries,
      @Param("pendingStatus") int pendingStatus,
      @Param("failedStatus") int failedStatus,
      @Param("processingStatus") int processingStatus);

  @Update("""
      UPDATE outbox
      SET status = #{status},
          retry_count = #{retryCount},
          claim_owner = NULL,
          claim_until = NULL
      WHERE id = #{id}
        AND status = #{processingStatus}
        AND claim_owner = #{claimOwner}
      """)
  int releaseClaim(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("status") int status,
      @Param("retryCount") int retryCount,
      @Param("processingStatus") int processingStatus);

  @Delete("""
      DELETE FROM outbox
      WHERE id = #{id}
        AND status = #{processingStatus}
        AND claim_owner = #{claimOwner}
      """)
  int deleteClaimed(@Param("id") long id,
      @Param("claimOwner") String claimOwner,
      @Param("processingStatus") int processingStatus);
}
