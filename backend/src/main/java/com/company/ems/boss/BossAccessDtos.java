package com.company.ems.boss;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class BossAccessDtos {

    private BossAccessDtos() {
    }

    @Data
    public static class Request {
        private Long employeeId;
        @Size(max = 500)
        private String reason;
        @Min(1)
        private Integer durationMinutes;
    }

    public record GrantView(Long id,
                            Long userId,
                            String userName,
                            Long employeeId,
                            String employeeName,
                            String reason,
                            Long approvalId,
                            String status,
                            boolean active,
                            LocalDateTime startsAt,
                            LocalDateTime expiresAt,
                            LocalDateTime revokedAt,
                            LocalDateTime createdAt) {
    }

    public record RequestResult(String status,
                                Long approvalId,
                                Long grantId,
                                String message,
                                GrantView grant) {
    }

    public record StatusView(String mode,
                             boolean enabled,
                             boolean canRequest,
                             boolean requireReason,
                             int approverCount,
                             int defaultDurationMinutes,
                             int maxDurationMinutes,
                             boolean allowAllEmployees,
                             List<GrantView> grants) {
    }
}
