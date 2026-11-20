package com.company.ems.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank
        private String employeeNo;
        @NotBlank
        private String name;
        private String gender;
        private LocalDate birthDate;
        private String phone;
        private String email;
        private String idCard;
        private String address;
        private String emergencyContact;
        private LocalDate hireDate;
        private Long primaryOrgId;
        private Long primaryPositionId;
        private Long managerId;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private String gender;
        private LocalDate birthDate;
        private String phone;
        private String email;
        private String idCard;
        private String address;
        private String emergencyContact;
        private LocalDate hireDate;
        private Long primaryOrgId;
        private Long primaryPositionId;
        private Long managerId;
        private String reason;
    }

    public record EmployeeView(
            Long id,
            String employeeNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phone,
            String email,
            String idCard,
            String address,
            String emergencyContact,
            LocalDate hireDate,
            String status,
            Long orgId,
            String orgName,
            Long positionId,
            String positionName,
            Long managerId,
            String managerName,
            Set<String> maskedFields
    ) {
    }

    public record UpdateResult(String status, Long approvalId, String message, EmployeeView employee) {
    }

    public record VisibilityView(String fieldCode, String label, boolean visible, boolean canHide) {
    }

    @Data
    public static class VisibilityUpdateRequest {
        @NotBlank
        private String fieldCode;
        @NotNull
        private Boolean visible;
    }
}
