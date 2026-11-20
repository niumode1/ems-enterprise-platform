package com.company.ems.system;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        @NotBlank
        private String displayName;
        private Long employeeId;
        private List<String> roleCodes;
    }

    @Data
    public static class UpdateRequest {
        private String displayName;
        private Long employeeId;
        private String status;
    }

    @Data
    public static class RoleAssignRequest {
        private List<String> roleCodes;
        private List<RoleAssignment> assignments;
    }

    @Data
    public static class RoleAssignment {
        @NotBlank
        private String roleCode;
        private LocalDate startDate;
        private LocalDate endDate;
        private String note;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String password;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        private String newPassword;
    }

    public record RoleView(Long id, String code, String name) {
    }

    public record RoleAssignmentView(
            Long id,
            Long roleId,
            String roleCode,
            String roleName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String note,
            Long grantedBy,
            LocalDateTime revokedAt,
            LocalDateTime createdAt
    ) {
    }

    public record UserView(
            Long id,
            String username,
            String displayName,
            Long employeeId,
            String employeeName,
            String employeeNo,
            String status,
            boolean mfaEnabled,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            List<RoleView> roles
    ) {
    }
}
