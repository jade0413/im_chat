package com.im.cs.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.cs.agent.dao.entity.CsInternalNoteEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CsInternalNoteMapper extends BaseMapper<CsInternalNoteEntity> {

  @Select("""
      SELECT *
      FROM cs_internal_note
      WHERE tenant_id = #{tenantId}
        AND conv_id = #{convId}
      ORDER BY created_at ASC, id ASC
      LIMIT #{limit}
      """)
  List<CsInternalNoteEntity> listByConversation(@Param("tenantId") long tenantId,
      @Param("convId") long convId,
      @Param("limit") int limit);
}
