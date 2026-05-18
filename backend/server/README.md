# Home_IOT Spring Boot Server

## Package Direction
- `com.homeiot.common`
- `com.homeiot.device`
- `com.homeiot.telemetry`
- `com.homeiot.command`
- `com.homeiot.ota`
- `com.homeiot.mqtt`
- `com.homeiot.auth`

## Implemented Endpoints
- `POST /api/devices/register`
- `GET /api/devices`
- `GET /api/devices/{deviceId}`
- `PATCH /api/devices/{deviceId}/metadata`
- `GET /api/devices/{deviceId}/state`
- `POST /api/devices/{deviceId}/health`
- `POST /api/devices/{deviceId}/state/report`
- `POST /api/devices/{deviceId}/commands`
- `GET /api/devices/{deviceId}/commands`
- `GET /api/devices/{deviceId}/commands/pending`
- `PATCH /api/devices/{deviceId}/commands/{commandId}`
- `POST /api/devices/{deviceId}/telemetry`
- `GET /api/devices/{deviceId}/telemetry`
- `POST /api/devices/{deviceId}/ota/events`
- `POST /api/devices/{deviceId}/ota/status`

## Next Build Steps
1. add MQTT subscriber for reported state and health events
2. add persisted database migration scripts for non-H2 deployments
3. add authentication once device tokens and user accounts are defined
