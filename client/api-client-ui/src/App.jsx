import { useEffect, useState } from 'react'

import './App.css'
import { clearStoredAuth, fetchJson, hasStoredAuth, patchJson, postJson, storeBasicAuth } from './utils/api.js'

const COMMANDS = [
  { type: 'LED_ON', label: 'LED 켜기' },
  { type: 'LED_OFF', label: 'LED 끄기' },
  { type: 'NETWORK_STATUS', label: '상태 모드' },
  { type: 'REBOOT', label: '재부팅' },
]

export default function App() {
  const [authReady, setAuthReady] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [loginForm, setLoginForm] = useState({ username: '', password: '' })
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginError, setLoginError] = useState('')
  const [devices, setDevices] = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [deviceState, setDeviceState] = useState(null)
  const [commands, setCommands] = useState([])
  const [telemetry, setTelemetry] = useState([])
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busyCommand, setBusyCommand] = useState('')
  const [metadataForm, setMetadataForm] = useState({ displayName: '', room: '' })
  const [savingMetadata, setSavingMetadata] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [roomFilter, setRoomFilter] = useState('ALL')
  const [showOffline, setShowOffline] = useState(true)

  useEffect(() => {
    validateStoredSession()
  }, [])

  useEffect(() => {
    if (authenticated) {
      loadDevices()
    }
  }, [authenticated])

  useEffect(() => {
    if (!selectedId) {
      return undefined
    }

    loadDeviceDetail(selectedId)
    const timer = window.setInterval(() => loadDeviceDetail(selectedId, { silent: true }), 10000)
    return () => window.clearInterval(timer)
  }, [selectedId])

  const selectedDevice = devices.find((device) => device.deviceId === selectedId) ?? devices[0] ?? null
  const onlineCount = devices.filter((device) => device.online).length
  const avgRssi = getAverageRssi(devices)
  const rawState = parseJson(deviceState?.rawStateJson)
  const roomOptions = getRoomOptions(devices)
  const filteredDevices = devices.filter((device) => {
    const keyword = searchQuery.trim().toLowerCase()
    const matchesKeyword = !keyword || [
      device.name,
      device.displayName,
      device.room,
      device.deviceId,
      device.ip,
    ].some((value) => String(value || '').toLowerCase().includes(keyword))
    const matchesRoom = roomFilter === 'ALL' || (device.room || '미지정') === roomFilter
    const matchesOnline = showOffline || device.online
    return matchesKeyword && matchesRoom && matchesOnline
  })

  useEffect(() => {
    if (!selectedDevice) {
      setMetadataForm({ displayName: '', room: '' })
      return
    }

    setMetadataForm({
      displayName: selectedDevice.displayName || selectedDevice.name || '',
      room: selectedDevice.room || '',
    })
  }, [selectedDevice?.deviceId])

  async function loadDevices() {
    try {
      setLoading(true)
      setError('')
      const response = await fetchJson('/api/devices')
      const nextDevices = (response.data ?? []).map(normalizeDevice)
      setDevices(nextDevices)
      setSelectedId((current) => current || nextDevices[0]?.deviceId || '')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setError(err.message || '장치 목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  async function loadDeviceDetail(deviceId, options = {}) {
    try {
      if (!options.silent) {
        setDetailLoading(true)
      }
      const [stateResponse, commandResponse, telemetryResponse] = await Promise.all([
        fetchJson(`/api/devices/${deviceId}/state`),
        fetchJson(`/api/devices/${deviceId}/commands`),
        fetchJson(`/api/devices/${deviceId}/telemetry`),
      ])
      setDeviceState(normalizeState(stateResponse.data))
      setCommands((commandResponse.data ?? []).map(normalizeCommand))
      setTelemetry((telemetryResponse.data ?? []).map(normalizeTelemetry))
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      if (!options.silent) {
        setMessage(err.message || '장치 상세 정보를 불러오지 못했습니다.')
      }
    } finally {
      if (!options.silent) {
        setDetailLoading(false)
      }
    }
  }

  async function sendCommand(commandType) {
    if (!selectedDevice) {
      return
    }

    try {
      setBusyCommand(commandType)
      setMessage('')
      const response = await postJson(`/api/devices/${selectedDevice.deviceId}/commands`, {
        command_type: commandType,
        payload_json: '{}',
      })
      const command = normalizeCommand(response.data)
      setMessage(`${command.commandType} 명령이 ${formatCommandStatus(command.status)} 상태로 등록되었습니다.`)
      await loadDeviceDetail(selectedDevice.deviceId, { silent: true })
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || '명령 전송에 실패했습니다.')
    } finally {
      setBusyCommand('')
    }
  }

  async function saveMetadata(event) {
    event.preventDefault()
    if (!selectedDevice) {
      return
    }

    try {
      setSavingMetadata(true)
      setMessage('')
      const response = await patchJson(`/api/devices/${selectedDevice.deviceId}/metadata`, {
        display_name: metadataForm.displayName,
        room: metadataForm.room,
      })
      const updated = normalizeDevice(response.data)
      setDevices((current) => current.map((device) => (
        device.deviceId === updated.deviceId ? { ...device, ...updated } : device
      )))
      setMessage('장치 정보가 저장되었습니다.')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || '장치 정보를 저장하지 못했습니다.')
    } finally {
      setSavingMetadata(false)
    }
  }

  async function validateStoredSession() {
    if (!hasStoredAuth()) {
      setAuthReady(true)
      return
    }

    try {
      await fetchJson('/api/auth/me')
      setAuthenticated(true)
    } catch {
      clearStoredAuth()
    } finally {
      setAuthReady(true)
    }
  }

  async function handleLogin(event) {
    event.preventDefault()
    try {
      setLoginLoading(true)
      setLoginError('')
      storeBasicAuth(loginForm.username.trim(), loginForm.password)
      await fetchJson('/api/auth/me')
      setAuthenticated(true)
      setLoginForm({ username: '', password: '' })
    } catch {
      clearStoredAuth()
      setLoginError('아이디 또는 비밀번호가 올바르지 않습니다.')
    } finally {
      setLoginLoading(false)
    }
  }

  function handleLogout() {
    clearStoredAuth()
    setAuthenticated(false)
    setDevices([])
    setSelectedId('')
    setDeviceState(null)
    setCommands([])
    setTelemetry([])
  }

  function handleAuthError(err) {
    if (err?.status !== 401) {
      return false
    }
    handleLogout()
    setLoginError('로그인이 만료되었습니다. 다시 로그인해주세요.')
    return true
  }

  if (!authReady) {
    return (
      <main className="app-shell auth-shell">
        <section className="login-panel">
          <p className="eyebrow">Home IoT Admin</p>
          <h1>로그인 확인 중</h1>
        </section>
      </main>
    )
  }

  if (!authenticated) {
    return (
      <main className="app-shell auth-shell">
        <form className="login-panel" onSubmit={handleLogin}>
          <p className="eyebrow">Home IoT Admin</p>
          <h1>관리자 로그인</h1>
          <p className="hero-copy">
            장치 대시보드와 제어 기능은 관리자 계정으로 로그인한 뒤 사용할 수 있습니다.
          </p>
          {loginError ? <div className="banner error">{loginError}</div> : null}
          <label>
            <span>아이디</span>
            <input
              value={loginForm.username}
              onChange={(event) => setLoginForm((current) => ({ ...current, username: event.target.value }))}
              autoComplete="username"
              required
            />
          </label>
          <label>
            <span>비밀번호</span>
            <input
              type="password"
              value={loginForm.password}
              onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
              autoComplete="current-password"
              required
            />
          </label>
          <button className="refresh-button" disabled={loginLoading}>
            {loginLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">Home IoT Control</p>
          <h1>장치 운영 대시보드</h1>
          <p className="hero-copy">
            등록된 ESP32-S3 보드의 상태를 확인하고, 필요한 하드웨어 명령을 한 화면에서 전송합니다.
          </p>
        </div>
        <div className="hero-actions">
          <button className="refresh-button" onClick={loadDevices} disabled={loading}>
            {loading ? '새로고침 중...' : '장치 새로고침'}
          </button>
          <button className="logout-button" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </section>

      {error ? <div className="banner error">{error}</div> : null}
      {message ? <div className="banner">{message}</div> : null}

      <section className="metric-grid">
        <Metric label="등록 장치" value={devices.length} />
        <Metric label="온라인" value={`${onlineCount}/${devices.length || 0}`} tone="green" />
        <Metric label="평균 RSSI" value={avgRssi == null ? '-' : `${avgRssi} dBm`} />
        <Metric label="선택 장치" value={selectedDevice?.name || '-'} />
      </section>

      <section className="workspace-grid">
        <aside className="device-rail">
          <div className="section-title">
            <span>장치 목록</span>
            <small>{loading ? '불러오는 중' : `${filteredDevices.length}/${devices.length}개`}</small>
          </div>
          <div className="filter-stack">
            <input
              className="filter-input"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="이름, 위치, ID, IP 검색"
            />
            <select
              className="filter-input"
              value={roomFilter}
              onChange={(event) => setRoomFilter(event.target.value)}
            >
              <option value="ALL">전체 위치</option>
              {roomOptions.map((room) => (
                <option value={room} key={room}>{room}</option>
              ))}
            </select>
            <label className="filter-check">
              <input
                type="checkbox"
                checked={showOffline}
                onChange={(event) => setShowOffline(event.target.checked)}
              />
              <span>오프라인 장치 표시</span>
            </label>
          </div>
          {!loading && devices.length === 0 ? <Empty text="등록된 장치가 없습니다." /> : null}
          {!loading && devices.length > 0 && filteredDevices.length === 0 ? <Empty text="조건에 맞는 장치가 없습니다." /> : null}
          <div className="device-list">
            {filteredDevices.map((device) => (
              <button
                key={device.deviceId}
                className={`device-card ${device.deviceId === selectedDevice?.deviceId ? 'active' : ''}`}
                onClick={() => setSelectedId(device.deviceId)}
              >
                <span className={`status-dot ${device.online ? 'online' : 'offline'}`} />
                <span>
                  <strong>{device.name || device.deviceId}</strong>
                  <small>{device.room || '위치 미지정'} / {device.deviceType} / {device.fwVersion || 'FW 정보 없음'}</small>
                </span>
              </button>
            ))}
          </div>
        </aside>

        <section className="detail-panel">
          {!selectedDevice ? (
            <Empty text="상세 정보를 볼 장치를 선택하세요." />
          ) : (
            <>
              <div className="detail-header">
                <div>
                  <p className="eyebrow">선택된 장치</p>
                  <h2>{selectedDevice.name || selectedDevice.deviceId}</h2>
                  <p>{selectedDevice.deviceId}</p>
                </div>
                <span className={`pill ${selectedDevice.online ? 'online' : 'offline'}`}>
                  {selectedDevice.online ? '온라인' : '오프라인'}
                </span>
              </div>

              <div className="info-grid">
                <Info label="IP 주소" value={selectedDevice.ip || '-'} />
                <Info label="RSSI" value={selectedDevice.rssi == null ? '-' : `${selectedDevice.rssi} dBm`} />
                <Info label="위치" value={selectedDevice.room || '-'} />
                <Info label="마지막 응답" value={formatDate(selectedDevice.lastSeenAt)} />
                <Info label="OTA 준비" value={formatBoolean(deviceState?.otaReady, '준비됨', '대기')} />
              </div>

              <form className="metadata-form" onSubmit={saveMetadata}>
                <label>
                  <span>장치 이름</span>
                  <input
                    value={metadataForm.displayName}
                    onChange={(event) => setMetadataForm((current) => ({
                      ...current,
                      displayName: event.target.value,
                    }))}
                    placeholder="예: 거실 메인 보드"
                  />
                </label>
                <label>
                  <span>위치/방</span>
                  <input
                    value={metadataForm.room}
                    onChange={(event) => setMetadataForm((current) => ({
                      ...current,
                      room: event.target.value,
                    }))}
                    placeholder="예: 거실"
                  />
                </label>
                <button className="save-button" disabled={savingMetadata}>
                  {savingMetadata ? '저장 중...' : '정보 저장'}
                </button>
              </form>

              <div className="command-bar">
                {COMMANDS.map((command) => (
                  <button
                    key={command.type}
                    className="command-button"
                    disabled={busyCommand === command.type}
                    onClick={() => sendCommand(command.type)}
                  >
                    {busyCommand === command.type ? '전송 중...' : command.label}
                  </button>
                ))}
              </div>

              <div className="split-grid">
                <Panel title="보고된 상태" loading={detailLoading}>
                  <div className="state-grid">
                    <Info label="LED" value={deviceState?.ledOn == null ? '-' : deviceState.ledOn ? 'ON' : 'OFF'} />
                    <Info label="Wi-Fi" value={formatBoolean(deviceState?.wifiConnected, '연결됨', '끊김')} />
                    <Info label="MQTT" value={formatBoolean(rawState?.mqtt_connected, '연결됨', '끊김')} />
                    <Info label="LED 모드" value={rawState?.led_mode || '-'} />
                  </div>
                  <pre className="raw-box">{deviceState?.rawStateJson || '아직 보고된 상태가 없습니다.'}</pre>
                </Panel>

                <Panel title="최근 명령" loading={detailLoading}>
                  <Timeline
                    items={commands.slice(0, 6).map((command) => ({
                      id: command.commandId,
                      title: command.commandType,
                      subtitle: `${formatCommandStatus(command.status)} / ${formatDate(command.requestedAt)}`,
                    }))}
                    emptyText="아직 등록된 명령이 없습니다."
                  />
                </Panel>
              </div>

              <Panel title="텔레메트리">
                <Timeline
                  items={telemetry.slice(0, 8).map((event) => ({
                    id: event.telemetryId,
                    title: event.eventType,
                    subtitle: `${formatDate(event.createdAt)} / ${event.payloadJson || '{}'}`,
                  }))}
                  emptyText="아직 텔레메트리 이벤트가 없습니다."
                />
              </Panel>
            </>
          )}
        </section>
      </section>
    </main>
  )
}

function Metric({ label, value, tone = 'blue' }) {
  return (
    <div className={`metric-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Panel({ title, loading = false, children }) {
  return (
    <section className="panel">
      <div className="section-title">
        <span>{title}</span>
        {loading ? <small>동기화 중</small> : null}
      </div>
      {children}
    </section>
  )
}

function Info({ label, value }) {
  return (
    <div className="info-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Timeline({ items, emptyText }) {
  if (!items.length) {
    return <Empty text={emptyText} />
  }

  return (
    <div className="timeline">
      {items.map((item) => (
        <div className="timeline-item" key={item.id}>
          <strong>{item.title}</strong>
          <span>{item.subtitle}</span>
        </div>
      ))}
    </div>
  )
}

function Empty({ text }) {
  return <div className="empty-state">{text}</div>
}

function normalizeDevice(device) {
  return {
    deviceId: device?.device_id ?? device?.deviceId ?? '',
    name: device?.name ?? '',
    displayName: device?.display_name ?? device?.displayName ?? '',
    room: device?.room ?? '',
    deviceType: device?.device_type ?? device?.deviceType ?? '',
    fwVersion: device?.fw_version ?? device?.fwVersion ?? '',
    ip: device?.ip ?? '',
    rssi: device?.rssi ?? null,
    online: Boolean(device?.online),
    lastSeenAt: device?.last_seen_at ?? device?.lastSeenAt ?? '',
  }
}

function normalizeState(state) {
  if (!state) {
    return null
  }

  return {
    ledOn: state.led_on ?? state.ledOn ?? null,
    wifiConnected: state.wifi_connected ?? state.wifiConnected ?? null,
    otaReady: state.ota_ready ?? state.otaReady ?? null,
    rawStateJson: state.raw_state_json ?? state.rawStateJson ?? '',
  }
}

function normalizeCommand(command) {
  return {
    commandId: command?.command_id ?? command?.commandId,
    commandType: command?.command_type ?? command?.commandType ?? '',
    payloadJson: command?.payload_json ?? command?.payloadJson ?? '',
    status: command?.status ?? '',
    requestedAt: command?.requested_at ?? command?.requestedAt ?? '',
  }
}

function normalizeTelemetry(event) {
  return {
    telemetryId: event?.telemetry_id ?? event?.telemetryId,
    eventType: event?.event_type ?? event?.eventType ?? '',
    payloadJson: event?.payload_json ?? event?.payloadJson ?? '',
    createdAt: event?.created_at ?? event?.createdAt ?? '',
  }
}

function getAverageRssi(devices) {
  const values = devices.map((device) => device.rssi).filter((value) => typeof value === 'number')
  if (!values.length) {
    return null
  }
  return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length)
}

function getRoomOptions(devices) {
  return Array.from(new Set(devices.map((device) => device.room || '미지정'))).sort((a, b) => a.localeCompare(b, 'ko-KR'))
}

function parseJson(value) {
  if (!value) {
    return null
  }
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function formatBoolean(value, trueText = '예', falseText = '아니오') {
  if (value == null) {
    return '-'
  }
  return value ? trueText : falseText
}

function formatCommandStatus(status) {
  switch (status) {
    case 'PENDING':
      return '대기'
    case 'SENT':
      return '전송됨'
    case 'RECEIVED':
      return '수신됨'
    case 'APPLIED':
      return '적용됨'
    case 'FAILED':
      return '실패'
    default:
      return status || '-'
  }
}
