package com.im.common.outbox.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.common.outbox.dao.entity.OutboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommonOutboxMapper extends BaseMapper<OutboxEntity> {
}
