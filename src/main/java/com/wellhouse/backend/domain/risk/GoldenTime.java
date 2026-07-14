package com.wellhouse.backend.domain.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 골든타임(목표 수위 도달까지 남은 시간) 계산.
 *   v = 상승속도(m/s),  T = (h_target - h_current) / v
 * 상승하지 않으면(v≤0) 도달 불가.
 */
public final class GoldenTime {
    private GoldenTime() {}

    private static final double CM_PER_MIN_TO_M_PER_S = 1.0 / 100.0 / 60.0;

    /** 목표(주요) 도달 시간. seconds=null 이면 도달 불가. */
    public record Target(int targetCm, Integer seconds, boolean reachable) {}

    /** 인체 기준 부위별 도달 시간. */
    public record BodyEta(String part, double heightM, Integer seconds, boolean reachable) {}

    public record Result(double riseSpeedMPerS, Target primary, List<BodyEta> bodyEta) {}

    /**
     * 목표 수위까지 남은 시간(초). 이미 도달=0, 상승 안 함=Infinity.
     */
    public static double secondsToTarget(double hCurrentM, double hTargetM, double vMPerS) {
        double remaining = hTargetM - hCurrentM;
        if (remaining <= 0) return 0;
        if (vMPerS <= 0) return Double.POSITIVE_INFINITY;
        return remaining / vMPerS;
    }

    public static Result compute(double levelCm, double riseCmPerMin, double targetCm) {
        double hCurrentM = levelCm / 100.0;
        double vMPerS = riseCmPerMin * CM_PER_MIN_TO_M_PER_S;

        double primarySec = secondsToTarget(hCurrentM, targetCm / 100.0, vMPerS);
        Target primary = new Target(
                (int) Math.round(targetCm),
                toSeconds(primarySec),
                Double.isFinite(primarySec)
        );

        List<BodyEta> bodyEta = new ArrayList<>();
        for (Map.Entry<String, Double> e : Thresholds.BODY_HEIGHT_M_ORDERED.entrySet()) {
            double sec = secondsToTarget(hCurrentM, e.getValue(), vMPerS);
            bodyEta.add(new BodyEta(e.getKey(), e.getValue(), toSeconds(sec), Double.isFinite(sec)));
        }
        return new Result(vMPerS, primary, bodyEta);
    }

    private static Integer toSeconds(double sec) {
        return Double.isFinite(sec) ? (int) Math.round(sec) : null;
    }
}
