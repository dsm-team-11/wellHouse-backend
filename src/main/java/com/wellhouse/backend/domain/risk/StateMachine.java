package com.wellhouse.backend.domain.risk;

/**
 * 상태 전이 히스테리시스.
 *  - 승격: 즉시.
 *  - 강등: 한 번에 한 단계씩, 안정 유지 시간(위험→경고 10, 경고→주의 20, 주의→양호 30분) 필요.
 *  - 특보 floor 아래로는 강등 금지.
 * 순수 함수. 시각(nowMs)을 주입받아 테스트 가능.
 */
public final class StateMachine {
    private StateMachine() {}

    /** 전이 결과. candidateSince=null 이면 타이머 없음(승격/유지/floor). */
    public record Transition(RiskLevel level, Long candidateSince, boolean changed, String reason) {}

    /** from 단계에서 한 단계 강등에 필요한 안정 시간(초). */
    public static int requiredStableSeconds(RiskLevel from) {
        return switch (from) {
            case DANGER -> Thresholds.DANGER_TO_WARNING_SEC;
            case WARNING -> Thresholds.WARNING_TO_CAUTION_SEC;
            case CAUTION -> Thresholds.CAUTION_TO_GOOD_SEC;
            default -> Integer.MAX_VALUE; // GOOD 아래 없음
        };
    }

    /**
     * 다음 확정 상태 계산.
     *
     * @param prevLevel          직전 확정 단계
     * @param prevCandidateSince 하향 후보 연속 유지 시작 시각(ms), 없으면 null
     * @param computed           riskEngine 원시 결과
     * @param nowMs              현재 시각(ms)
     * @param advisoryFloor      특보 floor
     */
    public static Transition resolve(RiskLevel prevLevel, Long prevCandidateSince,
                                     RiskLevel computed, long nowMs, RiskLevel advisoryFloor) {
        if (prevLevel == null) prevLevel = RiskLevel.GOOD;

        // 1) 승격/유지: 즉시 반영
        if (computed.rank >= prevLevel.rank) {
            boolean changed = computed != prevLevel;
            return new Transition(computed, null, changed, changed ? "upgrade" : "hold");
        }

        // 2) 강등 시도. 특보 floor 아래로는 불가.
        RiskLevel floor = advisoryFloor == null ? RiskLevel.GOOD : advisoryFloor;
        if (prevLevel.rank <= floor.rank) {
            return new Transition(prevLevel, null, false, "advisory_floor_hold");
        }

        // 3) 안정 시간 체크
        long candidateSince = prevCandidateSince != null ? prevCandidateSince : nowMs;
        long stableMs = nowMs - candidateSince;
        long needMs = (long) requiredStableSeconds(prevLevel) * 1000L;

        if (stableMs >= needMs) {
            RiskLevel next = RiskLevel.fromRank(Math.max(prevLevel.rank - 1, floor.rank));
            // 다음 단계 타이머 리셋 (floor 도달 시 타이머 없음)
            Long nextSince = next.rank > floor.rank ? nowMs : null;
            boolean changed = next != prevLevel;
            return new Transition(next, nextSince, changed, "downgrade");
        }

        return new Transition(prevLevel, candidateSince, false, "stabilizing");
    }
}
