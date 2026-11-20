package com.company.ems.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT r.*
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND ur.status = 'ACTIVE'
              AND (ur.start_date IS NULL OR ur.start_date <= CURRENT_DATE)
              AND (ur.end_date IS NULL OR ur.end_date >= CURRENT_DATE)
            """)
    List<SysRole> selectByUserId(Long userId);

    @Select("SELECT * FROM sys_role WHERE code = #{code} LIMIT 1")
    SysRole selectByCode(String code);
}
