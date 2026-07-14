package com.wellhouse.backend.web.dto;

import com.wellhouse.backend.domain.risk.Advisory;
import com.wellhouse.backend.domain.risk.Thresholds;
import com.wellhouse.backend.service.weather.WeatherSnapshot;

/**
 * 앱 홈 화면용 기상 요약.
 *
 * @param region      표시 지역명(예: "대전 유성구")
 * @param rainMmH     현재 1시간 강수량(mm)
 * @param forecastMmH 다음 시각 예상 강수량(mm)
 * @param advisory    기상특보(NONE|WATCH|WARNING)
 * @param hazardRank  기상위험 단계 0=양호·1=주의·2=경고·3=위험
 * @param hazardLabel 기상위험 라벨
 * @param live        KMA 실연동 여부(서비스키 존재)
 * @param updatedAt   조회 시각(epoch millis)
 */
public record WeatherDto(
        String region,
        double rainMmH,
        double forecastMmH,
        String advisory,
        int hazardRank,
        String hazardLabel,
        boolean live,
        long updatedAt
) {

    private static final String[] LABELS = {"양호", "주의", "경고", "위험"};

    public static WeatherDto of(String region, WeatherSnapshot w, boolean live, long updatedAt) {
        int rank = hazardRank(w);
        return new WeatherDto(region, w.rainMmH(), w.forecastMmH(),
                w.advisory().name(), rank, LABELS[rank], live, updatedAt);
    }

    /** 현재/예보 강수량과 특보를 종합한 기상위험 단계. (기준값은 Thresholds 재사용) */
    private static int hazardRank(WeatherSnapshot w) {
        double rain = Math.max(w.rainMmH(), w.forecastMmH());
        Advisory adv = w.advisory();
        if (rain >= Thresholds.RAIN_DANGER || adv == Advisory.WARNING) return 3;
        if (rain >= Thresholds.RAIN_WARNING || adv == Advisory.WATCH) return 2;
        if (rain >= Thresholds.RAIN_CAUTION) return 1;
        return 0;
    }
}
