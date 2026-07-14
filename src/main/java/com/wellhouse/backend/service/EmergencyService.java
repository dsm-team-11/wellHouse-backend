package com.wellhouse.backend.service;

import com.wellhouse.backend.messaging.AppRealtimePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 위험(EMERGENCY) 단계 자동 조치.
 *  - 자동 신고: 서버는 30초 카운트다운만 실시간 송출. 실제 신고는 앱이 수행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyService {

    private static final int REPORT_COUNTDOWN_SEC = 30;

    private final FcmService fcm;
    private final AppRealtimePublisher realtime;

    /** 자동 신고 카운트다운 시작. */
    public void startAutoReport(String deviceId, String ownerUid) {
        Map<String, Object> report = Map.of(
                "status", "counting",
                "countdownSec", REPORT_COUNTDOWN_SEC,
                "startedAt", System.currentTimeMillis());
        realtime.publishEmergency(deviceId, report);
        if (ownerUid != null) {
            fcm.notifyUser(ownerUid, "🚨 긴급: 자동 신고 대기",
                    REPORT_COUNTDOWN_SEC + "초 후 자동으로 신고가 진행됩니다. 취소하려면 앱을 확인하세요.",
                    "wellhouse://emergency/" + deviceId,
                    Map.of("deviceId", deviceId, "type", "auto_report"));
        }
        log.warn("auto-report countdown started device={}", deviceId);
    }
}
