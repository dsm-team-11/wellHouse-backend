package com.wellhouse.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 앱 알림함 항목. */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_uid", columnList = "uid, createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;
    private String title;

    @Column(length = 1000)
    private String body;

    private String deepLink;

    @Column(length = 2000)
    private String dataJson;

    @Column(name = "is_read")
    private boolean read;

    private Instant createdAt;
}
