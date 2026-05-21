package com.homeiot.camera.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CameraDeviceResponse(
        String deviceId,
        String name,
        String room,
        String deviceType,
        String ip,
        boolean online,
        boolean cameraReady,
        OffsetDateTime lastFrameAt,
        Integer width,
        Integer height,
        long frameBytes,
        double ingestFps,
        long lastFrameAgeMs
) {
}
