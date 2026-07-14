package com.wellhouse.backend.entity;

import com.wellhouse.backend.domain.risk.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 상태전이/명령ACK/전원변화 등 이벤트 로그. */
@Entity
@Table(name = "event_log", indexes = {
        @Index(name = "idx_event_device_ts", columnList = "deviceId, ts")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private String type; // state_transition | command_ack | power_change | error ...

    @Enumerated(EnumType.STRING)
    private RiskLevel fromLevel;

    @Enumerated(EnumType.STRING)
    private RiskLevel toLevel;

    @Column(length = 4000)
    private String detailJson;

    private Instant ts;
}
