package com.wellhouse.backend.domain.risk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 위험도 판정 상수. 값의 근거는 docs/RISK_ENGINE.md.
 * 모든 임계값을 한 곳에 모아 튜닝을 쉽게 한다.
 */
public final class Thresholds {
    private Thresholds() {}

    // 수위(cm) 절대 기준 4단계: 양호 <3 / 주의 3~6 / 경고 6~10 / 위험 10~
    public static final double WATER_CAUTION_CM = 3;   // 물 유입 시작
    public static final double WATER_WARNING_CM = 6;   // 감전 위험대 진입
    public static final double WATER_DANGER_CM = 10;   // 즉시 대피

    // 수위 상승 속도(cm/min)
    public static final double RISE_CAUTION_FLOOR = 0.5; // 0.5~2 → 최소 주의
    public static final double RISE_BUMP = 2.0;          // ≥2 → 최종 +1 상향
    public static final double SUSTAINED_RISE = 0.5;     // ≥0.5 → "지속 상승" 판정

    // 시간당 강수량(mm/h)
    public static final double RAIN_CAUTION = 15;
    public static final double RAIN_WARNING = 30;
    public static final double RAIN_DANGER = 50;
    public static final double RAIN_EXTREME = 80;        // 극한호우 플래그
    public static final double WAKEUP_RAIN = 30;         // 절전 해제 트리거

    // 누적 강수: 최근 3h 누적 ≥60mm → +1
    public static final int CUM_WINDOW_HOURS = 3;
    public static final double CUM_BUMP_MM = 60;

    // 예보 선행 대응: 2h내 예보 강수(mm/h)
    public static final int FORECAST_LOOKAHEAD_HOURS = 2;
    public static final double FORECAST_CAUTION = 30;
    public static final double FORECAST_WARNING = 50;

    // 강등 히스테리시스: 안정 유지 시간(초). median 스무딩이 이미 깜빡임을 막으므로,
    // 데모의 실시간 양방향 반응(물 빠지면 곧 색 하강)을 위해 짧게 둔다. (원래 분 단위 10/20/30)
    public static final int DANGER_TO_WARNING_SEC = 10;
    public static final int WARNING_TO_CAUTION_SEC = 10;
    public static final int CAUTION_TO_GOOD_SEC = 10;

    // 인체 기준 위험 수위(m) - 골든타임 프리셋 (삽입 순서 유지)
    public static final Map<String, Double> BODY_HEIGHT_M;
    static {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("knee", 0.4);   // 무릎
        m.put("waist", 0.9);  // 허리
        m.put("chest", 1.2);  // 가슴
        m.put("chin", 1.5);   // 턱
        m.put("nose", 1.6);   // 코
        BODY_HEIGHT_M = Map.copyOf(m); // 불변. 순서가 필요하면 아래 ORDERED 사용
    }
    /** 순서가 보장되는 인체 기준(반복용) */
    public static final Map<String, Double> BODY_HEIGHT_M_ORDERED = buildOrdered();
    private static Map<String, Double> buildOrdered() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("knee", 0.4);
        m.put("waist", 0.9);
        m.put("chest", 1.2);
        m.put("chin", 1.5);
        m.put("nose", 1.6);
        return m;
    }

    // 기본 목표 수위(cm): 위험 진입선 10cm
    public static final double DEFAULT_GOLDEN_TARGET_CM = WATER_DANGER_CM;
}
