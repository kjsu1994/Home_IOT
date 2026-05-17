package com.homeiot.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OtaEventRequest(
        String fromVersion,
        String toVersion,
        String status,
        String message,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
