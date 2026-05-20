package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceShareResponse(
        Long inviteId,
        Long memberId,
        String email,
        String name,
        String role,
        String source,
        String status,
        boolean owner,
        boolean removable,
        OffsetDateTime sharedAt,
        OffsetDateTime expiresAt
) {
}
