package com.im.group.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.group.dao.entity.TenantConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository("groupTenantConfigMapper")
public interface TenantConfigMapper extends BaseMapper<TenantConfigEntity> {
}
