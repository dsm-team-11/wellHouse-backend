package com.wellhouse.backend.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.wellhouse.backend.domain.risk.Advisory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기상청 단기예보 조회서비스(공공데이터포털) 연동.
 *
 * <ul>
 *   <li>초단기실황(getUltraSrtNcst): 현재 1시간 강수량 RN1</li>
 *   <li>초단기예보(getUltraSrtFcst): 다음 시각 예상 강수량 RN1</li>
 * </ul>
 *
 * <p>서비스키({@code wellhouse.weather.kma-service-key}, env {@code KMA_SERVICE_KEY})가 없으면
 * 호출하지 않고 {@link WeatherSnapshot#EMPTY}(강수 0)를 돌려준다. 공공데이터포털에서 발급한
 * <b>"일반 인증키(Decoding)"</b>를 사용해야 한다(RestClient가 인코딩을 1회 수행).
 */
@Slf4j
@Component
public class KmaWeatherClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final RestClient rest;
    private final String serviceKey;

    public KmaWeatherClient(
            @Value("${wellhouse.weather.kma-service-key:}") String serviceKey,
            @Value("${wellhouse.weather.kma-base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl) {
        this.serviceKey = serviceKey;
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean enabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /** 격자 좌표의 현재 기상 스냅샷. 미연동/오류 시 {@link WeatherSnapshot#EMPTY}. */
    public WeatherSnapshot fetch(KmaGrid.Cell cell) {
        if (!enabled()) return WeatherSnapshot.EMPTY;
        try {
            double rainNow = fetchNowcastRain(cell);
            double rainForecast = fetchForecastRain(cell);
            // 특보(호우주의보/경보)는 별도 서비스라 여기선 NONE. 실황 강수량으로 위험도가 이미 상승한다.
            // 3시간 누적은 실황만으론 알 수 없어 현재 1시간 강수량을 보수적 하한으로 사용한다.
            return new WeatherSnapshot(rainNow, rainNow, Advisory.NONE, rainForecast);
        } catch (Exception e) {
            log.warn("KMA fetch 실패 nx={} ny={}: {}", cell.nx(), cell.ny(), e.toString());
            return WeatherSnapshot.EMPTY;
        }
    }

    /** 초단기실황 RN1(1시간 강수량, mm). 매 정시 발표·약 40분 후 제공 → 40분 이전이면 직전 시각 사용. */
    private double fetchNowcastRain(KmaGrid.Cell cell) {
        LocalDateTime t = LocalDateTime.now();
        if (t.getMinute() < 40) t = t.minusHours(1);
        JsonNode items = call("/getUltraSrtNcst", t.format(DATE), t.format(HOUR) + "00", cell, 100);
        if (items == null) return 0;
        for (JsonNode item : items) {
            if ("RN1".equals(item.path("category").asText())) {
                return parseRain(item.path("obsrValue").asText());
            }
        }
        return 0;
    }

    /** 초단기예보 RN1: 가장 이른 예보 시각의 예상 강수량(mm). 매시 30분 발표·약 45분 후 제공. */
    private double fetchForecastRain(KmaGrid.Cell cell) {
        LocalDateTime t = LocalDateTime.now();
        if (t.getMinute() < 45) t = t.minusHours(1);
        JsonNode items = call("/getUltraSrtFcst", t.format(DATE), t.format(HOUR) + "30", cell, 300);
        if (items == null) return 0;
        String earliestTime = null;
        double rain = 0;
        for (JsonNode item : items) {
            if (!"RN1".equals(item.path("category").asText())) continue;
            String fcstTime = item.path("fcstTime").asText();
            if (earliestTime == null || fcstTime.compareTo(earliestTime) < 0) {
                earliestTime = fcstTime;
                rain = parseRain(item.path("fcstValue").asText());
            }
        }
        return rain;
    }

    /** 공통 호출 → items 배열 노드 반환(없으면 null). */
    private JsonNode call(String path, String baseDate, String baseTime, KmaGrid.Cell cell, int numOfRows) {
        JsonNode root = rest.get()
                .uri(uri -> uri.path(path)
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("dataType", "JSON")
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("pageNo", 1)
                        .queryParam("base_date", baseDate)
                        .queryParam("base_time", baseTime)
                        .queryParam("nx", cell.nx())
                        .queryParam("ny", cell.ny())
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (root == null) return null;
        String resultCode = root.path("response").path("header").path("resultCode").asText("");
        if (!"00".equals(resultCode)) {
            log.warn("KMA 응답코드 {} ({})", resultCode,
                    root.path("response").path("header").path("resultMsg").asText(""));
            return null;
        }
        JsonNode items = root.path("response").path("body").path("items").path("item");
        return items.isArray() ? items : null;
    }

    /** "강수없음"/"1.0mm"/"30.0~50.0mm"/"0" 등을 mm 숫자로 관대하게 파싱. */
    static double parseRain(String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        if (s.isEmpty() || s.contains("없음") || "-".equals(s)) return 0;
        Matcher m = NUMBER.matcher(s);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }
}
