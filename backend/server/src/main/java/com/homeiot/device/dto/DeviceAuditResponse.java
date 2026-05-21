package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceAuditResponse(
        Long auditId,
        String action,
        String targetValue,
        String actorEmail,
        String actorName,
        String message,
        OffsetDateTime createdAt
) {
}
