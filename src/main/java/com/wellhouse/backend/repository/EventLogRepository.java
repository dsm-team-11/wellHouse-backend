package com.wellhouse.backend.repository;

import com.wellhouse.backend.entity.EventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLogEntity, Long> {
    List<EventLogEntity> findTop50ByDeviceIdOrderByTsDesc(String deviceId);
}
