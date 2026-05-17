package com.homeiot.command.service;

import com.homeiot.command.dto.DeviceCommandCreateRequest;
import com.homeiot.command.dto.DeviceCommandResponse;
import com.homeiot.command.entity.DeviceCommand;
import com.homeiot.command.repository.DeviceCommandRepository;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.mqtt.publisher.DeviceMqttPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CommandService {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final DeviceMqttPublisher deviceMqttPublisher;

    public CommandService(DeviceRepository deviceRepository,
                          DeviceCommandRepository deviceCommandRepository,
                          DeviceMqttPublisher deviceMqttPublisher) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandRepository = deviceCommandRepository;
        this.deviceMqttPublisher = deviceMqttPublisher;
    }

    @Transactional
    public DeviceCommandResponse createCommand(String deviceId, DeviceCommandCreateRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));

        DeviceCommand command = new DeviceCommand(device, request.commandType(), request.payloadJson());
        deviceCommandRepository.save(command);

        boolean published = deviceMqttPublisher.publishCommand(
                deviceId,
                command.getId(),
                command.getCommandType(),
                command.getPayloadJson()
        );

        if (published) {
            command.markSent();
        } else {
            command.markFailed("mqtt publish failed");
        }

        return toResponse(command);
    }

    public List<DeviceCommandResponse> getCommands(String deviceId) {
        if (deviceRepository.findByDeviceId(deviceId).isEmpty()) {
            throw new NotFoundException("device not found: " + deviceId);
        }
        return deviceCommandRepository.findByDeviceDeviceIdOrderByRequestedAtDesc(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    private DeviceCommandResponse toResponse(DeviceCommand command) {
        return new DeviceCommandResponse(
                command.getId(),
                command.getCommandType(),
                command.getPayloadJson(),
                command.getStatus().name(),
                command.getRequestedAt(),
                command.getSentAt(),
                command.getAppliedAt(),
                command.getFailedAt(),
                command.getFailureReason()
        );
    }
}
