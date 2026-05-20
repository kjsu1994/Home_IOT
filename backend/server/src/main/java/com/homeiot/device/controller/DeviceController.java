package com.homeiot.device.controller;

import com.homeiot.common.dto.ApiResponse;
import com.homeiot.device.dto.DeviceClaimRequest;
import com.homeiot.device.dto.DeviceClaimTokenHistoryResponse;
import com.homeiot.device.dto.DeviceClaimTokenResponse;
import com.homeiot.device.dto.DeviceDetailResponse;
import com.homeiot.device.dto.DeviceHealthRequest;
import com.homeiot.device.dto.DeviceMetadataUpdateRequest;
import com.homeiot.device.dto.DeviceRegisterRequest;
import com.homeiot.device.dto.DeviceRegisterResponse;
import com.homeiot.device.dto.DeviceResponse;
import com.homeiot.device.dto.DeviceShareAssignRequest;
import com.homeiot.device.dto.DeviceShareResponse;
import com.homeiot.device.dto.DeviceStateReportRequest;
import com.homeiot.device.dto.DeviceStateResponse;
import com.homeiot.device.service.DeviceQueryService;
import com.homeiot.device.service.DeviceService;
import com.homeiot.device.service.DeviceStateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<DeviceRegisterResponse> register(
            @RequestHeader(name = "X-Device-Api-Key", required = false) String presentedKey,
            @Valid @RequestBody DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request, presentedKey));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> getDevices() {
        return ApiResponse.ok(deviceQueryService.getDevices());
    }

    @PostMapping("/claim")
    public ApiResponse<DeviceDetailResponse> claim(@Valid @RequestBody DeviceClaimRequest request) {
        return ApiResponse.ok(deviceService.claim(request));
    }
    @PostMapping("/{deviceId}/claim-token")
    public ApiResponse<DeviceClaimTokenResponse> issueClaimToken(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceService.issueClaimToken(deviceId));
    }

    @GetMapping("/{deviceId}/claim-tokens")
    public ApiResponse<List<DeviceClaimTokenHistoryResponse>> getClaimTokenHistory(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceService.getClaimTokenHistory(deviceId));
    }

    @PostMapping("/{deviceId}/claim-tokens/{tokenId}/revoke")
    public ApiResponse<DeviceClaimTokenHistoryResponse> revokeClaimToken(@PathVariable String deviceId,
                                                                         @PathVariable Long tokenId) {
        return ApiResponse.ok(deviceService.revokeClaimToken(deviceId, tokenId));
    }

    @GetMapping("/{deviceId}/shares")
    public ApiResponse<List<DeviceShareResponse>> getShares(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceService.getShares(deviceId));
    }

    @PostMapping("/{deviceId}/shares")
    public ApiResponse<DeviceShareResponse> assignShare(@PathVariable String deviceId,
                                                        @Valid @RequestBody DeviceShareAssignRequest request) {
        return ApiResponse.ok(deviceService.assignShare(deviceId, request));
    }

    @DeleteMapping("/{deviceId}/shares/{memberId}")
    public ApiResponse<Void> removeShare(@PathVariable String deviceId, @PathVariable Long memberId) {
        deviceService.removeShare(deviceId, memberId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{deviceId}/share-invites/{inviteId}")
    public ApiResponse<Void> revokeShareInvite(@PathVariable String deviceId, @PathVariable Long inviteId) {
        deviceService.revokeShareInvite(deviceId, inviteId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceDetailResponse> getDevice(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceQueryService.getDevice(deviceId));
    }

    @PatchMapping("/{deviceId}/metadata")
    public ApiResponse<DeviceDetailResponse> updateMetadata(@PathVariable String deviceId,
                                                            @RequestBody DeviceMetadataUpdateRequest request) {
        return ApiResponse.ok(deviceService.updateMetadata(deviceId, request));
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
