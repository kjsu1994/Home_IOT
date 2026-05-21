package com.homeiot.device.service;

import com.homeiot.auth.service.AuthService;
import com.homeiot.auth.service.OwnershipService;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.device.dto.DeviceAuditResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceAuditLog;
import com.homeiot.device.repository.DeviceAuditLogRepository;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.member.entity.Member;
import com.homeiot.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeviceAuditService {
    private final DeviceAuditLogRepository auditLogRepository;
    private final DeviceRepository deviceRepository;
    private final OwnershipService ownershipService;
    private final AuthService authService;
    private final MemberRepository memberRepository;

    public DeviceAuditService(DeviceAuditLogRepository auditLogRepository,
                              DeviceRepository deviceRepository,
                              OwnershipService ownershipService,
                              AuthService authService,
                              MemberRepository memberRepository) {
        this.auditLogRepository = auditLogRepository;
        this.deviceRepository = deviceRepository;
        this.ownershipService = ownershipService;
        this.authService = authService;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void record(Device device, Member actor, String action, String targetValue, String message) {
        auditLogRepository.save(new DeviceAuditLog(device, actor, action, targetValue, message));
    }

    @Transactional
    public void recordCurrentActor(Device device, String action, String targetValue, String message) {
        String email = authService.currentEmailOrNull();
        Member actor = email == null ? null : memberRepository.findByEmail(email).orElse(null);
        record(device, actor, action, targetValue, message);
    }

    public List<DeviceAuditResponse> getAudits(String deviceId) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceManager(device);
        return auditLogRepository.findTop30ByDevice_DeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    private DeviceAuditResponse toResponse(DeviceAuditLog log) {
        Member actor = log.getActor();
        return new DeviceAuditResponse(
                log.getId(),
                log.getAction(),
                log.getTargetValue(),
                actor == null ? null : actor.getEmail(),
                actor == null ? null : actor.getName(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
