package com.wellhouse.backend.web;

import com.wellhouse.backend.service.AuthService;
import com.wellhouse.backend.service.PasswordResetService;
import com.wellhouse.backend.web.dto.TokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 회원가입/로그인 + 비밀번호 재설정(이메일 인증코드). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public record RegisterRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    /** email=계정 이메일(&lt;아이디&gt;@wellhouse.app), contactEmail=인증코드를 받을 실제 이메일. */
    public record ForgotPasswordRequest(@Email @NotBlank String email, @Email @NotBlank String contactEmail) {}
    public record ResetPasswordRequest(@Email @NotBlank String email, @NotBlank String code, @NotBlank String newPassword) {}

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req.email(), req.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    /** 인증코드 발송. 계정 존재 여부와 무관하게 200(정보 노출 방지). */
    @PostMapping("/password/forgot")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestCode(req.email(), req.contactEmail());
    }

    /** 인증코드 검증 후 비밀번호 변경. 코드 불일치/만료 시 400. */
    @PostMapping("/password/reset")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.reset(req.email(), req.code(), req.newPassword());
    }
}
