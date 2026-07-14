package com.wellhouse.backend.repository;

import com.wellhouse.backend.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {
    List<DeviceEntity> findByRegion(String region);
    List<DeviceEntity> findByOwnerUid(String ownerUid);
}
