package com.wellhouse.backend.web.dto;

import java.util.List;

/** AI(GPT) 기능 응답 DTO 모음. {@code ai=true} 면 실제 GPT 생성, false 면 규칙 기반 폴백. */
public final class AiDtos {

    private AiDtos() {}

    /** 홈 화면 2줄 맞춤 문구. */
    public record HomeMessageDto(String line1, String line2, boolean ai) {}

    /** 상황 맞춤 체크리스트(우선순위 순 정렬). */
    public record ChecklistDto(List<String> items, boolean ai) {}

    /** 침수 후 사후 리포트 / AI 개선 방안(마크다운 텍스트). */
    public record ReportDto(String markdown, boolean ai) {}
}
