package com.company.ems.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank
        private String type;
        private String name;
        private List<Long> employeeIds;
        private Boolean burnAfterRead;
        private Boolean antiRecall;
        private Integer retentionDays;
    }

    @Data
    public static class SettingsRequest {
        private String name;
        private Boolean burnAfterRead;
        private Boolean antiRecall;
        private Integer retentionDays;
    }

    @Data
    public static class AddMemberRequest {
        @NotNull
        private Long employeeId;
    }

    @Data
    public static class SendRequest {
        private String content;
        private Long attachmentId;
    }

    public record AttachmentView(Long id,
                                 Long conversationId,
                                 Long messageId,
                                 String kind,
                                 String originalName,
                                 String contentType,
                                 long sizeBytes,
                                 LocalDateTime createdAt) {
    }

    public record AttachmentCapabilities(boolean enabled,
                                         boolean directEnabled,
                                         boolean groupEnabled,
                                         boolean burnAfterReadEnabled,
                                         int maxSizeMb,
                                         int imageMaxSizeMb,
                                         List<String> allowedExtensions,
                                         List<String> imageExtensions) {
    }

    public record ConversationView(Long id,
                                   String type,
                                   String name,
                                   Long ownerId,
                                   String ownerName,
                                   int memberCount,
                                   boolean burnAfterRead,
                                   boolean antiRecall,
                                   Integer retentionDays,
                                   String lastMessagePreview,
                                   LocalDateTime lastMessageAt,
                                   long unreadCount,
                                   String myRole,
                                   LocalDateTime createdAt,
                                   boolean canManageSettings,
                                   boolean canManageMembers,
                                   boolean canLeave,
                                   boolean attachmentUploadAllowed,
                                   int attachmentMaxSizeMb,
                                   int attachmentImageMaxSizeMb) {
    }

    public record MemberView(Long employeeId,
                             String employeeName,
                             String role,
                             String status,
                             LocalDateTime mutedUntil,
                             LocalDateTime lastReadAt,
                             LocalDateTime joinedAt) {
    }

    public record ConversationDetailView(ConversationView conversation,
                                         List<MemberView> members) {
    }

    public record MessageView(Long id,
                              Long conversationId,
                              Long senderId,
                              String senderName,
                              String type,
                              String content,
                              boolean recalled,
                              boolean burnAfterRead,
                              boolean burnedForMe,
                              boolean mine,
                              long readCount,
                              LocalDateTime createdAt,
                              AttachmentView attachment) {
    }
}
