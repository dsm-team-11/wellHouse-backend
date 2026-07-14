package com.wellhouse.backend.service;

import com.wellhouse.backend.domain.risk.Advisory;
import com.wellhouse.backend.domain.risk.Thresholds;
import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.WeatherEntity;
import com.wellhouse.backend.messaging.DeviceCommander;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 기상 폴링/저장 + 강수 급증 시 절전 해제.
 * TODO(KMA): fetchRegionWeather 를 기상청 초단기실황/예보/특보 실연동으로 교체.
 *   지역→격자(nx,ny) 매핑, API 키(wellhouse.weather.kma-service-key) 필요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherRepository weatherRepo;
    private final DeviceRepository deviceRepo;
    private final DeviceCommander commander;

    @Value("${wellhouse.weather.regions:seoul}")
    private String regionsCsv;

    public List<String> regions() {
        return Arrays.stream(regionsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @Transactional
    public void pollAll() {
        for (String region : regions()) {
            Snapshot w = fetchRegionWeather(region);
            weatherRepo.save(WeatherEntity.builder()
                    .region(region)
                    .rainMmH(w.rainMmH())
                    .cumulative3hMm(w.cumulative3hMm())
                    .advisory(w.advisory())
                    .forecastMmH(w.forecastMmH())
                    .updatedAt(Instant.now())
                    .build());

            if (w.rainMmH() >= Thresholds.WAKEUP_RAIN) {
                wakeupRegion(region, w.rainMmH());
            }
        }
        log.info("weather poll done regions={}", regions().size());
    }

    public WeatherEntity current(String region) {
        return weatherRepo.findById(region).orElse(null);
    }

    private void wakeupRegion(String region, double rainMmH) {
        for (DeviceEntity d : deviceRepo.findByRegion(region)) {
            commander.sendWakeup(d.getDeviceId(), rainMmH, region);
        }
    }

    /** KMA 호출 자리표시자. 실연동 전까지 안전한 기본값. */
    Snapshot fetchRegionWeather(String region) {
        // TODO: 기상청 API fetch
        return new Snapshot(0, 0, Advisory.NONE, 0);
    }

    public record Snapshot(double rainMmH, double cumulative3hMm, Advisory advisory, double forecastMmH) {}
}
