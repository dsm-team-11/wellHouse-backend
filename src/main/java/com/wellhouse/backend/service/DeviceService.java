package com.wellhouse.backend.service;

import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/** 기기 하트비트/오프라인 감지/페어링. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final long OFFLINE_MS = 60_000;

    private final DeviceRepository deviceRepo;
    private final FcmService fcm;

    /** 하트비트 기록. 신규 기기면 생성. */
    @Transactional
    public void recordHeartbeat(String deviceId, Integer rssi) {
        DeviceEntity d = deviceRepo.findById(deviceId)
                .orElseGet(() -> DeviceEntity.builder().deviceId(deviceId).build());
        d.setLastHeartbeatAt(Instant.now());
        d.setRssi(rssi);
        d.setOnline(true);
        deviceRepo.save(d);
    }

    /** 60초 무응답 기기 오프라인 표시 + 소유자 알림 (스케줄러 호출). */
    @Transactional
    public int scanOffline() {
        Instant cutoff = Instant.now().minusMillis(OFFLINE_MS);
        int changed = 0;
        for (DeviceEntity d : deviceRepo.findAll()) {
            boolean offline = d.getLastHeartbeatAt() == null || d.getLastHeartbeatAt().isBefore(cutoff);
            if (offline && d.isOnline()) {
                d.setOnline(false);
                deviceRepo.save(d);
                changed++;
                if (d.getOwnerUid() != null) {
                    fcm.notifyUser(d.getOwnerUid(), "📴 기기 오프라인",
                            "침수 감지 기기가 60초 이상 응답하지 않습니다. 전원/네트워크를 확인하세요.",
                            "wellhouse://device/" + d.getDeviceId(),
                            Map.of("deviceId", d.getDeviceId(), "type", "offline"));
                }
            }
        }
        return changed;
    }

    /**
     * 페어링. 코드 검증 후 소유권 설정. 성공 시 DeviceEntity 반환(토큰 발급은 컨트롤러가 수행).
     */
    @Transactional
    public DeviceEntity pair(String uid, String deviceId, String pairingCode) {
        DeviceEntity d = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록되지 않은 기기입니다."));

        if (d.getOwnerUid() != null && !d.getOwnerUid().equals(uid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 사용자에게 등록된 기기입니다.");
        }
        if (d.getPairingCode() == null || !d.getPairingCode().equals(pairingCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "페어링 코드가 일치하지 않습니다.");
        }
        if (d.getPairingExpiresAt() != null && d.getPairingExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "페어링 코드가 만료되었습니다.");
        }

        d.setOwnerUid(uid);
        d.setPairedAt(Instant.now());
        d.setPairingCode(null); // 1회용 소모
        deviceRepo.save(d);
        log.info("device paired device={} owner={}", deviceId, uid);
        return d;
    }
}
