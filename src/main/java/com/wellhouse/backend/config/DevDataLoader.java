package com.wellhouse.backend.config;

import com.wellhouse.backend.domain.risk.Advisory;
import com.wellhouse.backend.domain.risk.RiskLevel;
import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.DeviceStateEntity;
import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.entity.WeatherEntity;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.DeviceStateRepository;
import com.wellhouse.backend.repository.UserRepository;
import com.wellhouse.backend.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** 개발 프로파일 데모 시드: 기상 지역 + 페어링 대기 데모 기기. */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataLoader implements CommandLineRunner {

    private final WeatherRepository weatherRepo;
    private final DeviceRepository deviceRepo;
    private final DeviceStateRepository stateRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 시연용 고정 데모 계정: 앱에서 demo / demo1234 로 로그인.
        // (앱 규칙상 아이디 "demo" → 이메일 "demo@wellhouse.app")
        String demoEmail = "demo@wellhouse.app";
        if (userRepo.findByEmail(demoEmail).isEmpty()) {
            userRepo.save(UserEntity.builder()
                    .uid(UUID.randomUUID().toString())
                    .email(demoEmail)
                    .passwordHash(passwordEncoder.encode("demo1234"))
                    .address("서울특별시 중구")   // 날씨가 서울 격자로 해석되도록
                    .build());
            log.info("데모 계정 시드: {} (pw=demo1234)", demoEmail);
        }

        for (String region : new String[]{"seoul", "incheon", "gyeonggi"}) {
            if (!weatherRepo.existsById(region)) {
                weatherRepo.save(WeatherEntity.builder()
                        .region(region).rainMmH(0).cumulative3hMm(0)
                        .advisory(Advisory.NONE).forecastMmH(0).updatedAt(Instant.now())
                        .build());
            }
        }

        // 데모 기기: 매 부팅마다 "페어링 가능 + 미소유" 상태로 리셋(개발 편의).
        // → 어느 계정으로 로그인해도 앱이 자동 페어링해 서버 데이터를 볼 수 있다.
        String demoId = "demo-device-01";
        DeviceEntity demo = deviceRepo.findById(demoId)
                .orElseGet(() -> DeviceEntity.builder().deviceId(demoId).build());
        demo.setRegion("seoul");
        demo.setLat(37.5665);
        demo.setLng(126.9780);
        demo.setOnline(false);
        demo.setMode("normal");
        demo.setOwnerUid(null);                 // 소유권 해제(재페어링 가능)
        demo.setPairingCode("123456");          // 페어링 코드 복원
        demo.setPairingExpiresAt(Instant.now().plusSeconds(3600L * 24 * 365));
        deviceRepo.save(demo);
        log.info("데모 기기 리셋: {} (pairingCode=123456, owner 해제)", demoId);

        // 앱 데모: 페어링 즉시 상태가 보이도록 GOOD(양호)로 시작점 시드.
        // 실제 ESP 수위가 들어오면 서버가 수위에 따라 단계를 갱신한다(양호→주의→경고→위험).
        DeviceStateEntity st = stateRepo.findById(demoId)
                .orElseGet(() -> DeviceStateEntity.builder().deviceId(demoId).build());
        st.setLevel(RiskLevel.GOOD);
        st.setRawLevel(RiskLevel.GOOD);
        st.setRiseCmPerMin(0.0);
        st.setReportPending(false);
        st.setFloodEpisodeActive(false);
        st.setUpdatedAt(Instant.now());
        stateRepo.save(st);
        log.info("데모 기기 상태 시드: {} level=GOOD", demoId);
    }
}
