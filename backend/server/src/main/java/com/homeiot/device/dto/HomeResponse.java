package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HomeResponse(
        Long homeId,
        String name,
        String ownerEmail,
        OffsetDateTime createdAt,
        List<HomeMemberResponse> members
) {
}
