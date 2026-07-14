package com.wellhouse.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** wellHouse 침수 대응 백엔드 진입점. */
@SpringBootApplication
@EnableScheduling
public class WellHouseApplication {
    public static void main(String[] args) {
        SpringApplication.run(WellHouseApplication.class, args);
    }
}
