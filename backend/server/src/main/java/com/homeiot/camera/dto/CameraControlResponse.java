package com.homeiot.camera.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CameraControlResponse(
        String mode,
        long uploadIntervalMs,
        int jpegQuality,
        String frameSize,
        int viewerCount,
        boolean recording
) {
}
