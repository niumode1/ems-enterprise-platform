package com.company.ems.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT DISTINCT p.*
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND ur.status = 'ACTIVE'
              AND (ur.start_date IS NULL OR ur.start_date <= CURRENT_DATE)
              AND (ur.end_date IS NULL OR ur.end_date >= CURRENT_DATE)
            """)
    List<SysPermission> selectByUserId(Long userId);
}
