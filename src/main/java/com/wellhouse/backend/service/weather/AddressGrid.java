package com.wellhouse.backend.service.weather;

import java.util.Map;

/**
 * 주소 문자열(또는 지역 별칭)을 기상청 격자(nx, ny)로 변환한다.
 *
 * <p>다음 우편번호 위젯은 위경도를 주지 않고 도로명 주소 문자열만 돌려주므로,
 * 외부 지오코딩 키 없이 동작하도록 <b>시/도 · 시/군/구 대표 좌표표</b>를 내장한다.
 * 우선순위: {@code 시도|시군구} 정밀 좌표 → 시/도 중심 좌표 → 서울(기본).
 *
 * <p>표에 없는 시/군/구는 시/도 중심으로 폴백한다. 필요 시 표만 확장하면 정밀도가 올라간다.
 */
public final class AddressGrid {

    private AddressGrid() {}

    public record Location(double lat, double lon, KmaGrid.Cell cell, String label) {}

    private static final double[] DEFAULT = {37.5665, 126.9780}; // 서울시청

    /** 설정/스케줄러가 쓰는 영문 지역 별칭. */
    private static final Map<String, double[]> ALIAS = Map.of(
            "seoul", new double[]{37.5665, 126.9780},
            "incheon", new double[]{37.4563, 126.7052},
            "gyeonggi", new double[]{37.2636, 127.0286},
            "daejeon", new double[]{36.3504, 127.3845},
            "busan", new double[]{35.1796, 129.0756}
    );

    /** 시/도 중심(도청·시청 인근) 대표 좌표. 키는 정규화된 시/도명. */
    private static final Map<String, double[]> SIDO = Map.ofEntries(
            Map.entry("서울", new double[]{37.5665, 126.9780}),
            Map.entry("부산", new double[]{35.1796, 129.0756}),
            Map.entry("대구", new double[]{35.8714, 128.6014}),
            Map.entry("인천", new double[]{37.4563, 126.7052}),
            Map.entry("광주", new double[]{35.1595, 126.8526}),
            Map.entry("대전", new double[]{36.3504, 127.3845}),
            Map.entry("울산", new double[]{35.5384, 129.3114}),
            Map.entry("세종", new double[]{36.4800, 127.2890}),
            Map.entry("경기", new double[]{37.2636, 127.0286}),
            Map.entry("강원", new double[]{37.8813, 127.7298}),
            Map.entry("충북", new double[]{36.6357, 127.4917}),
            Map.entry("충남", new double[]{36.6588, 126.6728}),
            Map.entry("전북", new double[]{35.8242, 127.1480}),
            Map.entry("전남", new double[]{34.8161, 126.4629}),
            Map.entry("경북", new double[]{36.5760, 128.5056}),
            Map.entry("경남", new double[]{35.2280, 128.6811}),
            Map.entry("제주", new double[]{33.4996, 126.5312})
    );

    /** 정밀 좌표: "정규화시도|시군구" → 좌표. (대전 5개 구는 서비스 핵심 지역이라 전부 포함) */
    private static final Map<String, double[]> DISTRICT = Map.ofEntries(
            Map.entry("대전|동구", new double[]{36.3110, 127.4548}),
            Map.entry("대전|중구", new double[]{36.3255, 127.4213}),
            Map.entry("대전|서구", new double[]{36.3555, 127.3838}),
            Map.entry("대전|유성구", new double[]{36.3620, 127.3562}),
            Map.entry("대전|대덕구", new double[]{36.3466, 127.4155}),
            Map.entry("서울|강남구", new double[]{37.5172, 127.0473}),
            Map.entry("서울|관악구", new double[]{37.4784, 126.9516}),
            Map.entry("서울|송파구", new double[]{37.5145, 127.1060}),
            Map.entry("서울|영등포구", new double[]{37.5264, 126.8963}),
            Map.entry("서울|강서구", new double[]{37.5509, 126.8495}),
            Map.entry("경기|수원시", new double[]{37.2636, 127.0286}),
            Map.entry("경기|성남시", new double[]{37.4200, 127.1265}),
            Map.entry("경기|고양시", new double[]{37.6584, 126.8320}),
            Map.entry("경기|용인시", new double[]{37.2411, 127.1776}),
            Map.entry("인천|미추홀구", new double[]{37.4636, 126.6503}),
            Map.entry("인천|부평구", new double[]{37.5070, 126.7219})
    );

    /**
     * 도로명/지번 주소 문자열을 격자로 변환.
     * @param address 예: "대전광역시 유성구 가정로 76" (null/blank 이면 기본값=서울)
     */
    public static Location fromAddress(String address) {
        if (address == null || address.isBlank()) {
            return locate(DEFAULT, "서울");
        }
        String[] tokens = address.trim().split("\\s+");
        String sido = normalizeSido(tokens[0]);
        String sigungu = tokens.length > 1 ? tokens[1] : "";

        double[] coord = DISTRICT.get(sido + "|" + sigungu);
        String label;
        if (coord != null) {
            label = sido + " " + sigungu;
        } else {
            coord = SIDO.getOrDefault(sido, DEFAULT);
            label = SIDO.containsKey(sido) ? (sigungu.isBlank() ? sido : sido + " " + sigungu) : "서울";
        }
        return locate(coord, label);
    }

    /** 영문 지역 별칭(seoul 등) 또는 한글 주소를 모두 받아 변환. */
    public static Location fromRegion(String region) {
        if (region == null || region.isBlank()) return locate(DEFAULT, "서울");
        double[] alias = ALIAS.get(region.trim().toLowerCase());
        if (alias != null) return locate(alias, region.trim());
        return fromAddress(region);
    }

    private static Location locate(double[] coord, String label) {
        return new Location(coord[0], coord[1], KmaGrid.toGrid(coord[0], coord[1]), label);
    }

    /** "대전광역시" → "대전", "경기도" → "경기" 등으로 정규화. */
    private static String normalizeSido(String raw) {
        String s = raw.trim();
        return switch (s) {
            case "서울특별시", "서울시", "서울" -> "서울";
            case "부산광역시", "부산시", "부산" -> "부산";
            case "대구광역시", "대구시", "대구" -> "대구";
            case "인천광역시", "인천시", "인천" -> "인천";
            case "광주광역시", "광주시", "광주" -> "광주";
            case "대전광역시", "대전시", "대전" -> "대전";
            case "울산광역시", "울산시", "울산" -> "울산";
            case "세종특별자치시", "세종시", "세종" -> "세종";
            case "경기도", "경기" -> "경기";
            case "강원도", "강원특별자치도", "강원" -> "강원";
            case "충청북도", "충북" -> "충북";
            case "충청남도", "충남" -> "충남";
            case "전라북도", "전북특별자치도", "전북" -> "전북";
            case "전라남도", "전남" -> "전남";
            case "경상북도", "경북" -> "경북";
            case "경상남도", "경남" -> "경남";
            case "제주특별자치도", "제주도", "제주" -> "제주";
            default -> s;
        };
    }
}
