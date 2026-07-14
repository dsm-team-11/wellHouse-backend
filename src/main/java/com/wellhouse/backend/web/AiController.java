package com.wellhouse.backend.web;

import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.DeviceStateEntity;
import com.wellhouse.backend.entity.EventLogEntity;
import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.DeviceStateRepository;
import com.wellhouse.backend.repository.EventLogRepository;
import com.wellhouse.backend.repository.UserRepository;
import com.wellhouse.backend.service.WeatherService;
import com.wellhouse.backend.service.ai.AiService;
import com.wellhouse.backend.service.weather.AddressGrid;
import com.wellhouse.backend.service.weather.WeatherSnapshot;
import com.wellhouse.backend.web.dto.AiDtos.ChecklistDto;
import com.wellhouse.backend.web.dto.AiDtos.HomeMessageDto;
import com.wellhouse.backend.web.dto.AiDtos.ReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * GPT 기반 AI 기능(홈 문구 · 체크리스트 · 사후 리포트 · 개선방안).
 * 모두 로그인 사용자 컨텍스트(주소·집정보·기기 이벤트)를 조합해 생성한다.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService ai;
    private final WeatherService weatherService;
    private final UserRepository userRepo;
    private final DeviceRepository deviceRepo;
    private final DeviceStateRepository stateRepo;
    private final EventLogRepository eventRepo;

    /** 홈 화면 2줄 맞춤 문구. */
    @GetMapping("/home-message")
    public HomeMessageDto homeMessage(Authentication auth) {
        UserEntity u = user(auth);
        WeatherSnapshot w = weatherService.fetchByAddress(u.getAddress());
        String region = AddressGrid.fromAddress(u.getAddress()).label();
        String name = nameOf(u);
        String riskLabel = myRiskLabel(auth.getName());

        String[] lines = ai.homeMessage(name, region, w, riskLabel);
        return new HomeMessageDto(lines[0], lines.length > 1 ? lines[1] : "", ai.enabled());
    }

    /** 상황 맞춤 체크리스트(우선순위). */
    @GetMapping("/checklist")
    public ChecklistDto checklist(Authentication auth) {
        UserEntity u = user(auth);
        WeatherSnapshot w = weatherService.fetchByAddress(u.getAddress());
        String riskLabel = myRiskLabel(auth.getName());
        List<String> items = ai.checklist(u.getIsSemiBasement(), u.getHomeAreaM2(), u.getWindowCount(), w, riskLabel);
        return new ChecklistDto(items, ai.enabled());
    }

    /** 침수 후 사후 리포트. */
    @GetMapping("/report/{deviceId}")
    public ReportDto report(Authentication auth, @PathVariable String deviceId) {
        assertOwner(auth, deviceId);
        String markdown = ai.report(chronologicalEvents(deviceId));
        return new ReportDto(markdown, ai.enabled());
    }

    /** 사고 기반 AI 개선 방안. */
    @GetMapping("/report/{deviceId}/improvements")
    public ReportDto improvements(Authentication auth, @PathVariable String deviceId) {
        assertOwner(auth, deviceId);
        UserEntity u = user(auth);
        String markdown = ai.improvements(u.getIsSemiBasement(), u.getHomeAreaM2(), u.getWindowCount(),
                chronologicalEvents(deviceId));
        return new ReportDto(markdown, ai.enabled());
    }

    // ─────────────────────────────── 헬퍼 ───────────────────────────────

    private UserEntity user(Authentication auth) {
        return userRepo.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 없음"));
    }

    /** 이메일 로컬파트를 이름 대용으로 사용(앱은 아이디 기반). */
    private String nameOf(UserEntity u) {
        if (u.getEmail() == null) return "고객";
        int at = u.getEmail().indexOf('@');
        return at > 0 ? u.getEmail().substring(0, at) : u.getEmail();
    }

    /** 사용자의 첫 기기 현재 위험단계 라벨. 없으면 "양호". */
    private String myRiskLabel(String uid) {
        List<DeviceEntity> devices = deviceRepo.findByOwnerUid(uid);
        if (devices.isEmpty()) return "양호";
        DeviceStateEntity st = stateRepo.findById(devices.get(0).getDeviceId()).orElse(null);
        return st != null && st.getLevel() != null ? st.getLevel().label : "양호";
    }

    /** 최근 이벤트를 시간 오름차순으로. */
    private List<EventLogEntity> chronologicalEvents(String deviceId) {
        List<EventLogEntity> events = new ArrayList<>(eventRepo.findTop50ByDeviceIdOrderByTsDesc(deviceId));
        java.util.Collections.reverse(events);
        return events;
    }

    private void assertOwner(Authentication auth, String deviceId) {
        DeviceEntity d = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록되지 않은 기기"));
        if (!auth.getName().equals(d.getOwnerUid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 기기의 소유자가 아닙니다.");
        }
    }
}
