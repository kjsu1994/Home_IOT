package com.homeiot.camera.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CameraStatusRequest(
        Boolean cameraReady,
        Integer width,
        Integer height,
        String message
) {
}
