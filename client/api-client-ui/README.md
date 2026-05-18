# Home_IOT UI

## Local
```powershell
npm run dev
```

Local API proxy uses `/api -> http://localhost:8080`.

The dashboard requires the backend admin account:
- `APP_ADMIN_USERNAME`
- `APP_ADMIN_PASSWORD`

## Shared ctfmon domain build
Use this when serving Home_IOT under `https://ctfmon.shop/iot/` while keeping ctfmon `/api` untouched.

```powershell
$env:VITE_BASE_PATH="/iot/"
$env:VITE_API_BASE_PATH="/iot-api"
npm run build
```

Required public routes:
- `/iot/*` -> Home_IOT frontend
- `/iot-api/*` -> Home_IOT backend `/api/*`

Do not route Home_IOT through public `/api`; that path belongs to ctfmon.

Before public deployment:
- Set a strong backend `APP_ADMIN_PASSWORD`.
- Set backend `APP_DEVICE_API_KEY` as the device bootstrap key.
- Configure firmware `HOME_IOT_DEVICE_API_KEY` with that bootstrap key for first registration. After registration, each board stores its own random device key.
