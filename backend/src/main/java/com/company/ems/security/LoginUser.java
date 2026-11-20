package com.company.ems.security;

import java.util.Set;

public record LoginUser(
        Long userId,
        String username,
        String displayName,
        Long employeeId,
        Set<String> roles,
        Set<String> permissions
) {
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
