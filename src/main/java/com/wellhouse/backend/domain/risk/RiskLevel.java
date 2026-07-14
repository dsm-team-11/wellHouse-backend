package com.wellhouse.backend.domain.risk;

/**
 * 위험 단계. rank가 클수록 위험 (MAX 종합에 사용하는 순서형).
 * GOOD(양호) < CAUTION(주의) < WARNING(경고) < DANGER(위험)
 */
public enum RiskLevel {
    GOOD(0, "양호", "green"),
    CAUTION(1, "주의", "yellow"),
    WARNING(2, "경고", "orange"),
    DANGER(3, "위험", "red");

    public final int rank;
    public final String label;
    public final String color;

    RiskLevel(int rank, String label, String color) {
        this.rank = rank;
        this.label = label;
        this.color = color;
    }

    /** rank를 [GOOD, DANGER]로 클램프해 반환 */
    public static RiskLevel fromRank(int rank) {
        int r = Math.max(GOOD.rank, Math.min(DANGER.rank, rank));
        for (RiskLevel l : values()) {
            if (l.rank == r) return l;
        }
        return GOOD;
    }

    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        return a.rank >= b.rank ? a : b;
    }

    /** 한 단계 상향 (DANGER에서 클램프) */
    public RiskLevel bumped() {
        return fromRank(this.rank + 1);
    }
}
