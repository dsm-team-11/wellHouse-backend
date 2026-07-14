package com.wellhouse.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 기기 메타/소유권. */
@Entity
@Table(name = "devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceEntity {

    @Id
    private String deviceId;

    private String ownerUid;
    private String region;
    private Double lat;
    private Double lng;

    private boolean online;
    private String mode;            // normal | emergency
    private Double goldenTargetCm;  // 사용자 지정 목표 수위(cm), null이면 기본 10

    // 페어링
    private String pairingCode;
    private Instant pairingExpiresAt;
    private Instant pairedAt;

    // 하트비트
    private Instant lastHeartbeatAt;
    private Integer rssi;
}
