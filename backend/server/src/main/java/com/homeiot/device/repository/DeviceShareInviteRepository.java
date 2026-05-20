package com.homeiot.device.repository;

import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceShareInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceShareInviteRepository extends JpaRepository<DeviceShareInvite, Long> {
    List<DeviceShareInvite> findAllByDevice_DeviceIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtAsc(String deviceId);
    List<DeviceShareInvite> findAllByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtAsc(String email);
    Optional<DeviceShareInvite> findByIdAndDevice_DeviceId(Long id, String deviceId);
    Optional<DeviceShareInvite> findByDeviceAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(Device device, String email);
}
