package com.homeiot.ota.controller;

import com.homeiot.common.dto.ApiResponse;
import com.homeiot.ota.dto.OtaEventRequest;
import com.homeiot.ota.dto.OtaStatusRequest;
import com.homeiot.ota.service.OtaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{deviceId}/ota")
public class OtaController {

    private final OtaService otaService;

    public OtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @PostMapping("/events")
    public ApiResponse<Void> record(@PathVariable String deviceId, @RequestBody OtaEventRequest request) {
        otaService.record(deviceId, request);
        return ApiResponse.ok(null, "ota event recorded");
    }

    @PostMapping("/status")
    public ApiResponse<Void> recordStatus(@PathVariable String deviceId, @RequestBody OtaStatusRequest request) {
        otaService.recordStatus(deviceId, request);
        return ApiResponse.ok(null, "ota status recorded");
    }
}
