package com.wellhouse.backend.repository;

import com.wellhouse.backend.entity.WaterSampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaterSampleRepository extends JpaRepository<WaterSampleEntity, Long> {

    Optional<WaterSampleEntity> findTop1ByDeviceIdOrderByTsDesc(String deviceId);

    List<WaterSampleEntity> findByDeviceIdAndTsGreaterThanEqual(String deviceId, Instant since);

    @Modifying
    @Query("delete from WaterSampleEntity w where w.ts < :cutoff")
    int deleteByTsBefore(@Param("cutoff") Instant cutoff);
}
