package com.wellhouse.backend.messaging;

import com.wellhouse.backend.entity.CommandEntity;

/**
 * 펌웨어로 명령을 내보내는 아웃바운드 포트. 구현은 MQTT(기본) 또는 No-op(브로커 없음).
 */
public interface DeviceCommander {
    /** devices/{deviceId}/commands 로 명령 발행. */
    void sendCommand(String deviceId, CommandEntity command);

    /** devices/{deviceId}/control/wakeup 으로 절전 해제 발행. */
    void sendWakeup(String deviceId, double rainMmH, String region);
}
