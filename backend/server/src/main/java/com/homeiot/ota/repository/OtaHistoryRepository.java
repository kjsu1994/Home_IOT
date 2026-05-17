package com.homeiot.ota.repository;

import com.homeiot.ota.entity.OtaHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtaHistoryRepository extends JpaRepository<OtaHistory, Long> {
}
