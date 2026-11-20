package com.company.ems.system;

import com.company.ems.common.ApiResponse;
import com.company.ems.system.entity.SysPermission;
import com.company.ems.system.entity.SysRole;
import com.company.ems.system.mapper.SysPermissionMapper;
import com.company.ems.system.mapper.SysRoleMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    public RoleController(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public ApiResponse<List<SysRole>> roles() {
        return ApiResponse.ok(roleMapper.selectList(null));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('permission:read')")
    public ApiResponse<List<SysPermission>> permissions() {
        return ApiResponse.ok(permissionMapper.selectList(null));
    }
}
