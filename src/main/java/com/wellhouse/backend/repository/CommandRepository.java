package com.wellhouse.backend.repository;

import com.wellhouse.backend.entity.CommandEntity;
import com.wellhouse.backend.entity.CommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CommandRepository extends JpaRepository<CommandEntity, String> {
    List<CommandEntity> findByDeviceIdOrderByCreatedAtDesc(String deviceId);
    List<CommandEntity> findByStatusAndCreatedAtBefore(CommandStatus status, Instant before);
}
