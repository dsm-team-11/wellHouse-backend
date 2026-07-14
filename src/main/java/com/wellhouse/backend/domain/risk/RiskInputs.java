package com.wellhouse.backend.domain.risk;

/**
 * 위험도 계산 입력. 빌더로 필요한 값만 지정하고 나머지는 기본값(0/NONE).
 */
public record RiskInputs(
        double levelCm,
        double riseCmPerMin,
        double rainMmPerH,
        double cumulativeRainMm,
        Advisory advisory,
        double forecastMmPerH
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double levelCm = 0;
        private double riseCmPerMin = 0;
        private double rainMmPerH = 0;
        private double cumulativeRainMm = 0;
        private Advisory advisory = Advisory.NONE;
        private double forecastMmPerH = 0;

        public Builder levelCm(double v) { this.levelCm = v; return this; }
        public Builder riseCmPerMin(double v) { this.riseCmPerMin = v; return this; }
        public Builder rainMmPerH(double v) { this.rainMmPerH = v; return this; }
        public Builder cumulativeRainMm(double v) { this.cumulativeRainMm = v; return this; }
        public Builder advisory(Advisory v) { this.advisory = v == null ? Advisory.NONE : v; return this; }
        public Builder forecastMmPerH(double v) { this.forecastMmPerH = v; return this; }

        public RiskInputs build() {
            return new RiskInputs(levelCm, riseCmPerMin, rainMmPerH,
                    cumulativeRainMm, advisory, forecastMmPerH);
        }
    }
}
