package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceClaimTokenHistoryResponse(
        Long tokenId,
        String status,
        String createdByEmail,
        String usedByEmail,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime usedAt,
        OffsetDateTime revokedAt
) {
}
