package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceResponse(
        String deviceId,
        String name,
        String displayName,
        String room,
        String deviceType,
        String fwVersion,
        String ip,
        Integer rssi,
        boolean online,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        boolean shareManager
) {
}
