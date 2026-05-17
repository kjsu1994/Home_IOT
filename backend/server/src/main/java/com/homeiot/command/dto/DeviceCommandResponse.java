package com.homeiot.command.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceCommandResponse(
        Long commandId,
        String commandType,
        String payloadJson,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime sentAt,
        OffsetDateTime appliedAt,
        OffsetDateTime failedAt,
        String failureReason
) {
}
