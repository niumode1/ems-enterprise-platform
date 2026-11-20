package com.company.ems.approval;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    @Data
    public static class SubmitRequest {
        @NotBlank
        private String policyCode;
        private String entityType;
        private Long entityId;
        private Map<String, Object> payload;
        private String reason;
    }

    @Data
    public static class ActionRequest {
        private String comment;
    }

    public record ApprovalView(
            Long id,
            String policyCode,
            String policyName,
            String entityType,
            Long entityId,
            Map<String, Object> payload,
            String reason,
            String status,
            Long requesterId,
            String requesterName,
            int currentApprovals,
            int requiredApprovals,
            String myAction,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean conflict,
            List<Long> conflictingRequestIds,
            List<String> staleFields,
            List<ActionView> actions
    ) {
    }

    public record ActionView(Long id,
                             Long approverId,
                             String approverName,
                             String action,
                             String comment,
                             LocalDateTime createdAt) {
    }

    public record UpdateResult(String status, Long approvalId, String message) {
    }

    public record ReminderResult(int checked, int reminded, int expired) {
    }
}
