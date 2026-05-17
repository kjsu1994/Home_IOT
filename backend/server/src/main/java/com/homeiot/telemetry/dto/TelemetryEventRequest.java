package com.homeiot.telemetry.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TelemetryEventRequest(
        @NotBlank String eventType,
        String payloadJson
) {
}
