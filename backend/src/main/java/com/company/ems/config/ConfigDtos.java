package com.company.ems.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

public final class ConfigDtos {

    private ConfigDtos() {
    }

    @Data
    public static class UpdateRequest {
        @NotNull
        private String value;
        private String reason;
        private String group;
        private String description;
        private String valueType;
    }

    public record UpdateResult(String status, Long approvalId, String message, String value) {
    }
}
