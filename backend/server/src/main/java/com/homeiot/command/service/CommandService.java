package com.homeiot.command.service;

import com.homeiot.auth.service.OwnershipService;
import com.homeiot.command.dto.DeviceCommandCreateRequest;
import com.homeiot.command.dto.DeviceCommandResponse;
import com.homeiot.command.dto.DeviceCommandStageRequest;
import com.homeiot.command.entity.CommandStatus;
import com.homeiot.command.entity.DeviceCommand;
import com.homeiot.command.repository.DeviceCommandRepository;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.mqtt.publisher.DeviceMqttPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CommandService {
    private static final List<CommandStatus> DELIVERABLE_STATUSES = List.copyOf(EnumSet.of(
            CommandStatus.PENDING,
            CommandStatus.SENT
    ));

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final DeviceMqttPublisher deviceMqttPublisher;
    private final OwnershipService ownershipService;

    public CommandService(DeviceRepository deviceRepository,
                          DeviceCommandRepository deviceCommandRepository,
                          DeviceMqttPublisher deviceMqttPublisher,
                          OwnershipService ownershipService) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandRepository = deviceCommandRepository;
        this.deviceMqttPublisher = deviceMqttPublisher;
        this.ownershipService = ownershipService;
    }

    @Transactional
    public DeviceCommandResponse createCommand(String deviceId, DeviceCommandCreateRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceAccess(device);

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
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceAccess(device);
        return deviceCommandRepository.findByDeviceDeviceIdOrderByRequestedAtDesc(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<DeviceCommandResponse> getPendingCommand(String deviceId) {
        if (deviceRepository.findByDeviceId(deviceId).isEmpty()) {
            throw new NotFoundException("device not found: " + deviceId);
        }
        return deviceCommandRepository
                .findFirstByDeviceDeviceIdAndStatusInOrderByRequestedAtAsc(deviceId, DELIVERABLE_STATUSES)
                .map(this::toResponse);
    }

    @Transactional
    public DeviceCommandResponse updateCommandStage(String deviceId, Long commandId, DeviceCommandStageRequest request) {
        DeviceCommand command = deviceCommandRepository.findById(commandId)
                .filter(candidate -> candidate.getDevice().getDeviceId().equals(deviceId))
                .orElseThrow(() -> new NotFoundException("command not found: " + commandId));

        String stage = request.stage().toUpperCase(Locale.ROOT);
        switch (stage) {
            case "RECEIVED" -> command.markReceived();
            case "APPLIED" -> command.markApplied();
            case "FAILED" -> command.markFailed(defaultFailureReason(request.resultMessage()));
            default -> throw new IllegalArgumentException("unsupported command stage: " + request.stage());
        }

        return toResponse(command);
    }

    private String defaultFailureReason(String resultMessage) {
        if (resultMessage == null || resultMessage.isBlank()) {
            return "device reported command failure";
        }
        return resultMessage;
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
