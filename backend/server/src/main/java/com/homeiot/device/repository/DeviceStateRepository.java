package com.homeiot.device.repository;

import com.homeiot.device.entity.DeviceState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceStateRepository extends JpaRepository<DeviceState, Long> {
    Optional<DeviceState> findByDeviceDeviceId(String deviceId);
}
