package com.wellhouse.backend.domain.risk;

import java.util.ArrayList;
import java.util.List;

/**
 * 최종 위험도 결정 엔진.
 *
 *   최종 = MAX(수위, 강수, 특보floor, 상승속도, 예보)
 *          그다음 +1 상향(bump): 상승속도≥2cm/min, 최근3h누적≥60mm
 *          마지막에 DANGER로 클램프
 *
 * 순수 함수 모음. docs/RISK_ENGINE.md 참고.
 */
public final class RiskEngine {
    private RiskEngine() {}

    /** 수위 단계. 3~10cm 구간은 지속 상승(≥0.5cm/min)이면 경고로 승격. */
    public static RiskLevel waterLevelStage(double levelCm, double riseCmPerMin) {
        if (levelCm >= Thresholds.WATER_DANGER_CM) return RiskLevel.DANGER;
        if (levelCm >= Thresholds.WATER_WARNING_CM) {
            return riseCmPerMin >= Thresholds.SUSTAINED_RISE
                    ? RiskLevel.WARNING
                    : RiskLevel.CAUTION;
        }
        if (levelCm >= Thresholds.WATER_CAUTION_CM) return RiskLevel.CAUTION;
        return RiskLevel.GOOD;
    }

    /** 시간당 강수량 단계. */
    public static RiskLevel rainStage(double mmPerH) {
        if (mmPerH >= Thresholds.RAIN_DANGER) return RiskLevel.DANGER;
        if (mmPerH >= Thresholds.RAIN_WARNING) return RiskLevel.WARNING;
        if (mmPerH >= Thresholds.RAIN_CAUTION) return RiskLevel.CAUTION;
        return RiskLevel.GOOD;
    }

    /** 기상특보 floor (최소 보장 단계). */
    public static RiskLevel advisoryFloor(Advisory advisory) {
        if (advisory == Advisory.WARNING) return RiskLevel.WARNING;
        if (advisory == Advisory.WATCH) return RiskLevel.CAUTION;
        return RiskLevel.GOOD;
    }

    /** 상승속도 자체 floor. ≤0.5 GOOD, 0.5~2 CAUTION, ≥2 WARNING(+별도 bump). */
    public static RiskLevel riseRateStage(double riseCmPerMin) {
        if (riseCmPerMin >= Thresholds.RISE_BUMP) return RiskLevel.WARNING;
        if (riseCmPerMin >= Thresholds.RISE_CAUTION_FLOOR) return RiskLevel.CAUTION;
        return RiskLevel.GOOD;
    }

    /** 예보 선행 floor. */
    public static RiskLevel forecastFloor(double forecastMmPerH) {
        if (forecastMmPerH >= Thresholds.FORECAST_WARNING) return RiskLevel.WARNING;
        if (forecastMmPerH >= Thresholds.FORECAST_CAUTION) return RiskLevel.CAUTION;
        return RiskLevel.GOOD;
    }

    /** 최종 위험도 계산. */
    public static RiskResult compute(RiskInputs in) {
        RiskResult.Contributors c = new RiskResult.Contributors(
                waterLevelStage(in.levelCm(), in.riseCmPerMin()),
                rainStage(in.rainMmPerH()),
                advisoryFloor(in.advisory()),
                riseRateStage(in.riseCmPerMin()),
                forecastFloor(in.forecastMmPerH())
        );

        RiskLevel level = c.water();
        level = RiskLevel.max(level, c.rain());
        level = RiskLevel.max(level, c.advisory());
        level = RiskLevel.max(level, c.riseRate());
        level = RiskLevel.max(level, c.forecast());

        List<String> bumps = new ArrayList<>();
        int rank = level.rank;
        if (in.riseCmPerMin() >= Thresholds.RISE_BUMP) {
            rank += 1;
            bumps.add("riseRate>=2cm/min");
        }
        if (in.cumulativeRainMm() >= Thresholds.CUM_BUMP_MM) {
            rank += 1;
            bumps.add("cumulative3h>=" + (int) Thresholds.CUM_BUMP_MM + "mm");
        }

        return new RiskResult(RiskLevel.fromRank(rank), c, bumps, c.advisory());
    }
}
