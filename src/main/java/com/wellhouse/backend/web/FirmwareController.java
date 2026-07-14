package com.wellhouse.backend.web;

import com.wellhouse.backend.service.CommandService;
import com.wellhouse.backend.service.DeviceService;
import com.wellhouse.backend.service.RiskEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * 펌웨어 REST 폴백 (MQTT 미사용 시). DEVICE 역할 필요, 토큰 subject == deviceId.
 */
@RestController
@RequestMapping("/api/firmware")
@RequiredArgsConstructor
public class FirmwareController {

    private final RiskEvaluationService riskService;
    private final DeviceService deviceService;
    private final CommandService commandService;

    public record WaterReq(double levelCm, Long timestamp) {}
    public record HeartbeatReq(Integer rssi, Long timestamp) {}
    public record AckReq(String result, String detail) {}

    @PostMapping("/{deviceId}/water")
    public void water(Authentication auth, @PathVariable String deviceId, @RequestBody WaterReq req) {
        assertSelf(auth, deviceId);
        riskService.onWaterReading(deviceId, req.levelCm(), tsOf(req.timestamp()));
    }

    @PostMapping("/{deviceId}/heartbeat")
    public void heartbeat(Authentication auth, @PathVariable String deviceId, @RequestBody HeartbeatReq req) {
        assertSelf(auth, deviceId);
        deviceService.recordHeartbeat(deviceId, req.rssi());
    }

    @PostMapping("/{deviceId}/commands/{cmdId}/ack")
    public void ack(Authentication auth, @PathVariable String deviceId,
                    @PathVariable String cmdId, @RequestBody AckReq req) {
        assertSelf(auth, deviceId);
        commandService.handleAck(deviceId, cmdId, req.result(), req.detail());
    }

    private void assertSelf(Authentication auth, String deviceId) {
        if (!auth.getName().equals(deviceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "기기 토큰과 deviceId가 일치하지 않습니다.");
        }
    }

    private Instant tsOf(Long ms) {
        return ms != null && ms > 0 ? Instant.ofEpochMilli(ms) : Instant.now();
    }
}
