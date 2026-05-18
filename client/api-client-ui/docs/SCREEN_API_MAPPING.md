# Screen API Mapping

## Dashboard
- `GET /api/devices`

## Device Detail
- `GET /api/devices/{device_id}`
- `PATCH /api/devices/{device_id}/metadata`
- `GET /api/devices/{device_id}/state`
- `GET /api/devices/{device_id}/telemetry`
- `GET /api/devices/{device_id}/commands`

## Command Panel
- `POST /api/devices/{device_id}/commands`
- `GET /api/devices/{device_id}/commands/pending`
- `PATCH /api/devices/{device_id}/commands/{command_id}`

## OTA Status
- `POST /api/devices/{device_id}/ota/events`
- `POST /api/devices/{device_id}/ota/status`
