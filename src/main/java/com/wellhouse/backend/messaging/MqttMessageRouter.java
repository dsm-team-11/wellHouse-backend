package com.wellhouse.backend.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellhouse.backend.entity.EventLogEntity;
import com.wellhouse.backend.repository.EventLogRepository;
import com.wellhouse.backend.service.CommandService;
import com.wellhouse.backend.service.DeviceService;
import com.wellhouse.backend.service.RiskEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 펌웨어 인바운드 메시지 라우팅 (MQTT 토픽 또는 REST 폴백 공용).
 * 토픽 규약:
 *   devices/{id}/water                  {level_cm, timestamp}
 *   devices/{id}/heartbeat              {rssi, timestamp}
 *   devices/{id}/power                  {powerState, source, timestamp}
 *   devices/{id}/commands/{cmdId}/ack   {result, detail}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageRouter {

    private final RiskEvaluationService riskService;
    private final DeviceService deviceService;
    private final CommandService commandService;
    private final EventLogRepository eventRepo;
    private final ObjectMapper objectMapper;

    public void route(String topic, String payload) {
        try {
            String[] p = topic.split("/");
            if (p.length < 3 || !"devices".equals(p[0])) {
                log.debug("무시된 토픽: {}", topic);
                return;
            }
            String deviceId = p[1];
            String kind = p[2];
            JsonNode json = payload == null || payload.isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(payload);

            switch (kind) {
                case "water" -> {
                    double levelCm = json.path("level_cm").asDouble(0);
                    Instant ts = tsOf(json);
                    riskService.onWaterReading(deviceId, levelCm, ts);
                }
                case "heartbeat" -> {
                    Integer rssi = json.has("rssi") ? json.get("rssi").asInt() : null;
                    deviceService.recordHeartbeat(deviceId, rssi);
                }
                case "power" -> logPower(deviceId, json);
                case "commands" -> {
                    // devices/{id}/commands/{cmdId}/ack
                    if (p.length >= 5 && "ack".equals(p[4])) {
                        commandService.handleAck(deviceId, p[3],
                                json.path("result").asText("unknown"),
                                json.path("detail").asText(null));
                    }
                }
                default -> log.debug("알 수 없는 종류: {}", kind);
            }
        } catch (Exception e) {
            log.error("MQTT 라우팅 실패 topic={} err={}", topic, e.getMessage());
        }
    }

    private void logPower(String deviceId, JsonNode json) {
        eventRepo.save(EventLogEntity.builder()
                .deviceId(deviceId).type("power_change")
                .detailJson(json.toString())
                .ts(Instant.now()).build());
    }

    private Instant tsOf(JsonNode json) {
        long ms = json.path("timestamp").asLong(0);
        return ms > 0 ? Instant.ofEpochMilli(ms) : Instant.now();
    }
}
