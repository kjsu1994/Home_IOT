package com.homeiot.telemetry.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TelemetryEventResponse(
        Long telemetryId,
        String eventType,
        String payloadJson,
        OffsetDateTime createdAt
) {
}
