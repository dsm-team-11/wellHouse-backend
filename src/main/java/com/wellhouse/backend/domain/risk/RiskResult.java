package com.wellhouse.backend.domain.risk;

import java.util.List;

/**
 * 위험도 계산 결과.
 * @param level         최종 확정(원시) 단계
 * @param contributors  각 요소별 기여 단계
 * @param bumps         적용된 +1 상향 사유
 * @param advisoryFloor 기상특보 floor (강등 하한에 사용)
 */
public record RiskResult(
        RiskLevel level,
        Contributors contributors,
        List<String> bumps,
        RiskLevel advisoryFloor
) {
    public record Contributors(
            RiskLevel water,
            RiskLevel rain,
            RiskLevel advisory,
            RiskLevel riseRate,
            RiskLevel forecast
    ) {}
}
