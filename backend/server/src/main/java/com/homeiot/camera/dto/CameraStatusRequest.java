package com.homeiot.camera.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CameraStatusRequest(
        Boolean cameraReady,
        Integer width,
        Integer height,
        Long captureMs,
        Long uploadMs,
        Long totalMs,
        Long firmwareFrameBytes,
        Long appliedIntervalMs,
        Long captureFailures,
        Long uploadFailures,
        Boolean cameraStreaming,
        Boolean cameraControlReceived,
        String sensorName,
        Integer sensorPid,
        String message
) {
}
