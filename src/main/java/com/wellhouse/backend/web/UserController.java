package com.wellhouse.backend.web;

import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.EmergencyContact;
import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.NotificationRepository;
import com.wellhouse.backend.repository.UserRepository;
import com.wellhouse.backend.service.weather.AddressGrid;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 내 정보/집 정보/FCM 토큰. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;
    private final DeviceRepository deviceRepo;
    private final NotificationRepository notificationRepo;

    public record HomeReq(Double homeAreaM2, Boolean isSemiBasement,
                          Double homeLat, Double homeLng, Integer windowCount,
                          String address,
                          Integer floorHeightCm, String entrance, Boolean barrierInstalled,
                          Integer floodHistoryLevel, String emergencyName, String emergencyPhone,
                          java.util.List<String> windowSizes) {}
    public record FcmTokenReq(@NotBlank String token) {}
    public record ContactReq(String name, String relation, String phone) {}

    @GetMapping
    public UserEntity me(Authentication auth) {
        return load(auth);
    }

    @PutMapping("/home")
    public UserEntity updateHome(Authentication auth, @RequestBody HomeReq req) {
        UserEntity u = load(auth);
        u.setHomeAreaM2(req.homeAreaM2());
        u.setIsSemiBasement(req.isSemiBasement());
        u.setHomeLat(req.homeLat());
        u.setHomeLng(req.homeLng());
        u.setWindowCount(req.windowCount());
        // 주소가 오면 기상청 격자를 함께 계산해 저장(날씨 조회에 사용).
        if (req.address() != null && !req.address().isBlank()) {
            AddressGrid.Location loc = AddressGrid.fromAddress(req.address());
            u.setAddress(req.address().trim());
            u.setGridNx(loc.cell().nx());
            u.setGridNy(loc.cell().ny());
        }
        // 나머지 집 세부정보/비상연락처/창문크기 — 값이 온 것만 갱신(부분 저장 시 기존 값 보존).
        if (req.floorHeightCm() != null) u.setFloorHeightCm(req.floorHeightCm());
        if (req.entrance() != null) u.setEntrance(req.entrance());
        if (req.barrierInstalled() != null) u.setBarrierInstalled(req.barrierInstalled());
        if (req.floodHistoryLevel() != null) u.setFloodHistoryLevel(req.floodHistoryLevel());
        if (req.emergencyName() != null) u.setEmergencyName(req.emergencyName());
        if (req.emergencyPhone() != null) u.setEmergencyPhone(req.emergencyPhone());
        if (req.windowSizes() != null) {
            u.getWindowSizes().clear();
            u.getWindowSizes().addAll(req.windowSizes());
        }
        return userRepo.save(u);
    }

    /** 회원 탈퇴: 소유 기기 페어링 해제 + 알림 삭제 + 계정 삭제(복구 불가). */
    @DeleteMapping
    @Transactional
    public void deleteMe(Authentication auth) {
        String uid = auth.getName();
        UserEntity u = load(auth);
        // 기기는 물리/공유 자원이라 삭제하지 않고 소유만 해제(다시 페어링 가능).
        for (DeviceEntity d : deviceRepo.findByOwnerUid(uid)) {
            d.setOwnerUid(null);
            deviceRepo.save(d);
        }
        notificationRepo.deleteByUid(uid);
        userRepo.delete(u);
    }

    /** 내 비상 연락망 목록 조회. */
    @GetMapping("/emergency-contacts")
    public java.util.List<EmergencyContact> emergencyContacts(Authentication auth) {
        return load(auth).getEmergencyContacts();
    }

    /** 비상 연락망 목록 전체 교체(앱이 추가/수정/삭제 후 전체 목록을 보낸다). */
    @PutMapping("/emergency-contacts")
    public java.util.List<EmergencyContact> updateEmergencyContacts(
            Authentication auth, @RequestBody java.util.List<ContactReq> body) {
        UserEntity u = load(auth);
        u.getEmergencyContacts().clear();
        if (body != null) {
            for (ContactReq c : body) {
                u.getEmergencyContacts().add(new EmergencyContact(c.name(), c.relation(), c.phone()));
            }
        }
        userRepo.save(u);
        return u.getEmergencyContacts();
    }

    @PostMapping("/fcm-token")
    public void registerToken(Authentication auth, @Valid @RequestBody FcmTokenReq req) {
        UserEntity u = load(auth);
        u.getFcmTokens().add(req.token());
        userRepo.save(u);
    }

    @DeleteMapping("/fcm-token")
    public void removeToken(Authentication auth, @Valid @RequestBody FcmTokenReq req) {
        UserEntity u = load(auth);
        u.getFcmTokens().remove(req.token());
        userRepo.save(u);
    }

    private UserEntity load(Authentication auth) {
        return userRepo.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 없음"));
    }
}
