package com.wellhouse.backend.service;

import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호 재설정 흐름: 인증코드 발급 → 사용자가 입력한 이메일로 발송 → 코드 검증 후 비밀번호 변경.
 *
 * 인증코드는 단기(10분)라서 DB 대신 메모리에 보관한다(계정 email 기준). 서버 재시작/다중 인스턴스
 * 환경에선 코드가 유실될 수 있으나, 재요청으로 복구되므로 이 규모에선 충분하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    /** 계정 email(=&lt;아이디&gt;@wellhouse.app) → 발급된 코드. */
    private final Map<String, Code> codes = new ConcurrentHashMap<>();

    private record Code(String value, Instant expiresAt, int attempts) {}

    /**
     * 인증코드 발급 + {@code contactEmail} 로 발송.
     * 계정 존재 여부를 노출하지 않기 위해, 계정이 없어도 예외 없이 조용히 반환한다.
     */
    public void requestCode(String accountEmail, String contactEmail) {
        UserEntity user = userRepo.findByEmail(accountEmail).orElse(null);
        if (user == null) {
            log.info("[RESET] 존재하지 않는 계정 요청 무시: {}", accountEmail);
            return;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.put(accountEmail, new Code(code, Instant.now().plus(CODE_TTL), 0));
        mailService.send(
                contactEmail,
                "[웰하우스] 비밀번호 재설정 인증코드",
                "인증코드: " + code + "\n\n10분 안에 앱에 입력해주세요.\n본인이 요청하지 않았다면 이 메일을 무시하세요."
        );
    }

    /** 코드 검증 후 새 비밀번호로 변경. 코드 불일치/만료/시도초과 시 400. */
    @Transactional
    public void reset(String accountEmail, String inputCode, String newPassword) {
        Code code = codes.get(accountEmail);
        if (code == null || Instant.now().isAfter(code.expiresAt())) {
            codes.remove(accountEmail);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증코드가 만료되었어요. 다시 요청해주세요.");
        }
        if (code.attempts() >= MAX_ATTEMPTS) {
            codes.remove(accountEmail);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시도 횟수를 초과했어요. 다시 요청해주세요.");
        }
        if (!code.value().equals(inputCode)) {
            codes.put(accountEmail, new Code(code.value(), code.expiresAt(), code.attempts() + 1));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증코드가 올바르지 않아요.");
        }
        UserEntity user = userRepo.findByEmail(accountEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "계정을 찾을 수 없어요."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        codes.remove(accountEmail);
        log.info("[RESET] 비밀번호 변경 완료: {}", accountEmail);
    }
}
