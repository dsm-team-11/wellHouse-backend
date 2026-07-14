package com.wellhouse.backend.service;

import com.wellhouse.backend.repository.WaterSampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** 데이터 정리 (수위 원본 24h TTL). */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private static final Duration RETENTION = Duration.ofHours(24);

    private final WaterSampleRepository waterRepo;

    @Transactional
    public int cleanupOldWater() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int removed = waterRepo.deleteByTsBefore(cutoff);
        if (removed > 0) log.info("수위 원본 정리: {}건 삭제", removed);
        return removed;
    }
}
