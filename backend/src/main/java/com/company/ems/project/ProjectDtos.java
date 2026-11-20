package com.company.ems.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    @Data
    public static class ProjectRequest {
        @NotBlank
        private String name;
        private String code;
        private String description;
        private Long orgId;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer retentionDays;
        private String downloadPolicy;
    }

    @Data
    public static class MemberRequest {
        @NotNull
        private Long employeeId;
        @NotBlank
        private String memberRole;
    }

    @Data
    public static class FolderRequest {
        @NotBlank
        private String name;
        private Long parentId;
    }

    @Data
    public static class DocumentPermissionRequest {
        @NotNull
        private Long employeeId;
        private Boolean canView;
        private Boolean canDownload;
        private Boolean canPrint;
        private Boolean canDelete;
    }

    public record ProjectView(Long id,
                              String name,
                              String code,
                              String description,
                              Long ownerId,
                              String ownerName,
                              Long orgId,
                              String status,
                              LocalDate startDate,
                              LocalDate endDate,
                              Integer retentionDays,
                              String downloadPolicy,
                              Integer progress,
                              String progressMode,
                              String myRole,
                              long memberCount,
                              long documentCount,
                              LocalDateTime createdAt) {
    }

    public record MemberView(Long id,
                             Long projectId,
                             Long employeeId,
                             String employeeName,
                             String employeeNo,
                             String memberRole) {
    }

    public record FolderView(Long id, Long projectId, Long parentId, String name) {
    }

    public record DocumentView(Long id,
                               Long projectId,
                               Long folderId,
                               String name,
                               String originalName,
                               long sizeBytes,
                               String contentType,
                               Long uploaderId,
                               String uploaderName,
                               int currentVersion,
                               LocalDateTime createdAt,
                               boolean canDownload,
                               boolean canPrint,
                               boolean canDelete) {
    }

    public record DocumentVersionView(Long id,
                                      Long documentId,
                                      int versionNo,
                                      String name,
                                      String originalName,
                                      long sizeBytes,
                                      String contentType,
                                      Long uploaderId,
                                      String uploaderName,
                                      String note,
                                      String status,
                                      LocalDateTime createdAt) {
    }

    public record DocumentSearchResult(Long documentId,
                                       Long projectId,
                                       String projectName,
                                       Long folderId,
                                       String name,
                                       String originalName,
                                       String contentType,
                                       long sizeBytes,
                                       int currentVersion,
                                       Long uploaderId,
                                       String uploaderName,
                                       LocalDateTime updatedAt,
                                       boolean canDownload,
                                       String snippet) {
    }

    public record DocumentPrint(Long id,
                                String name,
                                String originalName,
                                String contentType,
                                boolean previewable,
                                String content,
                                boolean watermarkEnabled,
                                String watermarkText) {
    }

    public record DocumentPermissionView(Long id,
                                         Long documentId,
                                         Long employeeId,
                                         String employeeName,
                                         boolean canView,
                                         boolean canDownload,
                                         boolean canPrint,
                                         boolean canDelete) {
    }
}
