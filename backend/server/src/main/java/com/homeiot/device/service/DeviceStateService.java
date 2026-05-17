package com.homeiot.device.service;

import com.homeiot.command.entity.DeviceCommand;
import com.homeiot.command.repository.DeviceCommandRepository;
import com.homeiot.device.dto.DeviceHealthRequest;
import com.homeiot.device.dto.DeviceStateReportRequest;
import com.homeiot.device.dto.DeviceStateResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceState;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.repository.DeviceStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeviceStateService {

    private final DeviceRepository deviceRepository;
    private final DeviceStateRepository deviceStateRepository;
    private final DeviceCommandRepository deviceCommandRepository;

    public DeviceStateService(DeviceRepository deviceRepository,
                              DeviceStateRepository deviceStateRepository,
                              DeviceCommandRepository deviceCommandRepository) {
        this.deviceRepository = deviceRepository;
        this.deviceStateRepository = deviceStateRepository;
        this.deviceCommandRepository = deviceCommandRepository;
    }

    public DeviceStateResponse updateHealth(String deviceId, DeviceHealthRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new com.homeiot.common.error.NotFoundException("device not found: " + deviceId));

        device.updateHealth(request.online(), request.ip(), request.rssi());

        return deviceStateRepository.findByDeviceDeviceId(deviceId)
                .map(state -> new DeviceStateResponse(deviceId, state.getLedOn(), state.getWifiConnected(),
                        state.getOtaReady(), state.getLastReportedAt(), state.getRawStateJson()))
                .orElse(new DeviceStateResponse(deviceId, null, null, null, null, null));
    }

    public DeviceStateResponse reportState(String deviceId, DeviceStateReportRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new com.homeiot.common.error.NotFoundException("device not found: " + deviceId));
        device.updateHealth(true, null, null);

        DeviceState state = deviceStateRepository.findByDeviceDeviceId(deviceId)
                .orElseGet(() -> new DeviceState(device));
        state.update(request.ledOn(), request.wifiConnected(), request.otaReady(), request.rawStateJson());
        deviceStateRepository.save(state);

        if (Boolean.TRUE.equals(request.ledOn())) {
            markLatestCommandApplied(deviceId, "LED_ON");
        } else if (Boolean.FALSE.equals(request.ledOn())) {
            markLatestCommandApplied(deviceId, "LED_OFF");
        }
        if (request.rawStateJson() != null && request.rawStateJson().contains("\"led_mode\":\"STATUS\"")) {
            markLatestCommandApplied(deviceId, "NETWORK_STATUS");
        }

        return new DeviceStateResponse(
                deviceId,
                state.getLedOn(),
                state.getWifiConnected(),
                state.getOtaReady(),
                state.getLastReportedAt(),
                state.getRawStateJson()
        );
    }

    private void markLatestCommandApplied(String deviceId, String commandType) {
        deviceCommandRepository.findByDeviceDeviceIdOrderByRequestedAtDesc(deviceId).stream()
                .filter(command -> command.getCommandType().equalsIgnoreCase(commandType))
                .filter(command -> !"APPLIED".equals(command.getStatus().name()))
                .findFirst()
                .ifPresent(DeviceCommand::markApplied);
    }
}
