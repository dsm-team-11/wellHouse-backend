package com.wellhouse.backend.web;

import com.wellhouse.backend.entity.NotificationEntity;
import com.wellhouse.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 앱 알림함. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notifRepo;

    @GetMapping
    public List<NotificationEntity> list(Authentication auth) {
        return notifRepo.findTop50ByUidOrderByCreatedAtDesc(auth.getName());
    }

    @PostMapping("/{id}/read")
    public void markRead(Authentication auth, @PathVariable Long id) {
        NotificationEntity n = notifRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!n.getUid().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 알림이 아닙니다.");
        }
        n.setRead(true);
        notifRepo.save(n);
    }
}
