package com.wellhouse.backend.web;

import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 내 정보/집 정보/FCM 토큰. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;

    public record HomeReq(Double homeAreaM2, Boolean isSemiBasement,
                          Double homeLat, Double homeLng, Integer windowCount) {}
    public record FcmTokenReq(@NotBlank String token) {}

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
        return userRepo.save(u);
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
