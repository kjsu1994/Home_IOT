package com.homeiot.device.service;

import com.homeiot.device.dto.DeviceRegisterRequest;
import com.homeiot.device.dto.DeviceRegisterResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public DeviceRegisterResponse register(DeviceRegisterRequest request) {
        Device device = deviceRepository.findByDeviceId(request.deviceId())
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
        deviceRepository.save(device);

        return new DeviceRegisterResponse(
                device.getDeviceId(),
                true,
                isNew ? "device registered" : "device registration refreshed"
        );
    }
}
