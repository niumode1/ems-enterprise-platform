package com.company.ems.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class RecoveryDtos {

    private RecoveryDtos() {
    }

    @Data
    public static class Request {
        @NotBlank
        private String action;
        private Long userId;
        private String username;
        @Size(max = 500)
        private String reason;
        private String newPassword;
    }

    @Data
    public static class SignRequest {
        @Size(max = 500)
        private String comment;
    }

    public record SignatureView(Long id,
                                Long signerId,
                                String signerName,
                                String signerRole,
                                String comment,
                                LocalDateTime createdAt) {
    }

    public record RequestView(Long id,
                              String action,
                              Long targetUserId,
                              String targetUsername,
                              String targetDisplayName,
                              String reason,
                              String status,
                              Long requesterId,
                              String requesterName,
                              int requiredSigners,
                              int signatureCount,
                              LocalDateTime expiresAt,
                              LocalDateTime appliedAt,
                              LocalDateTime createdAt,
                              List<SignatureView> signatures) {
    }

    public record RequestResult(String status, RequestView request, String message) {
    }

    public record StatusView(boolean enabled,
                             String mode,
                             int requiredSigners,
                             List<String> signerRoles,
                             List<String> allowedActions,
                             boolean deploymentAccountEnabled,
                             boolean deploymentAccount,
                             boolean signer,
                             boolean canRequest) {
    }
}
