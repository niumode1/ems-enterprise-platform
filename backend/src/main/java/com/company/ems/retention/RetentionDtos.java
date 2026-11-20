package com.company.ems.retention;

import lombok.Data;

import java.util.List;

public final class RetentionDtos {

    private RetentionDtos() {
    }

    @Data
    public static class RunRequest {
        private Boolean dryRun;
        private List<String> scopes;
    }

    public record ScopeResult(String scope,
                              int retentionDays,
                              String action,
                              long matched,
                              long affected,
                              boolean dryRun,
                              String note) {
    }

    public record RunResult(boolean dryRun, List<ScopeResult> results) {
    }

    public record ScopeStatus(String scope,
                              boolean enabled,
                              int retentionDays,
                              String action,
                              String detail) {
    }

    public record StatusView(boolean jobEnabled, List<ScopeStatus> scopes) {
    }
}
