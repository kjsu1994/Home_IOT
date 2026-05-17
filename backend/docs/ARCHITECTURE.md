# Home_IOT Backend Architecture

## Domain Split
- `device`: 장치 등록, 기본 메타데이터, 최신 연결 상태
- `telemetry`: 시계열 이벤트 저장
- `command`: 제어 명령 생성, 상태 추적
- `ota`: OTA 이벤트 기록
- `mqtt`: topic subscribe/publish
- `auth`: 관리자 인증

## Communication Model
- REST: register / query / history
- MQTT: realtime command / reported / health / telemetry

## Initial REST APIs
- `POST /api/devices/register`
- `GET /api/devices`

## Register Payload Example
```json
{
  "device_id": "esp32s3-001",
  "name": "거실 테스트 보드",
  "device_type": "ESP32_S3",
  "fw_version": "0.1.0",
  "ip": "192.168.123.116",
  "rssi": -59
}
```
