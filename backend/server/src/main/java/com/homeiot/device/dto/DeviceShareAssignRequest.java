package com.homeiot.device.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceShareAssignRequest(
        @NotBlank @Email String email,
        @Pattern(regexp = "^(VIEWER|CONTROLLER|OWNER|USER)?$") String role
) {
}
