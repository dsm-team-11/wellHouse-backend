package com.wellhouse.backend.web;

import com.wellhouse.backend.domain.risk.RiskLevel;
import com.wellhouse.backend.entity.DeviceStateEntity;
import com.wellhouse.backend.repository.DeviceStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * 개발 전용 도구. dev 프로파일에서만 빈으로 등록된다(운영엔 존재하지 않음).
 * 앱 데모: 기기 상태를 즉시 바꿔 앱이 폴링으로 부드럽게 전환되는지 확인.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final DeviceStateRepository stateRepo;

    /**
     * 기기 상태를 지정 위험단계로 설정.
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
        return stateRepo.save(st);
    }
}
