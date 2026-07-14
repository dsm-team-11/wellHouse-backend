package com.wellhouse.backend.web;

import com.wellhouse.backend.domain.risk.RiskLevel;
import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.DeviceStateEntity;
import com.wellhouse.backend.entity.NotificationEntity;
import com.wellhouse.backend.entity.WaterSampleEntity;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.DeviceStateRepository;
import com.wellhouse.backend.repository.NotificationRepository;
import com.wellhouse.backend.repository.WaterSampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * 개발 전용 도구. dev 프로파일에서만 빈으로 등록된다(운영엔 존재하지 않음).
 * 앱 데모: 기기 상태를 즉시 바꿔 앱이 폴링으로 부드럽게 전환되는지 + 알림함이 채워지는지 확인.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final DeviceStateRepository stateRepo;
    private final DeviceRepository deviceRepo;
    private final NotificationRepository notifRepo;
    private final WaterSampleRepository waterSampleRepo;

    /**
     * 기기 상태를 지정 위험단계로 설정하고, 소유자에게 상황 알림을 생성한다(데모).
     * 예: POST /api/dev/state/demo-device-01/DANGER  (GOOD|CAUTION|WARNING|DANGER)
     */
    @PostMapping("/state/{deviceId}/{level}")
    public DeviceStateEntity setState(@PathVariable String deviceId, @PathVariable String level) {
        RiskLevel lv = RiskLevel.valueOf(level.toUpperCase());
        DeviceStateEntity st = stateRepo.findById(deviceId)
                .orElseGet(() -> DeviceStateEntity.builder().deviceId(deviceId).build());
        st.setLevel(lv);
        st.setRawLevel(lv);
        st.setUpdatedAt(Instant.now());
        stateRepo.save(st);

        // 데모 수위 샘플 적재 — 모니터링 수위 그래프가 실데이터로 그려지도록.
        waterSampleRepo.save(WaterSampleEntity.builder()
                .deviceId(deviceId).levelCm(cmFor(lv)).ts(Instant.now()).build());

        // 소유자 알림함에 상황 알림 적재(앱 알림 화면이 실제 데이터로 채워지도록).
        deviceRepo.findById(deviceId)
                .map(DeviceEntity::getOwnerUid)
                .ifPresent(uid -> notifRepo.save(NotificationEntity.builder()
                        .uid(uid)
                        .title(titleFor(lv))
                        .body(bodyFor(lv))
                        .deepLink("wellhouse://device/" + deviceId)
                        .read(false)
                        .createdAt(Instant.now())
                        .build()));
        return st;
    }

    /**
     * 시연 시작점 리셋: 기기 online=true + 상태 GOOD + 낮은 baseline 수위 샘플.
     * 이후 실제 센서로 물이 차오르면 서버가 위험단계를 자동 상승시킨다.
     * 예: POST /api/dev/demo/reset/demo-device-01
     */
    @PostMapping("/demo/reset/{deviceId}")
    public DeviceStateEntity resetDemo(@PathVariable String deviceId) {
        deviceRepo.findById(deviceId).ifPresent(dev -> {
            dev.setOnline(true);
            deviceRepo.save(dev);
        });

        DeviceStateEntity st = stateRepo.findById(deviceId)
                .orElseGet(() -> DeviceStateEntity.builder().deviceId(deviceId).build());
        st.setLevel(RiskLevel.GOOD);
        st.setRawLevel(RiskLevel.GOOD);
        st.setRiseCmPerMin(0.0);
        st.setUpdatedAt(Instant.now());
        stateRepo.save(st);

        // 깨끗한 시작점: 낮은 baseline 수위 한 점.
        waterSampleRepo.save(WaterSampleEntity.builder()
                .deviceId(deviceId).levelCm(0).ts(Instant.now()).build());
        return st;
    }

    /** 데모용 위험단계 → 대표 수위(cm). */
    private double cmFor(RiskLevel lv) {
        return switch (lv) {
            case GOOD -> 2;
            case CAUTION -> 8;
            case WARNING -> 16;
            case DANGER -> 32;
        };
    }

    private String titleFor(RiskLevel lv) {
        return switch (lv) {
            case DANGER -> "🚨 침수 위험 경보";
            case WARNING -> "⚠️ 침수 경고";
            case CAUTION -> "🟡 침수 주의";
            case GOOD -> "✅ 상황 종료";
        };
    }

    private String bodyFor(RiskLevel lv) {
        return switch (lv) {
            case DANGER -> "수위가 위험 단계에 도달했어요. 즉시 대피를 준비하세요.";
            case WARNING -> "빗물이 유입될 수 있어요. 귀중품을 높은 곳으로 옮겨주세요.";
            case CAUTION -> "비가 강해지고 있어요. 예방 조치를 시작했어요.";
            case GOOD -> "수위가 안정되어 침수 상황이 종료됐어요.";
        };
    }
}
