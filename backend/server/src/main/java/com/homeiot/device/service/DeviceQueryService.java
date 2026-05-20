package com.homeiot.device.service;

import com.homeiot.auth.service.OwnershipService;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.dto.DeviceDetailResponse;
import com.homeiot.device.dto.DeviceResponse;
import com.homeiot.device.dto.DeviceStateResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceShare;
import com.homeiot.device.entity.DeviceState;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.device.repository.DeviceStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeviceQueryService {
    private static final Duration OFFLINE_AFTER = Duration.ofSeconds(90);

    private final DeviceRepository deviceRepository;
    private final DeviceShareRepository deviceShareRepository;
    private final DeviceStateRepository deviceStateRepository;
    private final OwnershipService ownershipService;

    public DeviceQueryService(DeviceRepository deviceRepository,
                              DeviceShareRepository deviceShareRepository,
                              DeviceStateRepository deviceStateRepository,
                              OwnershipService ownershipService) {
        this.deviceRepository = deviceRepository;
        this.deviceShareRepository = deviceShareRepository;
        this.deviceStateRepository = deviceStateRepository;
        this.ownershipService = ownershipService;
    }

    public List<DeviceResponse> getDevices() {
        if (ownershipService.isMaster()) {
            return deviceRepository.findAllByOrderByCreatedAtAsc().stream().map(this::toDeviceResponse).toList();
        }

        Long memberId = ownershipService.currentMember().getId();
        LinkedHashMap<String, Device> visibleDevices = new LinkedHashMap<>();
        deviceRepository.findAllByOwner_IdOrderByCreatedAtAsc(memberId)
                .forEach(device -> visibleDevices.put(device.getDeviceId(), device));
        deviceShareRepository.findAllByMember_IdOrderByCreatedAtAsc(memberId).stream()
                .map(DeviceShare::getDevice)
                .forEach(device -> visibleDevices.putIfAbsent(device.getDeviceId(), device));
        return visibleDevices.values().stream().map(this::toDeviceResponse).toList();
    }

    public DeviceDetailResponse getDevice(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .map(device -> {
                    ownershipService.requireDeviceAccess(device);
                    return device;
                })
                .map(this::toDeviceDetailResponse)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
    }

    public DeviceStateResponse getDeviceState(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceAccess(device);

        return deviceStateRepository.findByDeviceDeviceId(deviceId)
                .map(state -> toStateResponse(device.getDeviceId(), state))
                .orElse(new DeviceStateResponse(device.getDeviceId(), null, null, null, null, null));
    }

    public Device getDeviceEntity(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceAccess(device);
        return device;
    }

    private DeviceResponse toDeviceResponse(Device device) {
        return new DeviceResponse(
                device.getDeviceId(),
                device.getEffectiveName(),
                device.getDisplayName(),
                device.getRoom(),
                device.getDeviceType(),
                device.getFwVersion(),
                device.getIp(),
                device.getRssi(),
                isOnline(device),
                device.getLastSeenAt(),
                device.getCreatedAt(),
                ownershipService.canManageDevice(device)
        );
    }

    private DeviceDetailResponse toDeviceDetailResponse(Device device) {
        return new DeviceDetailResponse(
                device.getDeviceId(),
                device.getEffectiveName(),
                device.getDisplayName(),
                device.getRoom(),
                device.getDeviceType(),
                device.getFwVersion(),
                device.getIp(),
                device.getRssi(),
                isOnline(device),
                device.getLastSeenAt(),
                device.getCreatedAt(),
                ownershipService.canManageDevice(device)
        );
    }

    private boolean isOnline(Device device) {
        if (!device.isOnline() || device.getLastSeenAt() == null) {
            return false;
        }
        return device.getLastSeenAt().isAfter(OffsetDateTime.now().minus(OFFLINE_AFTER));
    }

    private DeviceStateResponse toStateResponse(String deviceId, DeviceState state) {
        return new DeviceStateResponse(
                deviceId,
                state.getLedOn(),
                state.getWifiConnected(),
                state.getOtaReady(),
                state.getLastReportedAt(),
                state.getRawStateJson()
        );
    }
}
