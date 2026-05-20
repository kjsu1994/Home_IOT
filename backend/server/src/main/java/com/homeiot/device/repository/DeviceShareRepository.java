package com.homeiot.device.repository;

import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceShareRepository extends JpaRepository<DeviceShare, Long> {
    boolean existsByDevice_IdAndMember_Id(Long deviceId, Long memberId);
    List<DeviceShare> findAllByMember_IdOrderByCreatedAtAsc(Long memberId);
    List<DeviceShare> findAllByDevice_DeviceIdOrderByCreatedAtAsc(String deviceId);
    Optional<DeviceShare> findByDevice_DeviceIdAndMember_Id(String deviceId, Long memberId);
    boolean existsByDeviceAndMember_Id(Device device, Long memberId);
}
