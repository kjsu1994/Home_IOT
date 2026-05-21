package com.homeiot.device.service;

import com.homeiot.auth.service.OwnershipService;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.dto.DeviceDetailResponse;
import com.homeiot.device.dto.DeviceResponse;
import com.homeiot.device.dto.DeviceStateResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceMemberPreference;
import com.homeiot.device.entity.DeviceShare;
import com.homeiot.device.entity.DeviceState;
import com.homeiot.device.repository.DeviceMemberPreferenceRepository;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.device.repository.DeviceStateRepository;
import com.homeiot.member.entity.Member;
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
    private final DeviceMemberPreferenceRepository preferenceRepository;
    private final DeviceShareRepository deviceShareRepository;
    private final DeviceStateRepository deviceStateRepository;
    private final OwnershipService ownershipService;

    public DeviceQueryService(DeviceRepository deviceRepository,
                              DeviceMemberPreferenceRepository preferenceRepository,
                              DeviceShareRepository deviceShareRepository,
                              DeviceStateRepository deviceStateRepository,
                              OwnershipService ownershipService) {
        this.deviceRepository = deviceRepository;
        this.preferenceRepository = preferenceRepository;
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
        DeviceMemberPreference preference = currentPreference(device);
        return new DeviceResponse(
                device.getDeviceId(),
                effectiveName(device, preference),
                device.getDisplayName(),
                effectiveRoom(device, preference),
                device.getDeviceType(),
                device.getFwVersion(),
                device.getIp(),
                device.getRssi(),
                isOnline(device),
                device.getLastSeenAt(),
                device.getCreatedAt(),
                device.getHome() == null ? null : device.getHome().getId(),
                device.getHome() == null ? null : device.getHome().getName(),
                preference == null ? null : preference.getDisplayName(),
                preference == null ? null : preference.getRoom(),
                ownershipService.canControlDevice(device),
                ownershipService.canManageDevice(device)
        );
    }

    private DeviceDetailResponse toDeviceDetailResponse(Device device) {
        DeviceMemberPreference preference = currentPreference(device);
        return new DeviceDetailResponse(
                device.getDeviceId(),
                effectiveName(device, preference),
                device.getDisplayName(),
                effectiveRoom(device, preference),
                device.getDeviceType(),
                device.getFwVersion(),
                device.getIp(),
                device.getRssi(),
                isOnline(device),
                device.getLastSeenAt(),
                device.getCreatedAt(),
                device.getHome() == null ? null : device.getHome().getId(),
                device.getHome() == null ? null : device.getHome().getName(),
                preference == null ? null : preference.getDisplayName(),
                preference == null ? null : preference.getRoom(),
                ownershipService.canControlDevice(device),
                ownershipService.canManageDevice(device)
        );
    }

    private DeviceMemberPreference currentPreference(Device device) {
        if (ownershipService.isMaster()) {
            return null;
        }
        Member member = ownershipService.currentMember();
        return preferenceRepository.findByDeviceAndMember(device, member).orElse(null);
    }

    private String effectiveName(Device device, DeviceMemberPreference preference) {
        return preference != null && preference.getDisplayName() != null ? preference.getDisplayName() : device.getEffectiveName();
    }

    private String effectiveRoom(Device device, DeviceMemberPreference preference) {
        return preference != null && preference.getRoom() != null ? preference.getRoom() : device.getRoom();
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
