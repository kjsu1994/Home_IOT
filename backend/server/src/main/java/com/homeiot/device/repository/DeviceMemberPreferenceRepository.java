package com.homeiot.device.repository;

import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceMemberPreference;
import com.homeiot.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceMemberPreferenceRepository extends JpaRepository<DeviceMemberPreference, Long> {
    Optional<DeviceMemberPreference> findByDeviceAndMember(Device device, Member member);
}
