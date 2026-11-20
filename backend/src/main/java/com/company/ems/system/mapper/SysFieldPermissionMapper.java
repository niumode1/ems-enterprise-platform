package com.company.ems.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.system.entity.SysFieldPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysFieldPermissionMapper extends BaseMapper<SysFieldPermission> {

    @Select("""
            SELECT id, entity_type, field_code, role_id, scope_type, visibility, mask_rule,
                   self_edit, exportable, ai_readable, is_sensitive, key_field,
                   created_at, updated_at
            FROM sys_field_permission
            WHERE entity_type = #{entityType}
            """)
    List<SysFieldPermission> selectByEntityType(String entityType);
}
