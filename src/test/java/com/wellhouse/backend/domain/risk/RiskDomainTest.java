package com.wellhouse.backend.domain.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 핵심 도메인 로직 단위 테스트 (JS core.test.js 와 동등). */
class RiskDomainTest {

    private static final long MIN = 60_000L;

    @Test
    @DisplayName("수위 단계: 경계값")
    void waterStage() {
        assertEquals(RiskLevel.GOOD, RiskEngine.waterLevelStage(0, 0));
        assertEquals(RiskLevel.GOOD, RiskEngine.waterLevelStage(2.9, 0));
        assertEquals(RiskLevel.CAUTION, RiskEngine.waterLevelStage(3, 0));
        assertEquals(RiskLevel.CAUTION, RiskEngine.waterLevelStage(5, 0));     // 정지
        assertEquals(RiskLevel.WARNING, RiskEngine.waterLevelStage(5, 0.6));   // 지속 상승
        assertEquals(RiskLevel.DANGER, RiskEngine.waterLevelStage(10, 0));
        assertEquals(RiskLevel.DANGER, RiskEngine.waterLevelStage(30, 0));
    }

    @Test
    @DisplayName("강수 단계")
    void rainStage() {
        assertEquals(RiskLevel.GOOD, RiskEngine.rainStage(14));
        assertEquals(RiskLevel.CAUTION, RiskEngine.rainStage(15));
        assertEquals(RiskLevel.WARNING, RiskEngine.rainStage(30));
        assertEquals(RiskLevel.DANGER, RiskEngine.rainStage(50));
        assertEquals(RiskLevel.DANGER, RiskEngine.rainStage(120));
    }

    @Test
    @DisplayName("최종 위험도 = MAX(...) 종합 + bump")
    void computeRisk() {
        // 수위 낮아도 강수가 위험이면 위험
        assertEquals(RiskLevel.DANGER,
                RiskEngine.compute(RiskInputs.builder().levelCm(1).rainMmPerH(55).build()).level());

        // 호우경보 floor
        assertEquals(RiskLevel.WARNING,
                RiskEngine.compute(RiskInputs.builder().advisory(Advisory.WARNING).build()).level());

        // 상승속도 ≥2 → WARNING + bump → DANGER
        RiskResult r3 = RiskEngine.compute(RiskInputs.builder().levelCm(5).riseCmPerMin(2.5).build());
        assertEquals(RiskLevel.DANGER, r3.level());
        assertFalse(r3.bumps().isEmpty());

        // 누적 3h 65mm → +1 (CAUTION → WARNING)
        assertEquals(RiskLevel.WARNING,
                RiskEngine.compute(RiskInputs.builder().rainMmPerH(20).cumulativeRainMm(65).build()).level());

        // 예보 50mm/h → 최소 경고
        assertEquals(RiskLevel.WARNING,
                RiskEngine.compute(RiskInputs.builder().forecastMmPerH(50).build()).level());
    }

    @Test
    @DisplayName("골든타임: 도달 시간")
    void goldenTime() {
        // 2cm/min, 2cm→10cm = 8cm 남음 = 4분 = 240초
        GoldenTime.Result g = GoldenTime.compute(2, 2, 10);
        assertEquals(240, g.primary().seconds());
        assertTrue(g.primary().reachable());

        // 상승 안 하면 도달 불가
        GoldenTime.Result g2 = GoldenTime.compute(2, 0, 10);
        assertFalse(g2.primary().reachable());
        assertNull(g2.primary().seconds());

        // 인체 기준 무릎 ETA 존재
        assertTrue(g.bodyEta().stream().anyMatch(b -> b.part().equals("knee") && b.reachable()));
    }

    @Test
    @DisplayName("수위 상승 속도: v = Q / A, Q = v · A 역산")
    void riseSpeedFromInflow() {
        // Q=0.02 m³/s, A=10 m² → v = 0.002 m/s
        assertEquals(0.002, GoldenTime.riseSpeedFromInflow(0.02, 10), 1e-9);
        // 면적 0 이하 → 상승 없음
        assertEquals(0.0, GoldenTime.riseSpeedFromInflow(0.02, 0), 1e-9);
        // 역산 왕복: Q = v · A
        assertEquals(0.02, GoldenTime.inflowFromRiseSpeed(0.002, 10), 1e-9);

        // t초 뒤 예상 수위: h(t) = h0 + (Q/A)·t
        // h0=0.02m, v=0.002m/s, t=60s → 0.02 + 0.12 = 0.14m
        assertEquals(0.14, GoldenTime.projectedLevelM(0.02, 0.02, 10, 60), 1e-9);
    }

    @Test
    @DisplayName("골든타임 4-arg: 면적 주면 유입 유량 Q 역산")
    void goldenTimeWithArea() {
        // 2cm/min = 0.000333.. m/s, A=10 m² → Q = v·A
        GoldenTime.Result g = GoldenTime.compute(2, 2, 10, 10);
        assertEquals(240, g.primary().seconds());            // 골든타임은 A와 무관
        assertEquals(g.riseSpeedMPerS() * 10, g.inflowM3PerS(), 1e-12);
        assertTrue(g.inflowM3PerS() > 0);

        // 면적 미상(기본 3-arg) → Q=0
        assertEquals(0.0, GoldenTime.compute(2, 2, 10).inflowM3PerS(), 1e-12);
    }

    @Test
    @DisplayName("상태머신: 승격 즉시, 강등은 안정시간 필요")
    void hysteresis() {
        long t0 = 1_000_000L;

        // GOOD → DANGER 즉시
        StateMachine.Transition up = StateMachine.resolve(RiskLevel.GOOD, null, RiskLevel.DANGER, t0, RiskLevel.GOOD);
        assertEquals(RiskLevel.DANGER, up.level());
        assertEquals("upgrade", up.reason());

        // DANGER에서 computed=GOOD, 5분 → 아직 강등 안 됨
        StateMachine.Transition s1 = StateMachine.resolve(RiskLevel.DANGER, t0, RiskLevel.GOOD, t0 + 5 * MIN, RiskLevel.GOOD);
        assertEquals(RiskLevel.DANGER, s1.level());
        assertEquals("stabilizing", s1.reason());

        // 10분 → 한 단계(WARNING)만 강등
        StateMachine.Transition s2 = StateMachine.resolve(RiskLevel.DANGER, t0, RiskLevel.GOOD, t0 + 10 * MIN, RiskLevel.GOOD);
        assertEquals(RiskLevel.WARNING, s2.level());
        assertEquals("downgrade", s2.reason());
    }

    @Test
    @DisplayName("상태머신: 특보 floor 아래로 강등 금지")
    void advisoryFloorHold() {
        long t0 = 2_000_000L;
        StateMachine.Transition s = StateMachine.resolve(
                RiskLevel.WARNING, t0, RiskLevel.GOOD, t0 + 60 * MIN, RiskLevel.WARNING);
        assertEquals(RiskLevel.WARNING, s.level());
        assertEquals("advisory_floor_hold", s.reason());
    }
}
