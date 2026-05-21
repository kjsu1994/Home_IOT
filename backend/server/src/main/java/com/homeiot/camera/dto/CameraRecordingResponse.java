package com.homeiot.camera.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CameraRecordingResponse(
        String recordingId,
        String deviceId,
        OffsetDateTime startedAt,
        OffsetDateTime stoppedAt,
        long frameCount,
        long totalBytes,
        boolean active
) {
}
