package com.homeiot.device.repository;

import com.homeiot.device.entity.DeviceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceAuditLogRepository extends JpaRepository<DeviceAuditLog, Long> {
    List<DeviceAuditLog> findTop30ByDevice_DeviceIdOrderByCreatedAtDesc(String deviceId);
}
