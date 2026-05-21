package com.homeiot.camera.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CameraStreamTokenResponse(
        String deviceId,
        String streamToken,
        OffsetDateTime expiresAt,
        String streamPath
) {
}
