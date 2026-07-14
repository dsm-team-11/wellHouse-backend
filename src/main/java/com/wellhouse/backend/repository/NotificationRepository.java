package com.wellhouse.backend.repository;

import com.wellhouse.backend.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findTop50ByUidOrderByCreatedAtDesc(String uid);

    /** 회원 탈퇴 시 해당 사용자의 알림 전부 삭제. */
    void deleteByUid(String uid);
}
