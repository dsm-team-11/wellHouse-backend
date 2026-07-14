package com.wellhouse.backend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellhouse.backend.entity.CommandEntity;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** DeviceCommander 의 MQTT 구현 (브로커 활성 시). */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wellhouse.mqtt", name = "enabled", havingValue = "true")
public class MqttDeviceCommander implements DeviceCommander {

    private final MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttDeviceCommander(MqttClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendCommand(String deviceId, CommandEntity c) {
        publish("devices/" + deviceId + "/commands", Map.of(
                "cmdId", c.getId(),
                "target", c.getTarget().name(),
                "ts", c.getCreatedAt().toEpochMilli(),
                "issuedBy", c.getIssuedBy() == null ? "system" : c.getIssuedBy(),
                "reason", c.getReason() == null ? "" : c.getReason()));
    }

    @Override
    public void sendWakeup(String deviceId, double rainMmH, String region) {
        publish("devices/" + deviceId + "/control/wakeup", Map.of(
                "command", "wakeup",
                "rainfall_mm_h", rainMmH,
                "region", region,
                "timestamp", System.currentTimeMillis()));
    }

    private void publish(String topic, Object payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            MqttMessage msg = new MqttMessage(body);
            msg.setQos(1);
            client.publish(topic, msg);
        } catch (Exception e) {
            log.error("MQTT 발행 실패 topic={} err={}", topic, e.getMessage());
        }
    }
}
