package com.wellhouse.backend.service.weather;

import com.wellhouse.backend.domain.risk.Advisory;

/**
 * 한 격자의 기상 스냅샷.
 *
 * @param rainMmH        현재 1시간 강수량(mm) — 초단기실황 RN1
 * @param cumulative3hMm 최근 3시간 누적 강수량(mm) 근사
 * @param advisory       기상특보 단계
 * @param forecastMmH    다음 시각 예상 강수량(mm) — 초단기예보 RN1
 */
public record WeatherSnapshot(double rainMmH, double cumulative3hMm, Advisory advisory, double forecastMmH) {

    /** KMA 미연동/실패 시 안전한 기본값. */
    public static final WeatherSnapshot EMPTY = new WeatherSnapshot(0, 0, Advisory.NONE, 0);
}
