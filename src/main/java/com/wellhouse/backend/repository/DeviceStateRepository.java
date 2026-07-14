package com.wellhouse.backend.repository;

import com.wellhouse.backend.entity.DeviceStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceStateRepository extends JpaRepository<DeviceStateEntity, String> {
}
