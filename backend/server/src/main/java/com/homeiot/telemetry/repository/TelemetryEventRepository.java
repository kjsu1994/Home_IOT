package com.homeiot.telemetry.repository;

import com.homeiot.telemetry.entity.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, Long> {
    List<TelemetryEvent> findTop50ByDeviceDeviceIdOrderByCreatedAtDesc(String deviceId);
}
