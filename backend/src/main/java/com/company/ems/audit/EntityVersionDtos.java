package com.company.ems.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class EntityVersionDtos {

    private EntityVersionDtos() {
    }

    public record VersionView(Long id,
                              String entityType,
                              Long entityId,
                              Integer versionNo,
                              String changeType,
                              List<String> changedFields,
                              Long operatorId,
                              String operatorName,
                              Long approvalId,
                              LocalDateTime createdAt) {
    }

    public record VersionDetail(VersionView version,
                                Map<String, Object> snapshot,
                                Map<String, Object> diff,
                                List<String> maskedFields) {
    }

    public record RollbackRequest(Long versionId, String reason) {
    }

    public record RollbackResult(String status, Long approvalId, Long newVersionId, String message) {
    }
}
