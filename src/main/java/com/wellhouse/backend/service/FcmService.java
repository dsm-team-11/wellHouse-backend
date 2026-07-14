package com.wellhouse.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.wellhouse.backend.entity.NotificationEntity;
import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.NotificationRepository;
import com.wellhouse.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.time.Instant;
import java.util.*;

/**
 * 푸시 알림 + 알림함 기록. Firebase는 "푸시 전용"으로만 사용.
 * fcm.enabled=false 이거나 자격증명이 없으면 알림함에만 저장하고 발송은 skip.
 */
@Slf4j
@Service
public class FcmService {

    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String credentialsPath;

    private FirebaseApp firebaseApp;

    public FcmService(NotificationRepository notifRepo,
                      UserRepository userRepo,
                      ObjectMapper objectMapper,
                      @Value("${wellhouse.fcm.enabled:false}") boolean enabled,
                      @Value("${wellhouse.fcm.credentials-path:}") String credentialsPath) {
        this.notifRepo = notifRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
    }

    @PostConstruct
    void init() {
        if (!enabled || credentialsPath == null || credentialsPath.isBlank()) {
            log.info("FCM 비활성화(또는 자격증명 없음) — 푸시는 로그만 남김");
            return;
        }
        try (FileInputStream in = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            this.firebaseApp = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("FCM 초기화 완료");
        } catch (Exception e) {
            log.error("FCM 초기화 실패 — 푸시 비활성화: {}", e.getMessage());
            this.firebaseApp = null;
        }
    }

    /** 사용자에게 푸시 + 알림함 기록. */
    public void notifyUser(String uid, String title, String body, String deepLink, Map<String, String> data) {
        // 1) 알림함 저장
        String dataJson = writeJson(data);
        notifRepo.save(NotificationEntity.builder()
                .uid(uid).title(title).body(body).deepLink(deepLink)
                .dataJson(dataJson).read(false).createdAt(Instant.now())
                .build());

        // 2) 발송
        if (firebaseApp == null) {
            log.debug("[FCM skip] to={} title={}", uid, title);
            return;
        }
        UserEntity user = userRepo.findById(uid).orElse(null);
        if (user == null || user.getFcmTokens().isEmpty()) return;

        List<String> tokens = new ArrayList<>(user.getFcmTokens());
        Map<String, String> payload = new HashMap<>(data != null ? data : Map.of());
        payload.put("deepLink", deepLink == null ? "" : deepLink);

        MulticastMessage msg = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(payload)
                .build();
        try {
            BatchResponse res = FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticast(msg);
            pruneInvalid(user, tokens, res);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 실패 uid={}: {}", uid, e.getMessage());
        }
    }

    private void pruneInvalid(UserEntity user, List<String> tokens, BatchResponse res) {
        List<SendResponse> responses = res.getResponses();
        Set<String> invalid = new HashSet<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (!r.isSuccessful() && r.getException() != null) {
                MessagingErrorCode code = r.getException().getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    invalid.add(tokens.get(i));
                }
            }
        }
        if (!invalid.isEmpty()) {
            user.getFcmTokens().removeAll(invalid);
            userRepo.save(user);
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? Map.of() : o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
