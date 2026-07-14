package com.wellhouse.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 원격/자동 명령과 실행 결과(ACK). */
@Entity
@Table(name = "commands", indexes = {
        @Index(name = "idx_cmd_device", columnList = "deviceId, createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommandEntity {

    @Id
    private String id; // UUID

    private String deviceId;

    @Enumerated(EnumType.STRING)
    private CommandTarget target;

    @Enumerated(EnumType.STRING)
    private CommandStatus status;

    private String issuedBy; // uid 또는 "system"
    private String reason;
    private Instant createdAt;

    // ACK
    private String ackResult; // ok | fail
    private String ackDetail;
    private Instant ackAt;
}
