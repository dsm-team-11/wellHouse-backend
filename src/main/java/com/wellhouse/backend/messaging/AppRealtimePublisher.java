package com.wellhouse.backend.messaging;

/**
 * 앱으로 실시간 상태를 밀어주는 포트. 구현은 WebSocket(STOMP).
 * 토픽: /topic/devices/{deviceId}/state , /goldenTime , /emergency
 */
public interface AppRealtimePublisher {
    void publishState(String deviceId, Object stateView);
    void publishGoldenTime(String deviceId, Object goldenView);
    void publishEmergency(String deviceId, Object report);
}
