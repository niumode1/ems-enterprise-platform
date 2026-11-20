package com.company.ems.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public final class AiDocumentDtos {

    private AiDocumentDtos() {
    }

    @Data
    public static class DocAskRequest {
        @NotNull
        private Long documentId;
        @NotBlank
        @Size(max = 500)
        private String question;
    }

    public record DocCitation(Long documentId,
                              String documentName,
                              int versionNo,
                              int chunkIndex,
                              String snippet) {
    }

    public record DocAnswer(String documentName,
                            int versionNo,
                            String answer,
                            List<DocCitation> citations,
                            double confidence,
                            boolean lowConfidence,
                            boolean aiUsed,
                            boolean citationsRequired) {
    }

    public record PolicySource(Long policyId, String title, String content) {
    }

    public record PolicyCitation(Long policyId,
                                 String title,
                                 int chunkIndex,
                                 String snippet) {
    }

    public record PolicyAnswer(String answer,
                               List<PolicyCitation> citations,
                               double confidence,
                               boolean lowConfidence,
                               boolean aiUsed,
                               boolean citationsRequired) {
    }

    @Data
    public static class DocTaskExtractRequest {
        @Min(1)
        @Max(50)
        private Integer max;
    }

    public record TaskSuggestion(String title,
                                 String description,
                                 LocalDate dueDate,
                                 String priority,
                                 String snippet,
                                 double confidence,
                                 boolean lowConfidence) {
    }

    public record DocTaskExtractResult(Long documentId,
                                       String documentName,
                                       int versionNo,
                                       List<TaskSuggestion> tasks,
                                       boolean aiUsed,
                                       String note) {
    }

    @Data
    public static class SuggestedTask {
        @NotBlank
        @Size(max = 200)
        private String title;
        @Size(max = 2000)
        private String description;
        private LocalDate dueDate;
        private String priority;
    }

    @Data
    public static class DocTaskApplyRequest {
        private Long assigneeEmployeeId;
        @NotEmpty
        @Size(max = 50)
        @Valid
        private List<SuggestedTask> tasks;
    }
}
