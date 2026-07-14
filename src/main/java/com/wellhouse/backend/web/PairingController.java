package com.wellhouse.backend.web;

import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.security.JwtService;
import com.wellhouse.backend.service.DeviceService;
import com.wellhouse.backend.web.dto.PairResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 기기 페어링 (앱). 성공 시 펌웨어용 커스텀 토큰 발급. */
@RestController
@RequestMapping("/api/pair")
@RequiredArgsConstructor
public class PairingController {

    private final DeviceService deviceService;
    private final JwtService jwtService;

    public record PairRequest(@NotBlank String deviceId, @NotBlank String pairingCode) {}

    @PostMapping
    public PairResponse pair(Authentication auth, @Valid @RequestBody PairRequest req) {
        DeviceEntity device = deviceService.pair(auth.getName(), req.deviceId(), req.pairingCode());
        String deviceToken = jwtService.issueDeviceToken(device.getDeviceId());
        return new PairResponse(true, device.getDeviceId(), deviceToken);
    }
}
