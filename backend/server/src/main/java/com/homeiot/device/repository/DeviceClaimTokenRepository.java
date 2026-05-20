package com.homeiot.device.repository;

import com.homeiot.device.entity.DeviceClaimToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceClaimTokenRepository extends JpaRepository<DeviceClaimToken, Long> {
    Optional<DeviceClaimToken> findByTokenHash(String tokenHash);
    Optional<DeviceClaimToken> findByIdAndDevice_DeviceId(Long id, String deviceId);
    List<DeviceClaimToken> findTop20ByDevice_DeviceIdOrderByCreatedAtDesc(String deviceId);
}
