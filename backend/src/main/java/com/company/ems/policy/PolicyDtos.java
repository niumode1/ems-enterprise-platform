package com.company.ems.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record PolicyView(Long id,
                             String title,
                             String category,
                             String originalName,
                             String contentType,
                             Long sizeBytes,
                             Long uploaderId,
                             String uploaderName,
                             boolean searchable,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
    }

    public record PolicyDetail(Long id,
                               String title,
                               String category,
                               String originalName,
                               String contentType,
                               Long sizeBytes,
                               Long uploaderId,
                               String uploaderName,
                               boolean searchable,
                               String contentText,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
    }

    public record PolicySearchResult(Long id,
                                     String title,
                                     String category,
                                     String snippet,
                                     LocalDateTime updatedAt) {
    }

    public record PolicyCapabilities(int maxFileMb,
                                     List<String> allowedExtensions,
                                     boolean aiEnabled,
                                     boolean searchEnabled,
                                     boolean canManage,
                                     boolean canAskAi) {
    }

    @Data
    public static class PolicyAskRequest {
        @NotBlank
        @Size(max = 500)
        private String question;
        private Long policyId;
    }
}
