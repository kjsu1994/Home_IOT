package com.homeiot.device.repository;

import com.homeiot.device.entity.HomeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeGroupRepository extends JpaRepository<HomeGroup, Long> {
    List<HomeGroup> findAllByOwner_IdOrderByCreatedAtAsc(Long ownerId);
}
