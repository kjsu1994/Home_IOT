package com.homeiot.auth.service;

import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.entity.Device;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.member.entity.DeviceOwnerMarker;
import com.homeiot.member.entity.Member;
import org.springframework.stereotype.Service;

@Service
public class OwnershipService {
    private final AuthService authService;
    private final DeviceShareRepository deviceShareRepository;

    public OwnershipService(AuthService authService,
                            DeviceShareRepository deviceShareRepository) {
        this.authService = authService;
        this.deviceShareRepository = deviceShareRepository;
    }

    public boolean isMaster() {
        return authService.currentUserHasRole("ROLE_MASTER");
    }

    public Member currentMember() {
        return authService.currentMember();
    }

    public void requireDeviceAccess(DeviceOwnerMarker owned) {
        if (isMaster()) {
            return;
        }
        Member member = currentMember();
        if (owned.ownerId() != null && owned.ownerId().equals(member.getId())) {
            return;
        }
        if (owned instanceof Device device && device.getId() != null
                && deviceShareRepository.existsByDevice_IdAndMember_Id(device.getId(), member.getId())) {
            return;
        }
        throw new UnauthorizedException("device access denied");
    }

    public boolean canManageDevice(Device device) {
        if (isMaster()) {
            return true;
        }
        Member member = currentMember();
        return device.ownerId() != null && device.ownerId().equals(member.getId());
    }

    public void requireDeviceManager(Device device) {
        if (!canManageDevice(device)) {
            throw new UnauthorizedException("device manager required");
        }
    }
}
