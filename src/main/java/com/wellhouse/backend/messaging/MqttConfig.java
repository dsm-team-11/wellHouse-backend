package com.wellhouse.backend.messaging;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.charset.StandardCharsets;

/**
 * MQTT 연결 (wellhouse.mqtt.enabled=true 일 때만).
 * 서버는 펌웨어 인바운드 토픽을 구독하고, 명령을 아웃바운드로 발행한다(MqttDeviceCommander).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "wellhouse.mqtt", name = "enabled", havingValue = "true")
public class MqttConfig {

    private static final String[] SUB_TOPICS = {
            "devices/+/water",
            "devices/+/heartbeat",
            "devices/+/power",
            "devices/+/commands/+/ack"
    };

    @Value("${wellhouse.mqtt.broker-url}")
    private String brokerUrl;
    @Value("${wellhouse.mqtt.client-id:wellhouse-server}")
    private String clientId;
    @Value("${wellhouse.mqtt.username:}")
    private String username;
    @Value("${wellhouse.mqtt.password:}")
    private String password;

    // router 는 콜백(messageArrived)에서만 쓰이고 빈 생성 시점엔 필요 없다.
    // @Lazy 로 주입해 mqttClient → router → commandService → mqttDeviceCommander → mqttClient
    // 순환 의존성을 끊는다.
    @Bean
    public MqttClient mqttClient(@Lazy MqttMessageRouter router) throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        if (username != null && !username.isBlank()) {
            opts.setUserName(username);
            opts.setPassword(password == null ? new char[0] : password.toCharArray());
        }

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                try {
                    for (String t : SUB_TOPICS) client.subscribe(t, 1);
                    log.info("MQTT 구독 완료 ({} topics) @ {}", SUB_TOPICS.length, serverURI);
                } catch (MqttException e) {
                    log.error("MQTT 구독 실패: {}", e.getMessage());
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT 연결 끊김: {}", cause != null ? cause.getMessage() : "unknown");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                router.route(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // no-op
            }
        });

        try {
            client.connect(opts);
        } catch (MqttException e) {
            log.error("MQTT 초기 연결 실패 (자동 재시도 대기): {}", e.getMessage());
        }
        return client;
    }
}
