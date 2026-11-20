package com.company.ems.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class AiAssistantDtos {

    private AiAssistantDtos() {
    }

    @Data
    public static class AskRequest {
        @NotBlank
        @Size(max = 200)
        private String question;
        private Long employeeId;
    }

    public record HelpHit(String title, String route, String description, String reason) {
    }

    public record PersonCitation(Long employeeId, String name, String employeeNo) {
    }

    public record PeopleAnswer(String answer,
                               List<PersonCitation> citations,
                               List<String> maskedFields,
                               boolean aiUsed) {
    }

    public record AskResult(String feature,
                            String answer,
                            List<HelpHit> locations,
                            List<PersonCitation> citations,
                            List<String> maskedFields,
                            boolean aiUsed) {
    }

    public record QuickQuestion(Long id, String question, String feature) {
    }

    public record QueryLogView(Long id,
                               String feature,
                               String questionSummary,
                               String model,
                               boolean success,
                               long latencyMs,
                               String answerSummary,
                               LocalDateTime createdAt) {
    }

    public record HelpIndexView(Long id,
                                String title,
                                String keywords,
                                String route,
                                String description,
                                String requiredPermission,
                                int sort,
                                boolean enabled) {
    }

    @Data
    public static class HelpIndexRequest {
        @NotBlank
        @Size(max = 128)
        private String title;
        @Size(max = 500)
        private String keywords;
        @NotBlank
        @Size(max = 200)
        private String route;
        @Size(max = 500)
        private String description;
        @Size(max = 64)
        private String requiredPermission;
        private Integer sort;
        private Boolean enabled;
    }
}
