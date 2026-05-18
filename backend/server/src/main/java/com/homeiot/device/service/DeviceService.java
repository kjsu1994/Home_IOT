package com.homeiot.device.service;

import com.homeiot.common.error.NotFoundException;
import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.dto.DeviceDetailResponse;
import com.homeiot.device.dto.DeviceMetadataUpdateRequest;
import com.homeiot.device.dto.DeviceRegisterRequest;
import com.homeiot.device.dto.DeviceRegisterResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DeviceService {
    private static final Duration OFFLINE_AFTER = Duration.ofSeconds(90);

    private final DeviceRepository deviceRepository;
    private final DeviceTokenService deviceTokenService;
    private final String bootstrapApiKey;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceTokenService deviceTokenService,
                         @Value("${app.device-api-key:}") String bootstrapApiKey) {
        this.deviceRepository = deviceRepository;
        this.deviceTokenService = deviceTokenService;
        this.bootstrapApiKey = bootstrapApiKey == null ? "" : bootstrapApiKey.trim();
    }

    @Transactional
    public DeviceRegisterResponse register(DeviceRegisterRequest request, String presentedKey) {
        Optional<Device> existingDevice = deviceRepository.findByDeviceId(request.deviceId());
        validateRegistrationKey(existingDevice, presentedKey);

        Device device = existingDevice
                .map(existing -> {
                    existing.refreshFrom(
                            request.name(),
                            request.deviceType(),
                            request.fwVersion(),
                            request.ip(),
                            request.rssi()
                    );
                    return existing;
                })
                .orElseGet(() -> new Device(
                        request.deviceId(),
                        request.name(),
                        request.deviceType(),
                        request.fwVersion(),
                        request.ip(),
                        request.rssi()
                ));

        boolean isNew = device.getId() == null;
        boolean issueDeviceToken = shouldIssueDeviceToken(device, presentedKey);
        String deviceApiKey = null;
        if (issueDeviceToken) {
            deviceApiKey = deviceTokenService.generateToken();
            device.updateDeviceApiKeyHash(deviceTokenService.hashToken(deviceApiKey));
        }

        deviceRepository.save(device);

        return new DeviceRegisterResponse(
                device.getDeviceId(),
                true,
                deviceApiKey,
                isNew ? "device registered" : "device registration refreshed"
        );
    }

    @Transactional
    public DeviceDetailResponse updateMetadata(String deviceId, DeviceMetadataUpdateRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));

        device.updateMetadata(request.displayName(), request.room());

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
                device.getLastSeenAt()
        );
    }

    private boolean isOnline(Device device) {
        if (!device.isOnline() || device.getLastSeenAt() == null) {
            return false;
        }
        return device.getLastSeenAt().isAfter(OffsetDateTime.now().minus(OFFLINE_AFTER));
    }

    private void validateRegistrationKey(Optional<Device> existingDevice, String presentedKey) {
        if (bootstrapApiKey.isBlank()) {
            return;
        }
        if (bootstrapApiKey.equals(presentedKey)) {
            return;
        }
        if (existingDevice.isPresent() && deviceTokenService.matches(existingDevice.get(), presentedKey)) {
            return;
        }
        throw new UnauthorizedException("invalid device api key");
    }

    private boolean shouldIssueDeviceToken(Device device, String presentedKey) {
        if (bootstrapApiKey.isBlank()) {
            return device.getDeviceApiKeyHash() == null;
        }
        return device.getDeviceApiKeyHash() == null || bootstrapApiKey.equals(presentedKey);
    }
}
