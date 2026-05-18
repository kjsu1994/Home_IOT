package com.homeiot.command.repository;

import com.homeiot.command.entity.DeviceCommand;
import com.homeiot.command.entity.CommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {
    List<DeviceCommand> findByDeviceDeviceIdOrderByRequestedAtDesc(String deviceId);

    Optional<DeviceCommand> findFirstByDeviceDeviceIdAndStatusInOrderByRequestedAtAsc(
            String deviceId,
            List<CommandStatus> statuses
    );
}
