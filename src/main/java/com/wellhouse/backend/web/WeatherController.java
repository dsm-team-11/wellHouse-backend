package com.wellhouse.backend.web;

import com.wellhouse.backend.entity.UserEntity;
import com.wellhouse.backend.repository.UserRepository;
import com.wellhouse.backend.service.WeatherService;
import com.wellhouse.backend.service.weather.AddressGrid;
import com.wellhouse.backend.service.weather.KmaWeatherClient;
import com.wellhouse.backend.service.weather.WeatherSnapshot;
import com.wellhouse.backend.web.dto.WeatherDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** 앱용 날씨 조회. 회원가입 시 저장한 주소(기상청 격자) 기준. */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final UserRepository userRepo;
    private final WeatherService weatherService;
    private final KmaWeatherClient kma;

    /** 내 주소 기준 현재 기상. 주소 미등록 시 서울 기본값. */
    @GetMapping("/me")
    public WeatherDto myWeather(Authentication auth) {
        UserEntity u = userRepo.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 없음"));

        AddressGrid.Location loc = AddressGrid.fromAddress(u.getAddress());
        WeatherSnapshot snapshot = weatherService.fetchByGrid(loc.cell().nx(), loc.cell().ny());
        return WeatherDto.of(loc.label(), snapshot, kma.enabled(), Instant.now().toEpochMilli());
    }
}
