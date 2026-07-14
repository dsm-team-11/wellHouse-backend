# wellHouse API / 통신 규약

서버: Spring Boot. 인증: JWT(Bearer). 실시간: 앱=WebSocket(STOMP), 펌웨어=MQTT(REST 폴백).

단계 enum: `0 양호 · 1 주의 · 2 경고 · 3 위험`. 시간: epoch milliseconds.

---

## 1. 인증 (앱)

| Method | Path | Body | 응답 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `{email, password}` | `{uid, accessToken}` |
| POST | `/api/auth/login` | `{email, password}` | `{uid, accessToken}` |

이후 모든 앱 요청 헤더: `Authorization: Bearer <accessToken>`.

## 2. 페어링 (앱 → 펌웨어 토큰 발급)

| Method | Path | Body | 응답 |
| --- | --- | --- | --- |
| POST | `/api/pair` | `{deviceId, pairingCode}` | `{ok, deviceId, deviceToken}` |

`deviceToken` = 펌웨어용 JWT(subject=deviceId, role=DEVICE). 앱이 BLE 등으로 펌웨어에 전달.

## 3. 앱 조회/명령

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/devices` | 내 기기 목록 |
| GET | `/api/devices/{id}/state` | 현재 위험 상태 |
| GET | `/api/devices/{id}/goldenTime` | 골든타임(현재 재계산) |
| GET | `/api/devices/{id}/events` | 이벤트 로그(최근 50) |
| GET | `/api/devices/{id}/commands` | 명령 이력 |
| POST | `/api/devices/{id}/commands` | 원격 명령 `{target: POWER\|WINDOW\|WATER_GATE\|WAKEUP}` |
| GET | `/api/notifications` | 알림함 |
| POST | `/api/notifications/{id}/read` | 읽음 처리 |
| GET/PUT | `/api/users/me`, `/api/users/me/home` | 내 정보/집 정보 |
| POST/DELETE | `/api/users/me/fcm-token` | FCM 토큰 등록/해제 `{token}` |

## 4. 앱 실시간 (WebSocket STOMP)

- 접속: `ws://<host>:8080/ws`
- 구독:
  - `/topic/devices/{deviceId}/state`  → `{level,label,color,raw,riseCmPerMin,updatedAt}`
  - `/topic/devices/{deviceId}/goldenTime` → `{riseSpeedMPerS, primary:{targetCm,seconds,reachable}, bodyEta:[...]}`
  - `/topic/devices/{deviceId}/emergency` → `{status,countdownSec,startedAt}`

---

## 5. 펌웨어 통신 (MQTT, 권장)

브로커: Mosquitto(개발) `tcp://localhost:1883`. 펌웨어는 페어링으로 받은 자격으로 연결.

**펌웨어 → 서버 (publish)**

| 토픽 | 페이로드 |
| --- | --- |
| `devices/{id}/water` | `{ "level_cm": 5.2, "timestamp": 1699999999999 }` |
| `devices/{id}/heartbeat` | `{ "rssi": -60, "timestamp": ... }` (30초 주기) |
| `devices/{id}/power` | `{ "powerState": "on\|cutoff", "source": "auto\|manual", "timestamp": ... }` |
| `devices/{id}/commands/{cmdId}/ack` | `{ "result": "ok\|fail", "detail": "..." }` |

**서버 → 펌웨어 (subscribe)**

| 토픽 | 페이로드 |
| --- | --- |
| `devices/{id}/commands` | `{ cmdId, target, ts, issuedBy, reason }` (10초 내 ack 필요) |
| `devices/{id}/control/wakeup` | `{ command:"wakeup", rainfall_mm_h, region, timestamp }` |

## 6. 펌웨어 통신 (REST 폴백, MQTT 미사용 시)

헤더: `Authorization: Bearer <deviceToken>` (subject가 `{deviceId}`와 일치해야 함).

| Method | Path | Body |
| --- | --- | --- |
| POST | `/api/firmware/{id}/water` | `{levelCm, timestamp}` |
| POST | `/api/firmware/{id}/heartbeat` | `{rssi, timestamp}` |
| POST | `/api/firmware/{id}/commands/{cmdId}/ack` | `{result, detail}` |

> 폴백에선 명령 수신을 위해 펌웨어가 `GET /api/devices/{id}/commands`를 폴링하거나 MQTT를 사용.
