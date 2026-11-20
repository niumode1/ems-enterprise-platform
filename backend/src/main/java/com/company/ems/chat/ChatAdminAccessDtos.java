package com.company.ems.chat;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatAdminAccessDtos {

    private ChatAdminAccessDtos() {
    }

    @Data
    public static class Request {
        private Long conversationId;
        private String reason;
        private Integer durationMinutes;
    }

    public record GrantView(Long id,
                            Long conversationId,
                            String conversationName,
                            Long requesterId,
                            String requesterName,
                            String reason,
                            String status,
                            LocalDateTime startsAt,
                            LocalDateTime expiresAt,
                            Long approvalId,
                            LocalDateTime createdAt,
                            boolean active) {
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
                             List<GrantView> grants) {
    }
}
