package com.company.ems.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
