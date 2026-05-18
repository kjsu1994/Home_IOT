package com.homeiot.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OtaStatusRequest(
        String otaStatus,
        Integer progressPercent,
        String message,
        String targetVersion
) {
}
