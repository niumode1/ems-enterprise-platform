package com.company.ems.security;

import com.company.ems.system.entity.SysPermission;
import com.company.ems.system.entity.SysRole;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysPermissionMapper;
import com.company.ems.system.mapper.SysRoleMapper;
import com.company.ems.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAuthorityService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    public UserAuthorityService(SysUserMapper userMapper,
                                SysRoleMapper roleMapper,
                                SysPermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    public LoginUser loadByUserId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return null;
        }
        Set<String> roles = roleMapper.selectByUserId(userId).stream()
                .map(SysRole::getCode)
                .collect(Collectors.toSet());
        Set<String> permissions = permissionMapper.selectByUserId(userId).stream()
                .map(SysPermission::getCode)
                .collect(Collectors.toSet());
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmployeeId(),
                roles,
                permissions
        );
    }
}
