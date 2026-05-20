package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceClaimRequest(
        String deviceId,
        String deviceApiKey,
        String claimToken,
        String displayName,
        String room
) {
}