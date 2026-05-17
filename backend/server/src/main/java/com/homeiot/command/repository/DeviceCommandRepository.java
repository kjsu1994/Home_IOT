package com.homeiot.command.repository;

import com.homeiot.command.entity.DeviceCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {
    List<DeviceCommand> findByDeviceDeviceIdOrderByRequestedAtDesc(String deviceId);
}
