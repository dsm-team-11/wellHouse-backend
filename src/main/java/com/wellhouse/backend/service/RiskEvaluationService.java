package com.wellhouse.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellhouse.backend.domain.risk.*;
import com.wellhouse.backend.entity.*;
import com.wellhouse.backend.messaging.AppRealtimePublisher;
import com.wellhouse.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 수위 입력 처리 파이프라인 (Firebase의 onWaterWrite 대응).
 *   상승속도 → 기상결합 → 위험도 → 히스테리시스 전이 → 골든타임 → 저장/실시간 → 자동조치.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluationService {

    private final WaterSampleRepository waterRepo;
    private final DeviceRepository deviceRepo;
    private final DeviceStateRepository stateRepo;
    private final WeatherRepository weatherRepo;
    private final EventLogRepository eventRepo;
    private final UserRepository userRepo;

    private final CommandService commandService;
    private final EmergencyService emergencyService;
    private final FcmService fcm;
    private final AppRealtimePublisher realtime;
    private final ObjectMapper objectMapper;

    /** 새 수위 샘플 처리. */
    @Transactional
    public void onWaterReading(String deviceId, double levelCm, Instant ts) {
        // 1) 상승 속도 (직전 샘플 기준) — 저장 전에 조회
        double riseCmPerMin = 0;
        Optional<WaterSampleEntity> prev = waterRepo.findTop1ByDeviceIdOrderByTsDesc(deviceId);
        if (prev.isPresent()) {
            double dMin = (ts.toEpochMilli() - prev.get().getTs().toEpochMilli()) / 60000.0;
            if (dMin > 0) riseCmPerMin = (levelCm - prev.get().getLevelCm()) / dMin;
        }
        waterRepo.save(WaterSampleEntity.builder().deviceId(deviceId).levelCm(levelCm).ts(ts).build());

        // 2) 기기 메타 + 기상 결합
        DeviceEntity device = deviceRepo.findById(deviceId).orElse(null);
        WeatherEntity weather = (device != null && device.getRegion() != null)
                ? weatherRepo.findById(device.getRegion()).orElse(null) : null;

        RiskInputs inputs = RiskInputs.builder()
                .levelCm(levelCm)
                .riseCmPerMin(riseCmPerMin)
                .rainMmPerH(weather != null ? weather.getRainMmH() : 0)
                .cumulativeRainMm(weather != null ? weather.getCumulative3hMm() : 0)
                .advisory(weather != null && weather.getAdvisory() != null ? weather.getAdvisory() : Advisory.NONE)
                .forecastMmPerH(weather != null ? weather.getForecastMmH() : 0)
                .build();
        RiskResult risk = RiskEngine.compute(inputs);

        // 3) 이전 상태 + 히스테리시스 전이
        DeviceStateEntity prevState = stateRepo.findById(deviceId).orElse(null);
        RiskLevel prevLevel = prevState != null && prevState.getLevel() != null ? prevState.getLevel() : RiskLevel.GOOD;
        Long prevCandidate = prevState != null ? prevState.getCandidateSince() : null;
        StateMachine.Transition transition = StateMachine.resolve(
                prevLevel, prevCandidate, risk.level(), System.currentTimeMillis(), risk.advisoryFloor());

        // 4) 골든타임 (바닥 면적 A 를 알면 유입 유량 Q=v·A 도 함께 역산)
        double targetCm = device != null && device.getGoldenTargetCm() != null
                ? device.getGoldenTargetCm() : Thresholds.DEFAULT_GOLDEN_TARGET_CM;
        double floorAreaM2 = floorAreaM2(device);
        GoldenTime.Result golden = GoldenTime.compute(levelCm, riseCmPerMin, targetCm, floorAreaM2);

        // 5) 상태 저장 + 실시간 송출
        DeviceStateEntity state = DeviceStateEntity.builder()
                .deviceId(deviceId)
                .level(transition.level())
                .rawLevel(risk.level())
                .candidateSince(transition.candidateSince())
                .riseCmPerMin(riseCmPerMin)
                .contributorsJson(writeJson(risk.contributors()))
                .bumpsJson(writeJson(risk.bumps()))
                .updatedAt(Instant.now())
                .build();
        stateRepo.save(state);

        realtime.publishState(deviceId, stateView(state));
        realtime.publishGoldenTime(deviceId, golden);

        // 6) 전이 처리
        if (!transition.changed()) return;

        eventRepo.save(EventLogEntity.builder()
                .deviceId(deviceId).type("state_transition")
                .fromLevel(prevLevel).toLevel(transition.level())
                .detailJson(writeJson(Map.of("reason", transition.reason(), "levelCm", levelCm,
                        "riseCmPerMin", riseCmPerMin)))
                .ts(Instant.now()).build());

        if (transition.level().rank > prevLevel.rank) {
            runEscalation(deviceId, device, transition.level(), golden);
        } else {
            runDowngrade(deviceId, device, transition.level());
        }
    }

    /** 승격 시 자동 조치. */
    private void runEscalation(String deviceId, DeviceEntity device, RiskLevel level, GoldenTime.Result golden) {
        String ownerUid = device != null ? device.getOwnerUid() : null;

        switch (level) {
            case CAUTION -> notify(ownerUid, "🟡 주의: 물 유입 감지",
                    "체크리스트를 확인하고 배수구를 점검하세요.", "wellhouse://device/" + deviceId, deviceId, level);

            case WARNING -> {
                commandService.issue(deviceId, CommandTarget.WATER_GATE, "system", "auto:WARNING");
                commandService.issue(deviceId, CommandTarget.WINDOW, "system", "auto:WARNING");
                commandService.issue(deviceId, CommandTarget.POWER, "system", "auto:WARNING");
                notify(ownerUid, "🟠 경고: 자동 차단 실행",
                        "물막이판 전개·전원/수도 차단을 실행했습니다. 대피를 준비하세요.",
                        "wellhouse://device/" + deviceId, deviceId, level);
            }

            case DANGER -> {
                if (device != null) {
                    device.setMode("emergency");
                    deviceRepo.save(device);
                }
                emergencyService.startAutoReport(deviceId, ownerUid);
                String eta = golden.primary().seconds() != null
                        ? "약 " + Math.round(golden.primary().seconds() / 60.0) + "분 후" : "곧";
                notify(ownerUid, "🔴 위험: 즉시 대피",
                        "위험 수위 도달 예상 " + eta + ". 지금 대피 경로를 확인하세요.",
                        "wellhouse://emergency/" + deviceId, deviceId, level);
            }
            default -> { /* GOOD: 없음 */ }
        }
    }

    /** 강등(수위 하강) 시 알림. */
    private void runDowngrade(String deviceId, DeviceEntity device, RiskLevel level) {
        String ownerUid = device != null ? device.getOwnerUid() : null;
        if (level == RiskLevel.GOOD && device != null && "emergency".equals(device.getMode())) {
            device.setMode("normal");
            deviceRepo.save(device);
        }
        notify(ownerUid, "🟢 수위 하강",
                "위험도가 " + level.label + " 단계로 내려갔습니다.",
                "wellhouse://device/" + deviceId, deviceId, level);
    }

    private void notify(String ownerUid, String title, String body, String deepLink, String deviceId, RiskLevel level) {
        if (ownerUid == null) return;
        fcm.notifyUser(ownerUid, title, body, deepLink,
                Map.of("deviceId", deviceId, "level", String.valueOf(level.rank)));
    }

    /** 현재 골든타임 재계산 (앱 GET 조회용). 샘플 없으면 null. */
    @Transactional(readOnly = true)
    public GoldenTime.Result computeCurrentGoldenTime(String deviceId) {
        WaterSampleEntity sample = waterRepo.findTop1ByDeviceIdOrderByTsDesc(deviceId).orElse(null);
        if (sample == null) return null;
        DeviceStateEntity st = stateRepo.findById(deviceId).orElse(null);
        double rise = st != null && st.getRiseCmPerMin() != null ? st.getRiseCmPerMin() : 0;
        DeviceEntity dev = deviceRepo.findById(deviceId).orElse(null);
        double target = dev != null && dev.getGoldenTargetCm() != null
                ? dev.getGoldenTargetCm() : Thresholds.DEFAULT_GOLDEN_TARGET_CM;
        return GoldenTime.compute(sample.getLevelCm(), rise, target, floorAreaM2(dev));
    }

    /** 기기 소유자의 집 바닥 면적 A(m²). 미상이면 0(=유입 유량 계산 안 함). */
    private double floorAreaM2(DeviceEntity device) {
        if (device == null || device.getOwnerUid() == null) return 0;
        return userRepo.findById(device.getOwnerUid())
                .map(UserEntity::getHomeAreaM2)
                .filter(a -> a != null && a > 0)
                .orElse(0.0);
    }

    private Map<String, Object> stateView(DeviceStateEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", s.getLevel().rank);
        m.put("label", s.getLevel().label);
        m.put("color", s.getLevel().color);
        m.put("raw", s.getRawLevel() != null ? s.getRawLevel().rank : null);
        m.put("riseCmPerMin", s.getRiseCmPerMin());
        m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toEpochMilli() : null);
        return m;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
