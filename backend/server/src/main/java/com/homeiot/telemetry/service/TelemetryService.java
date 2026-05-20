package com.homeiot.telemetry.service;

import com.homeiot.auth.service.OwnershipService;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.telemetry.dto.TelemetryEventRequest;
import com.homeiot.telemetry.dto.TelemetryEventResponse;
import com.homeiot.telemetry.entity.TelemetryEvent;
import com.homeiot.telemetry.repository.TelemetryEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TelemetryService {

    private final DeviceRepository deviceRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final OwnershipService ownershipService;

    public TelemetryService(DeviceRepository deviceRepository,
                            TelemetryEventRepository telemetryEventRepository,
                            OwnershipService ownershipService) {
        this.deviceRepository = deviceRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.ownershipService = ownershipService;
    }

    @Transactional
    public TelemetryEventResponse create(String deviceId, TelemetryEventRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        TelemetryEvent event = telemetryEventRepository.save(new TelemetryEvent(device, request.eventType(), request.payloadJson()));
        return toResponse(event);
    }

    public List<TelemetryEventResponse> getLatest(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceAccess(device);
        return telemetryEventRepository.findTop50ByDeviceDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    private TelemetryEventResponse toResponse(TelemetryEvent event) {
        return new TelemetryEventResponse(
                event.getId(),
                event.getEventType(),
                event.getPayloadJson(),
                event.getCreatedAt()
        );
    }
}
