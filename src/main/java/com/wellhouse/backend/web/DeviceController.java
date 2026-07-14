package com.wellhouse.backend.web;

import com.wellhouse.backend.domain.risk.GoldenTime;
import com.wellhouse.backend.entity.*;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.DeviceStateRepository;
import com.wellhouse.backend.repository.EventLogRepository;
import com.wellhouse.backend.repository.WaterSampleRepository;
import com.wellhouse.backend.service.CommandService;
import com.wellhouse.backend.service.RiskEvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 앱용 기기 조회/명령. 소유자만 접근. */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepo;
    private final DeviceStateRepository stateRepo;
    private final EventLogRepository eventRepo;
    private final WaterSampleRepository waterSampleRepo;
    private final CommandService commandService;
    private final RiskEvaluationService riskService;

    public record CommandReq(@NotNull CommandTarget target) {}

    @GetMapping
    public List<DeviceEntity> myDevices(Authentication auth) {
        return deviceRepo.findByOwnerUid(auth.getName());
    }

    @GetMapping("/{id}/state")
    public DeviceStateEntity state(Authentication auth, @PathVariable String id) {
        assertOwner(auth, id);
        return stateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상태 없음"));
    }

    @GetMapping("/{id}/goldenTime")
    public GoldenTime.Result goldenTime(Authentication auth, @PathVariable String id) {
        assertOwner(auth, id);
        return riskService.computeCurrentGoldenTime(id);
    }

    @GetMapping("/{id}/events")
    public List<EventLogEntity> events(Authentication auth, @PathVariable String id) {
        assertOwner(auth, id);
        return eventRepo.findTop50ByDeviceIdOrderByTsDesc(id);
    }

    /** 최근 24시간 수위 샘플(시간대별 그래프용). 오래된→최신 순으로 정렬해 반환. */
    @GetMapping("/{id}/water-samples")
    public List<WaterSampleEntity> waterSamples(Authentication auth, @PathVariable String id) {
        assertOwner(auth, id);
        List<WaterSampleEntity> list =
                waterSampleRepo.findByDeviceIdAndTsGreaterThanEqual(id, Instant.now().minus(Duration.ofHours(24)));
        list.sort(java.util.Comparator.comparing(WaterSampleEntity::getTs));
        return list;
    }

    @GetMapping("/{id}/commands")
    public List<CommandEntity> commands(Authentication auth, @PathVariable String id) {
        assertOwner(auth, id);
        return commandService.history(id);
    }

    /** 원격 차단 등 명령 발행 (앱). */
    @PostMapping("/{id}/commands")
    public CommandEntity issue(Authentication auth, @PathVariable String id, @Valid @RequestBody CommandReq req) {
        assertOwner(auth, id);
        return commandService.issue(id, req.target(), auth.getName(), "manual");
    }

    private void assertOwner(Authentication auth, String deviceId) {
        DeviceEntity d = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록되지 않은 기기"));
        if (!auth.getName().equals(d.getOwnerUid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 기기의 소유자가 아닙니다.");
        }
    }
}
