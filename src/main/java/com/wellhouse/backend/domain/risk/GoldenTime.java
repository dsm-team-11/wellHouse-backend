package com.wellhouse.backend.domain.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 골든타임(목표 수위 도달까지 남은 시간) 계산.
 *   v = 상승속도(m/s),  T = (h_target - h_current) / v
 * 상승하지 않으면(v≤0) 도달 불가.
 *
 * 수위 상승 속도는 두 가지로 구할 수 있다.
 *   1) 실측: 센서 수위 변화량(cm/min) 을 m/s 로 환산.
 *   2) 물리 모델: 유입 유량 Q(m³/s) 와 바닥 면적 A(m²) 로부터  v = Q / A.
 * 반대로 실측 상승속도와 면적을 알면 유입 유량을 역산할 수 있다.  Q = v · A.
 */
public final class GoldenTime {
    private GoldenTime() {}

    private static final double CM_PER_MIN_TO_M_PER_S = 1.0 / 100.0 / 60.0;

    /**
     * 유입 유량 기반 수위 상승 속도.  v(h) = Q / A   (m/s)
     *
     * @param inflowM3PerS Q : 유입 유량 (m³/s)
     * @param floorAreaM2  A : 방 바닥 면적 (m²). 0 이하이면 상승 없음(0) 으로 본다.
     */
    public static double riseSpeedFromInflow(double inflowM3PerS, double floorAreaM2) {
        if (floorAreaM2 <= 0) return 0;
        return inflowM3PerS / floorAreaM2;
    }

    /**
     * 실측 상승속도로부터 유입 유량 역산.  Q = v · A   (m³/s)
     *
     * @param riseSpeedMPerS v : 수위 상승 속도 (m/s)
     * @param floorAreaM2    A : 방 바닥 면적 (m²)
     */
    public static double inflowFromRiseSpeed(double riseSpeedMPerS, double floorAreaM2) {
        return riseSpeedMPerS * floorAreaM2;
    }

    /**
     * 유량 모델로 예측한, t초 뒤 예상 수위.  h(t) = h0 + (Q / A)·t   (m)
     *
     * @param h0M          현재 수위 (m)
     * @param inflowM3PerS Q : 유입 유량 (m³/s)
     * @param floorAreaM2  A : 방 바닥 면적 (m²)
     * @param tSec         t : 경과 시간 (초)
     */
    public static double projectedLevelM(double h0M, double inflowM3PerS, double floorAreaM2, double tSec) {
        return h0M + riseSpeedFromInflow(inflowM3PerS, floorAreaM2) * tSec;
    }

    /** 목표(주요) 도달 시간. seconds=null 이면 도달 불가. */
    public record Target(int targetCm, Integer seconds, boolean reachable) {}

    /** 인체 기준 부위별 도달 시간. */
    public record BodyEta(String part, double heightM, Integer seconds, boolean reachable) {}

    /** riseSpeedMPerS: 상승속도 v(m/s), inflowM3PerS: 유입 유량 Q(m³/s, 면적 미상이면 0). */
    public record Result(double riseSpeedMPerS, double inflowM3PerS, Target primary, List<BodyEta> bodyEta) {}

    /**
     * 목표 수위까지 남은 시간(초). 이미 도달=0, 상승 안 함=Infinity.
     */
    public static double secondsToTarget(double hCurrentM, double hTargetM, double vMPerS) {
        double remaining = hTargetM - hCurrentM;
        if (remaining <= 0) return 0;
        if (vMPerS <= 0) return Double.POSITIVE_INFINITY;
        return remaining / vMPerS;
    }

    /** 면적 미상(A=0) — 유입 유량 Q 는 계산하지 않는다(0). */
    public static Result compute(double levelCm, double riseCmPerMin, double targetCm) {
        return compute(levelCm, riseCmPerMin, targetCm, 0);
    }

    /**
     * 골든타임 + 유입 유량. 상승속도 v 는 실측(cm/min)에서 환산하고,
     * 바닥 면적 A 를 알면 유입 유량 Q = v·A 를 함께 역산한다.
     *
     * @param floorAreaM2 A : 방 바닥 면적 (m²). 0 이하이면 Q=0.
     */
    public static Result compute(double levelCm, double riseCmPerMin, double targetCm, double floorAreaM2) {
        double hCurrentM = levelCm / 100.0;
        double vMPerS = riseCmPerMin * CM_PER_MIN_TO_M_PER_S;
        double inflowM3PerS = inflowFromRiseSpeed(vMPerS, floorAreaM2);

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
        return new Result(vMPerS, inflowM3PerS, primary, bodyEta);
    }

    private static Integer toSeconds(double sec) {
        return Double.isFinite(sec) ? (int) Math.round(sec) : null;
    }
}
