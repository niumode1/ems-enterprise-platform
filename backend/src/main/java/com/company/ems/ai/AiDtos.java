package com.company.ems.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

public final class AiDtos {

    private AiDtos() {
    }

    @Data
    public static class ProviderRequest {
        private Long id;
        @NotBlank
        private String name;
        private String providerType;
        @NotBlank
        private String baseUrl;
        private String apiKey;
        @NotBlank
        private String model;
        private Boolean enabled;
        private Boolean isDefault;
    }

    public record ProviderView(Long id,
                               String name,
                               String providerType,
                               String baseUrl,
                               String model,
                               boolean enabled,
                               boolean isDefault,
                               boolean hasApiKey,
                               String maskedApiKey,
                               LocalDateTime updatedAt) {
    }

    @Data
    public static class TestRequest {
        private Long providerId;
    }

    public record TestResult(boolean success, String message, long latencyMs) {
    }
}
