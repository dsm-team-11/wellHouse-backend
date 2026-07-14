package com.wellhouse.backend.schedule;

import com.wellhouse.backend.service.CommandService;
import com.wellhouse.backend.service.DeviceService;
import com.wellhouse.backend.service.MaintenanceService;
import com.wellhouse.backend.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기 작업 (Firebase Cloud Scheduler 대응).
 *  - 오프라인 감지 1분 · 명령 타임아웃 30초 · 기상 폴링 30분 · 수위 정리 1시간
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final DeviceService deviceService;
    private final CommandService commandService;
    private final WeatherService weatherService;
    private final MaintenanceService maintenanceService;

    // initialDelay: 부팅 시 즉시 실행되어 시드(DevDataLoader)와 경합하는 것을 방지
    @Scheduled(initialDelay = 30_000, fixedRate = 60_000) // 1분
    public void offlineScan() {
        int changed = deviceService.scanOffline();
        if (changed > 0) log.info("offline scan: {} devices marked offline", changed);
    }

    @Scheduled(initialDelay = 15_000, fixedRate = 30_000) // 30초
    public void commandTimeoutSweep() {
        commandService.sweepTimeouts();
    }

    @Scheduled(initialDelay = 20_000, fixedRate = 1_800_000) // 30분
    public void weatherPoll() {
        weatherService.pollAll();
    }

    @Scheduled(initialDelay = 60_000, fixedRate = 3_600_000) // 1시간
    public void waterCleanup() {
        maintenanceService.cleanupOldWater();
    }
}
