package com.wellhouse.backend.entity;

import com.wellhouse.backend.domain.risk.Advisory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 지역별 기상 현황 (KMA 폴링 결과). */
@Entity
@Table(name = "weather")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WeatherEntity {

    @Id
    private String region;

    private double rainMmH;
    private double cumulative3hMm;

    @Enumerated(EnumType.STRING)
    private Advisory advisory;

    private double forecastMmH;
    private Instant updatedAt;
}
