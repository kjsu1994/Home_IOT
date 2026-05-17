package com.homeiot.ota.service;

import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.ota.dto.OtaEventRequest;
import com.homeiot.ota.entity.OtaHistory;
import com.homeiot.ota.repository.OtaHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OtaService {

    private final DeviceRepository deviceRepository;
    private final OtaHistoryRepository otaHistoryRepository;

    public OtaService(DeviceRepository deviceRepository, OtaHistoryRepository otaHistoryRepository) {
        this.deviceRepository = deviceRepository;
        this.otaHistoryRepository = otaHistoryRepository;
    }

    @Transactional
    public void record(String deviceId, OtaEventRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        otaHistoryRepository.save(new OtaHistory(
                device,
                request.fromVersion(),
                request.toVersion(),
                request.status(),
                request.message(),
                request.startedAt(),
                request.finishedAt()
        ));
    }
}
