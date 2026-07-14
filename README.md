# wellHouse Backend (Spring Boot)

반지하 침수 감지·대응 IoT 백엔드. **Spring Boot 3 / Java 17**.
데이터=자체 DB(JPA), 인증=Spring Security(JWT), 실시간=WebSocket(앱)·MQTT(펌웨어), 푸시=FCM(전용).

## 필요 도구

- **JDK 17+** (필수) — 현재 개발 PC에 미설치. 설치 후 아래 실행.
- Maven (또는 `mvn -N wrapper:wrapper`로 래퍼 생성)
- (선택) Docker — PostgreSQL·Mosquitto 브로커

## 실행

```bash
# 1) 개발: H2 인메모리 + MQTT off, 설치 없이 바로
mvn spring-boot:run                    # 프로파일 dev(기본), http://localhost:8080

# 2) 로직 단위 테스트 (JDK만 있으면 됨)
mvn test

# 3) 운영형: Postgres + Mosquitto
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

데모 기기: dev 부팅 시 `demo-device-01` (pairingCode `123456`, region seoul) 자동 생성.
빠른 확인: 회원가입 → `/api/pair` (deviceId=demo-device-01, code=123456) → `/api/firmware/...` 로 수위 전송.

## 구조

```
backend/
├─ pom.xml                    # Spring Boot 3.3 / Java 17
├─ docker-compose.yml         # postgres + mosquitto
├─ mosquitto/mosquitto.conf
├─ src/main/java/com/wellhouse/backend/
│  ├─ WellHouseApplication.java
│  ├─ domain/risk/            # 순수 로직: RiskEngine · StateMachine · GoldenTime · Thresholds (테스트됨)
│  ├─ entity/                 # JPA 엔티티 + enum(CommandTarget/Status)
│  ├─ repository/             # Spring Data JPA
│  ├─ service/                # RiskEvaluation · Command · Device · Weather · Emergency · Fcm · Auth · Maintenance
│  ├─ messaging/              # MQTT(인바운드 라우터·아웃바운드 커맨더) · WebSocket 퍼블리셔
│  ├─ security/               # JWT(발급·필터·설정)
│  ├─ web/                    # REST 컨트롤러 + dto
│  ├─ schedule/               # @Scheduled 주기 작업
│  └─ config/                 # WebSocket · 개발 시드
├─ src/main/resources/        # application(-dev/-prod).yml
└─ src/test/java/...          # RiskDomainTest
```

## 문서

- **[docs/API.md](docs/API.md)** — REST/MQTT/WebSocket 통신 규약 (펌웨어·앱 연동)
- **[docs/RISK_ENGINE.md](docs/RISK_ENGINE.md)** — 위험도 4단계 산정식·히스테리시스·골든타임

## 미연동 / TODO

- KMA 기상 API 실연동(`WeatherService.fetchRegionWeather`)
- 절전(딥슬립 vs 라이트슬립) 확정, LLM 사후 리포트, 대비점수/침수예측
