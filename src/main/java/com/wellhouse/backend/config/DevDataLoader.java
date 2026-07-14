package com.wellhouse.backend.config;

import com.wellhouse.backend.domain.risk.Advisory;
import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.WeatherEntity;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 개발 프로파일 데모 시드: 기상 지역 + 페어링 대기 데모 기기. */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataLoader implements CommandLineRunner {

    private final WeatherRepository weatherRepo;
    private final DeviceRepository deviceRepo;

    @Override
    public void run(String... args) {
        for (String region : new String[]{"seoul", "incheon", "gyeonggi"}) {
            if (!weatherRepo.existsById(region)) {
                weatherRepo.save(WeatherEntity.builder()
                        .region(region).rainMmH(0).cumulative3hMm(0)
                        .advisory(Advisory.NONE).forecastMmH(0).updatedAt(Instant.now())
                        .build());
            }
        }

        String demoId = "demo-device-01";
        if (!deviceRepo.existsById(demoId)) {
            deviceRepo.save(DeviceEntity.builder()
                    .deviceId(demoId)
                    .region("seoul")
                    .lat(37.5665).lng(126.9780)
                    .online(false)
                    .mode("normal")
                    .pairingCode("123456")
                    .pairingExpiresAt(Instant.now().plusSeconds(3600 * 24 * 365))
                    .build());
            log.info("데모 기기 생성: {} (pairingCode=123456)", demoId);
        }
    }
}
