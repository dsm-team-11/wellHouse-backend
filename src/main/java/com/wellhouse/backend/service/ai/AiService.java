package com.wellhouse.backend.service.ai;

import com.wellhouse.backend.entity.EventLogEntity;
import com.wellhouse.backend.service.weather.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * GPT 기반 문구/리포트 생성. 각 기능은 {@link OpenAiClient} 미연동/실패 시 규칙 기반 기본값으로 폴백한다.
 * (그래서 OPENAI_API_KEY 가 없어도 앱은 항상 의미 있는 결과를 받는다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final OpenAiClient openai;

    public boolean enabled() {
        return openai.enabled();
    }

    // ─────────────────────────────── 1) 홈 2줄 맞춤 문구 ───────────────────────────────

    /** @return [line1, line2] 정확히 2줄. */
    public String[] homeMessage(String name, String region, WeatherSnapshot w, String riskLabel) {
        String sys = "너는 반지하·저지대 침수 대비 IoT 앱 '우물집'의 홈 화면 문구 작성기다. "
                + "규칙: 정확히 2줄. 각 줄 24자 이내. 침착하고 따뜻한 존댓말. "
                + "이모지·과장·느낌표 남발 금지. 두 줄을 줄바꿈으로만 구분해 출력하고 다른 말은 붙이지 마라.";
        String usr = String.format(
                "사용자 이름: %s\n지역: %s\n기상특보: %s\n현재 강수량: %.1fmm/h, 예상: %.1fmm/h\n기기 위험단계: %s\n"
                        + "위 상황에 맞는 홈 화면 안내 문구 2줄을 만들어줘.",
                name, region, w.advisory(), w.rainMmH(), w.forecastMmH(), riskLabel);

        String out = openai.chat(sys, usr, 0.7, 120);
        if (out == null) return defaultHomeMessage(name, w, riskLabel);

        String[] lines = out.split("\\r?\\n");
        List<String> cleaned = new ArrayList<>();
        for (String l : lines) {
            String s = stripBullet(l);
            if (!s.isBlank()) cleaned.add(s);
        }
        if (cleaned.isEmpty()) return defaultHomeMessage(name, w, riskLabel);
        String l1 = cleaned.get(0);
        String l2 = cleaned.size() > 1 ? cleaned.get(1) : "";
        return new String[]{l1, l2};
    }

    private String[] defaultHomeMessage(String name, WeatherSnapshot w, String riskLabel) {
        double rain = Math.max(w.rainMmH(), w.forecastMmH());
        if (rain >= 30 || "위험".equals(riskLabel) || "경고".equals(riskLabel)) {
            return new String[]{"비가 강하게 내리고 있어요.", "귀중품을 높은 곳으로 옮겨주세요."};
        }
        if (rain >= 15 || "주의".equals(riskLabel)) {
            return new String[]{"비가 점점 강해지고 있어요.", "배수구와 물막이를 점검해 주세요."};
        }
        return new String[]{name + "님, 우리 집 침수 방어", "시스템이 정상 대기 중이에요."};
    }

    // ─────────────────────────────── 2) 체크리스트 우선순위 ───────────────────────────────

    public List<String> checklist(Boolean semiBasement, Double areaM2, Integer windows,
                                  WeatherSnapshot w, String riskLabel) {
        String sys = "너는 침수 대비 체크리스트를 우선순위 순으로 뽑아주는 안전 어시스턴트다. "
                + "규칙: 5~7개 항목. 가장 급한 것부터. 각 항목은 한 줄 명령형(존댓말) 20자 내외. "
                + "번호/불릿 없이 줄바꿈으로만 구분해 출력.";
        String usr = String.format(
                "거주형태: %s\n집 면적: %s㎡\n창문 수: %s\n기상특보: %s, 강수량 %.1fmm/h(예상 %.1fmm/h)\n위험단계: %s\n"
                        + "지금 당장 해야 할 침수 대비 행동을 우선순위대로 정리해줘.",
                Boolean.TRUE.equals(semiBasement) ? "반지하" : "지상",
                areaM2 == null ? "미상" : String.valueOf(Math.round(areaM2)),
                windows == null ? "미상" : String.valueOf(windows),
                w.advisory(), w.rainMmH(), w.forecastMmH(), riskLabel);

        String out = openai.chat(sys, usr, 0.5, 300);
        if (out == null) return defaultChecklist(semiBasement);
        List<String> items = parseLines(out, 7);
        return items.isEmpty() ? defaultChecklist(semiBasement) : items;
    }

    private List<String> defaultChecklist(Boolean semiBasement) {
        List<String> base = new ArrayList<>(List.of(
                "배수구·하수구 막힘 여부 확인",
                "물막이판·모래주머니 준비",
                "귀중품·전자기기 높은 곳으로 이동",
                "차량을 안전한 곳으로 이동",
                "손전등·비상식량·구급함 점검",
                "대피 경로와 대피소 위치 확인"
        ));
        if (Boolean.TRUE.equals(semiBasement)) {
            base.add(0, "현관문 앞 물막이 우선 설치");
            base.add("정전 대비 두꺼비집 위치 확인");
        }
        return base;
    }

    // ─────────────────────────────── 3) 침수 후 사후 리포트 ───────────────────────────────

    public String report(List<EventLogEntity> events) {
        String timeline = summarizeEvents(events);
        String sys = "너는 침수 사고 후 사용자에게 보여줄 사후 리포트를 쓰는 어시스턴트다. "
                + "마크다운으로 '## 요약', '## 진행 경과', '## 대응', '## 다음 대비' 4개 섹션으로 간결하게. "
                + "차분한 존댓말. 데이터에 없는 사실은 지어내지 마라.";
        String usr = "다음은 이번 침수 상황의 이벤트 로그다.\n" + timeline
                + "\n이 내용을 바탕으로 사후 리포트를 작성해줘.";

        String out = openai.chat(sys, usr, 0.5, 700);
        return out != null ? out : defaultReport(events, timeline);
    }

    private String defaultReport(List<EventLogEntity> events, String timeline) {
        return "## 요약\n최근 침수 위험 상황이 감지되어 자동 대응이 실행되었습니다.\n\n"
                + "## 진행 경과\n" + (timeline.isBlank() ? "- 기록된 이벤트가 없습니다.\n" : timeline)
                + "\n## 대응\n- 위험단계 상승에 따라 물막이·차단 시스템이 작동했습니다.\n"
                + "\n## 다음 대비\n- 배수구 점검과 물막이 상태를 다시 확인해 주세요.";
    }

    // ─────────────────────────────── 4) AI 개선 방안 ───────────────────────────────

    public String improvements(Boolean semiBasement, Double areaM2, Integer windows, List<EventLogEntity> events) {
        String sys = "너는 침수 대비 개선 컨설턴트다. 사용자의 집 정보와 이번 사고 경과를 보고 "
                + "구체적이고 실행 가능한 개선 방안을 마크다운 불릿 5개 내외로 제안해라. 차분한 존댓말.";
        String usr = String.format(
                "거주형태: %s\n면적: %s㎡\n창문 수: %s\n이번 사고 경과:\n%s\n"
                        + "재발 방지를 위한 개선 방안을 제안해줘.",
                Boolean.TRUE.equals(semiBasement) ? "반지하" : "지상",
                areaM2 == null ? "미상" : String.valueOf(Math.round(areaM2)),
                windows == null ? "미상" : String.valueOf(windows),
                summarizeEvents(events));

        String out = openai.chat(sys, usr, 0.6, 500);
        return out != null ? out : defaultImprovements(semiBasement);
    }

    private String defaultImprovements(Boolean semiBasement) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 현관·창문에 상시 물막이판을 설치해 두세요.\n");
        sb.append("- 배수구 역류 방지 밸브(역류방지기) 설치를 검토하세요.\n");
        sb.append("- 전자기기·콘센트 위치를 바닥에서 높여 배치하세요.\n");
        if (Boolean.TRUE.equals(semiBasement)) {
            sb.append("- 반지하 특성상 배수펌프와 예비 전원을 함께 준비하세요.\n");
        }
        sb.append("- 대피 경로와 가까운 대피소를 가족과 공유해 두세요.\n");
        return sb.toString();
    }

    // ─────────────────────────────── 공통 유틸 ───────────────────────────────

    private String summarizeEvents(List<EventLogEntity> events) {
        if (events == null || events.isEmpty()) return "(기록된 이벤트 없음)";
        StringBuilder sb = new StringBuilder();
        for (EventLogEntity e : events) {
            sb.append("- ").append(e.getTs()).append(" ").append(e.getType());
            if (e.getFromLevel() != null && e.getToLevel() != null) {
                sb.append(" (").append(e.getFromLevel()).append("→").append(e.getToLevel()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 번호/불릿을 떼고 빈 줄 제거, 최대 max개. */
    private List<String> parseLines(String text, int max) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String s = stripBullet(line);
            if (!s.isBlank()) out.add(s);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** 앞쪽 "1. ", "- ", "• ", "* " 등의 머리표 제거. */
    private String stripBullet(String line) {
        return line == null ? "" : line.strip()
                .replaceFirst("^\\s*(\\d+[.)]|[-*•·])\\s*", "")
                .strip();
    }
}
