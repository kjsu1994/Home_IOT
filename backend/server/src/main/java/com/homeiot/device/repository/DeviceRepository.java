package com.homeiot.device.repository;

import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.HomeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceId(String deviceId);
    List<Device> findAllByOrderByCreatedAtAsc();
    List<Device> findAllByOwner_IdOrderByCreatedAtAsc(Long ownerId);
    List<Device> findAllByHomeOrderByCreatedAtAsc(HomeGroup home);
}

