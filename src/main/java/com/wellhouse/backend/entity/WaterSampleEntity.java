package com.wellhouse.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 수위 원본 샘플 스트림. 24h 후 정리. */
@Entity
@Table(name = "water_sample", indexes = {
        @Index(name = "idx_water_device_ts", columnList = "deviceId, ts")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WaterSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private double levelCm;
    private Instant ts;
}
