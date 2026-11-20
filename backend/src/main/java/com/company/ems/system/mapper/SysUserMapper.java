package com.company.ems.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.ems.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE employee_id = #{employeeId} AND status = 'ACTIVE' LIMIT 1")
    SysUser selectByEmployeeId(Long employeeId);

    @Select("""
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role_permission rp ON rp.role_id = ur.role_id
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE p.code = #{permission} AND u.status = 'ACTIVE'
            """)
    List<Long> selectUserIdsByPermission(String permission);

    @Select("""
            <script>
            SELECT DISTINCT u.id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.status = 'ACTIVE'
              AND ur.status = 'ACTIVE'
              AND (ur.start_date IS NULL OR ur.start_date &lt;= CURRENT_DATE)
              AND (ur.end_date IS NULL OR ur.end_date &gt;= CURRENT_DATE)
              AND r.code IN
              <foreach collection="codes" item="code" open="(" separator="," close=")">#{code}</foreach>
            </script>
            """)
    List<Long> selectUserIdsByRoleCodes(@org.apache.ibatis.annotations.Param("codes") List<String> codes);
}
