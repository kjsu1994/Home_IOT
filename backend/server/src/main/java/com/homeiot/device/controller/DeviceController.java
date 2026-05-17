package com.homeiot.device.controller;

import com.homeiot.common.dto.ApiResponse;
import com.homeiot.device.dto.DeviceDetailResponse;
import com.homeiot.device.dto.DeviceHealthRequest;
import com.homeiot.device.dto.DeviceRegisterRequest;
import com.homeiot.device.dto.DeviceRegisterResponse;
import com.homeiot.device.dto.DeviceResponse;
import com.homeiot.device.dto.DeviceStateReportRequest;
import com.homeiot.device.dto.DeviceStateResponse;
import com.homeiot.device.service.DeviceQueryService;
import com.homeiot.device.service.DeviceService;
import com.homeiot.device.service.DeviceStateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceQueryService deviceQueryService;
    private final DeviceStateService deviceStateService;

    public DeviceController(DeviceService deviceService,
                            DeviceQueryService deviceQueryService,
                            DeviceStateService deviceStateService) {
        this.deviceService = deviceService;
        this.deviceQueryService = deviceQueryService;
        this.deviceStateService = deviceStateService;
    }

    @PostMapping("/register")
    public ApiResponse<DeviceRegisterResponse> register(@Valid @RequestBody DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> getDevices() {
        return ApiResponse.ok(deviceQueryService.getDevices());
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceDetailResponse> getDevice(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceQueryService.getDevice(deviceId));
    }

    @GetMapping("/{deviceId}/state")
    public ApiResponse<DeviceStateResponse> getState(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceQueryService.getDeviceState(deviceId));
    }

    @PostMapping("/{deviceId}/health")
    public ApiResponse<DeviceStateResponse> updateHealth(@PathVariable String deviceId,
                                                         @RequestBody DeviceHealthRequest request) {
        return ApiResponse.ok(deviceStateService.updateHealth(deviceId, request));
    }

    @PostMapping("/{deviceId}/state/report")
    public ApiResponse<DeviceStateResponse> reportState(@PathVariable String deviceId,
                                                        @RequestBody DeviceStateReportRequest request) {
        return ApiResponse.ok(deviceStateService.reportState(deviceId, request));
    }
}
