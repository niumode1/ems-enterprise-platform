package com.company.ems.approval;

import java.util.Map;

public record ApprovalCompletedEvent(
        Long requestId,
        String policyCode,
        String entityType,
        Long entityId,
        Map<String, Object> payload,
        Long requesterId,
        String reason
) {
}
