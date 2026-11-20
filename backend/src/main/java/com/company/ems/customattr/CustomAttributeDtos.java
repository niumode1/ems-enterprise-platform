package com.company.ems.customattr;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CustomAttributeDtos {

    private CustomAttributeDtos() {
    }

    @Data
    public static class DefRequest {
        private String entityType;
        @NotBlank
        private String fieldCode;
        @NotBlank
        private String name;
        private String fieldType;
        private List<String> options;
        private Boolean timeline;
        private String visibility;
        private String maskRule;
        private Boolean sensitive;
        private Boolean selfEditable;
        private Boolean required;
        private Integer sort;
    }

    @Data
    public static class ValueSetRequest {
        private String entityType;
        private Long entityId;
        private Map<String, String> values;
        private LocalDate effectiveDate;
        private String reason;
    }

    public record DefView(Long id,
                          String entityType,
                          String fieldCode,
                          String name,
                          String fieldType,
                          List<String> options,
                          boolean timeline,
                          String visibility,
                          String maskRule,
                          boolean sensitive,
                          boolean selfEditable,
                          boolean required,
                          int sort,
                          String status) {
    }

    public record ValueView(Long defId,
                            String fieldCode,
                            String name,
                            String fieldType,
                            List<String> options,
                            boolean timeline,
                            String value,
                            LocalDate startDate,
                            LocalDate endDate,
                            boolean visible,
                            boolean masked,
                            boolean editable) {
    }
}
