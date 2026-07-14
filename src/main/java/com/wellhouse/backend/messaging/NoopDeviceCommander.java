package com.wellhouse.backend.messaging;

import com.wellhouse.backend.entity.CommandEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MQTT 비활성(브로커 없음) 시 사용하는 No-op 커맨더.
 * 명령은 DB에 저장되고 로그만 남는다(REST 폴링으로 펌웨어가 가져갈 수도 있음).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wellhouse.mqtt", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDeviceCommander implements DeviceCommander {

    @Override
    public void sendCommand(String deviceId, CommandEntity command) {
        log.info("[MQTT off] command device={} target={} (DB 저장됨, 전송 skip)",
                deviceId, command.getTarget());
    }

    @Override
    public void sendWakeup(String deviceId, double rainMmH, String region) {
        log.info("[MQTT off] wakeup device={} rain={}mm/h region={}", deviceId, rainMmH, region);
    }
}
