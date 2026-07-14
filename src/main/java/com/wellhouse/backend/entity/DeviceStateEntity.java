package com.wellhouse.backend.entity;

import com.wellhouse.backend.domain.risk.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 기기의 현재 확정 상태 (1기기 1행). */
@Entity
@Table(name = "device_state")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceStateEntity {

    @Id
    private String deviceId;

    @Enumerated(EnumType.STRING)
    private RiskLevel level;      // 확정 단계

    @Enumerated(EnumType.STRING)
    private RiskLevel rawLevel;   // 히스테리시스 적용 전 원시 단계

    private Long candidateSince;  // 하향 후보 유지 시작(ms), null이면 없음
    private Double riseCmPerMin;

    @Column(length = 2000)
    private String contributorsJson;

    @Column(length = 1000)
    private String bumpsJson;

    private Instant updatedAt;

    /** 앱 편의: level 라벨/색상을 REST 응답에도 포함 (JPA 저장 대상 아님). */
    @jakarta.persistence.Transient
    public String getLabel() {
        return level != null ? level.label : null;
    }

    @jakarta.persistence.Transient
    public String getColor() {
        return level != null ? level.color : null;
    }
}
