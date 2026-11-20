package com.company.ems.roster;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

public final class RosterDtos {

    private RosterDtos() {
    }

    @Data
    public static class ReviewRequest {
        private Map<String, Object> values;
        private Boolean confirm;
    }

    @Data
    public static class ConfirmRequest {
        private Map<String, Object> values;
        private String comment;
    }

    @Data
    public static class RejectRequest {
        @NotBlank
        private String reason;
    }

    public record BatchView(Long id,
                            String batchNo,
                            String fileName,
                            long fileSize,
                            String format,
                            String status,
                            int totalRows,
                            int parsedRows,
                            int lowConfidenceRows,
                            int pendingConfirmRows,
                            int confirmedRows,
                            int rejectedRows,
                            int committedRows,
                            boolean aiUsed,
                            String aiModel,
                            String errorMessage,
                            String createdByName,
                            LocalDateTime createdAt,
                            LocalDateTime committedAt) {
    }

    public record RowView(Long id,
                          int rowNo,
                          String batchNo,
                          Map<String, Object> values,
                          Map<String, Object> raw,
                          double confidence,
                          String status,
                          Long matchedEmployeeId,
                          String matchedEmployeeName,
                          Long confirmUserId,
                          String confirmUserName,
                          String rejectReason,
                          String errorMessage,
                          LocalDateTime updatedAt) {
    }

    public record PushResult(int pushed,
                             int skipped,
                             int createdAsNew,
                             BatchView batch) {
    }

    public record CommitResult(String status,
                               Long approvalId,
                               String message,
                               BatchView batch) {
    }
}
