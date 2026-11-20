package com.company.ems.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskDtos {

    private TaskDtos() {
    }

    @Data
    public static class TaskRequest {
        @NotBlank
        @Size(max = 200)
        private String title;
        private String description;
        private String status;
        private String priority;
        private Long assigneeEmployeeId;
        private LocalDate startDate;
        private LocalDate dueDate;
        @Min(0)
        @Max(100)
        private Integer progress;
        private List<Long> participantEmployeeIds;
        private List<Long> dependsOnTaskIds;
        private Integer sort;
    }

    @Data
    public static class TaskStatusRequest {
        @NotBlank
        private String status;
        @Min(0)
        @Max(100)
        private Integer progress;
    }

    @Data
    public static class TaskCommentRequest {
        @NotBlank
        @Size(max = 2000)
        private String content;
    }

    public record ParticipantView(Long employeeId, String name, String employeeNo) {
    }

    public record TaskRef(Long id, String title, String status) {
    }

    public record TaskView(Long id,
                           Long projectId,
                           String title,
                           String description,
                           String status,
                           String priority,
                           Long assigneeEmployeeId,
                           String assigneeName,
                           Long creatorEmployeeId,
                           String creatorName,
                           LocalDate startDate,
                           LocalDate dueDate,
                           int progress,
                           int sort,
                           LocalDateTime completedAt,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt,
                           List<ParticipantView> participants,
                           List<TaskRef> dependencies,
                           long commentCount,
                           boolean canEdit,
                           boolean canDelete) {
    }

    public record CommentView(Long id,
                              Long taskId,
                              Long authorEmployeeId,
                              String authorName,
                              String content,
                              LocalDateTime createdAt) {
    }

    @Data
    public static class MilestoneRequest {
        @NotBlank
        @Size(max = 200)
        private String title;
        private String description;
        private LocalDate dueDate;
        private String status;
    }

    public record MilestoneView(Long id,
                                Long projectId,
                                String title,
                                String description,
                                LocalDate dueDate,
                                String status,
                                LocalDateTime reachedAt,
                                LocalDateTime createdAt) {
    }

    @Data
    public static class ProgressRequest {
        @NotNull
        @Min(0)
        @Max(100)
        private Integer progress;
        @Size(max = 500)
        private String note;
    }

    public record ProgressView(Long projectId,
                               int progress,
                               String mode,
                               long taskTotal,
                               long taskDone,
                               LocalDateTime updatedAt) {
    }

    public record ProgressSnapshotView(Long id,
                                       Long projectId,
                                       int progress,
                                       int taskTotal,
                                       int taskDone,
                                       String note,
                                       String createdByName,
                                       LocalDateTime createdAt) {
    }
}
