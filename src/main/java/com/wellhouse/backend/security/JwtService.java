package com.wellhouse.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** JWT 발급/검증. 사용자(USER)와 기기(DEVICE) 두 역할. */
@Service
public class JwtService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_DEVICE = "DEVICE";

    private final SecretKey key;
    private final long accessTtlMinutes;
    private final long deviceTtlDays;

    public JwtService(@Value("${wellhouse.jwt.secret}") String secret,
                      @Value("${wellhouse.jwt.access-token-ttl-minutes:120}") long accessTtlMinutes,
                      @Value("${wellhouse.jwt.device-token-ttl-days:3650}") long deviceTtlDays) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtlMinutes = accessTtlMinutes;
        this.deviceTtlDays = deviceTtlDays;
    }

    public String issueUserToken(String uid) {
        return build(uid, ROLE_USER, Duration.ofMinutes(accessTtlMinutes));
    }

    /** 페어링 시 펌웨어에 발급하는 장기 토큰 (subject=deviceId). */
    public String issueDeviceToken(String deviceId) {
        return build(deviceId, ROLE_DEVICE, Duration.ofDays(deviceTtlDays));
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    private String build(String subject, String role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
