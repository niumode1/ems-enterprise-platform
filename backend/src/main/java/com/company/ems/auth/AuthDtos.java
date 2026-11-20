package com.company.ems.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;
    }

    public record UserInfo(
            Long userId,
            String username,
            String displayName,
            Long employeeId,
            Set<String> roles,
            Set<String> permissions
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserInfo user
    ) {
    }
}
