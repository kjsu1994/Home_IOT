package com.homeiot.command.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceCommandCreateRequest(
        @NotBlank String commandType,
        String payloadJson
) {
}
