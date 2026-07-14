package com.wellhouse.backend.service;

import com.wellhouse.backend.domain.risk.Thresholds;
import com.wellhouse.backend.entity.DeviceEntity;
import com.wellhouse.backend.entity.WeatherEntity;
import com.wellhouse.backend.messaging.DeviceCommander;
import com.wellhouse.backend.repository.DeviceRepository;
import com.wellhouse.backend.repository.WeatherRepository;
import com.wellhouse.backend.service.weather.AddressGrid;
import com.wellhouse.backend.service.weather.KmaGrid;
import com.wellhouse.backend.service.weather.KmaWeatherClient;
import com.wellhouse.backend.service.weather.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기상 폴링/저장 + 강수 급증 시 절전 해제.
 *
 * <p>실제 기상 데이터는 {@link KmaWeatherClient}(기상청 초단기실황/예보)에서 온다.
 * 앱은 홈 화면을 짧은 주기로 폴링하므로 격자별로 {@link #GRID_TTL} 동안 캐시해 KMA 호출을 아낀다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    /** 앱 폴링(수 초 주기)에도 KMA를 과호출하지 않도록 격자 캐시 유지 시간. */
    private static final Duration GRID_TTL = Duration.ofMinutes(10);

    private final WeatherRepository weatherRepo;
    private final DeviceRepository deviceRepo;
    private final DeviceCommander commander;
    private final KmaWeatherClient kma;

    @Value("${wellhouse.weather.regions:seoul}")
    private String regionsCsv;

    private record Cached(WeatherSnapshot snapshot, Instant at) {}
    private final Map<String, Cached> gridCache = new ConcurrentHashMap<>();

    public List<String> regions() {
        return Arrays.stream(regionsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @Transactional
    public void pollAll() {
        for (String region : regions()) {
            WeatherSnapshot w = fetchRegionWeather(region);
            weatherRepo.save(WeatherEntity.builder()
                    .region(region)
                    .rainMmH(w.rainMmH())
                    .cumulative3hMm(w.cumulative3hMm())
                    .advisory(w.advisory())
                    .forecastMmH(w.forecastMmH())
                    .updatedAt(Instant.now())
                    .build());

            if (w.rainMmH() >= Thresholds.WAKEUP_RAIN) {
                wakeupRegion(region, w.rainMmH());
            }
        }
        log.info("weather poll done regions={} kma={}", regions().size(), kma.enabled());
    }

    public WeatherEntity current(String region) {
        return weatherRepo.findById(region).orElse(null);
    }

    /** 지역 별칭 또는 한글 주소 → 격자 → KMA(캐시). */
    WeatherSnapshot fetchRegionWeather(String region) {
        return byGrid(AddressGrid.fromRegion(region).cell());
    }

    /** 주소 문자열로 현재 기상 조회(앱 /api/weather/me). */
    public WeatherSnapshot fetchByAddress(String address) {
        return byGrid(AddressGrid.fromAddress(address).cell());
    }

    /** 저장된 격자로 조회. nx/ny 가 없으면 서울 기본 격자. */
    public WeatherSnapshot fetchByGrid(Integer nx, Integer ny) {
        KmaGrid.Cell cell = (nx != null && ny != null)
                ? new KmaGrid.Cell(nx, ny)
                : AddressGrid.fromAddress(null).cell();
        return byGrid(cell);
    }

    private WeatherSnapshot byGrid(KmaGrid.Cell cell) {
        String key = cell.nx() + ":" + cell.ny();
        Cached c = gridCache.get(key);
        if (c != null && Duration.between(c.at(), Instant.now()).compareTo(GRID_TTL) < 0) {
            return c.snapshot();
        }
        WeatherSnapshot fresh = kma.fetch(cell);
        gridCache.put(key, new Cached(fresh, Instant.now()));
        return fresh;
    }

    private void wakeupRegion(String region, double rainMmH) {
        for (DeviceEntity d : deviceRepo.findByRegion(region)) {
            commander.sendWakeup(d.getDeviceId(), rainMmH, region);
        }
    }
}
