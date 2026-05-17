package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceStateResponse(
        String deviceId,
        Boolean ledOn,
        Boolean wifiConnected,
        Boolean otaReady,
        OffsetDateTime lastReportedAt,
        String rawStateJson
) {
}
