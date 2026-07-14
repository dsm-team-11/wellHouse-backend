package com.wellhouse.backend.service;

import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.UserRepository;
import com.wellhouse.backend.security.JwtService;
import com.wellhouse.backend.web.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** 회원가입/로그인 → JWT 발급. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public TokenResponse register(String email, String password, String recoveryEmail) {
        if (userRepo.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }
        String uid = UUID.randomUUID().toString();
        UserEntity user = UserEntity.builder()
                .uid(uid).email(email)
                .recoveryEmail(recoveryEmail == null ? null : recoveryEmail.trim())
                .passwordHash(passwordEncoder.encode(password))
                .build();
        userRepo.save(user);
        return new TokenResponse(uid, jwtService.issueUserToken(uid));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return new TokenResponse(user.getUid(), jwtService.issueUserToken(user.getUid()));
    }
}
