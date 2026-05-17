package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceRegisterRequest(
        @NotBlank String deviceId,
        @NotBlank String name,
        @NotBlank String deviceType,
        @NotBlank String fwVersion,
        String ip,
        Integer rssi
) {
}
