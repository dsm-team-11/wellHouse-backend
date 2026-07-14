package com.wellhouse.backend.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** AppRealtimePublisher 의 WebSocket(STOMP) 구현. */
@Component
@RequiredArgsConstructor
public class StompRealtimePublisher implements AppRealtimePublisher {

    private final SimpMessagingTemplate template;

    @Override
    public void publishState(String deviceId, Object stateView) {
        template.convertAndSend("/topic/devices/" + deviceId + "/state", stateView);
    }

    @Override
    public void publishGoldenTime(String deviceId, Object goldenView) {
        template.convertAndSend("/topic/devices/" + deviceId + "/goldenTime", goldenView);
    }

    @Override
    public void publishEmergency(String deviceId, Object report) {
        template.convertAndSend("/topic/devices/" + deviceId + "/emergency", report);
    }
}
