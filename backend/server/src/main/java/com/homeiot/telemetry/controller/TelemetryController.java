package com.homeiot.telemetry.controller;

import com.homeiot.common.dto.ApiResponse;
import com.homeiot.telemetry.dto.TelemetryEventRequest;
import com.homeiot.telemetry.dto.TelemetryEventResponse;
import com.homeiot.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ApiResponse<TelemetryEventResponse> create(@PathVariable String deviceId,
                                                      @Valid @RequestBody TelemetryEventRequest request) {
        return ApiResponse.ok(telemetryService.create(deviceId, request));
    }

    @GetMapping
    public ApiResponse<List<TelemetryEventResponse>> getLatest(@PathVariable String deviceId) {
        return ApiResponse.ok(telemetryService.getLatest(deviceId));
    }
}
