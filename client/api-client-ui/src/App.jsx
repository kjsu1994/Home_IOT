import { useEffect, useState } from 'react'

import { fetchJson, postJson } from './utils/api.js'

export default function App() {
  const [devices, setDevices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busyKey, setBusyKey] = useState('')

  useEffect(() => {
    loadDevices()
  }, [])

  async function loadDevices() {
    try {
      setLoading(true)
      setError('')
      const response = await fetchJson('/api/devices')
      setDevices(response.data ?? [])
    } catch (err) {
      setError(err.message || '장치 목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  async function sendCommand(deviceId, commandType) {
    const key = `${deviceId}:${commandType}`
    try {
      setBusyKey(key)
      setMessage('')
      const response = await postJson(`/api/devices/${deviceId}/commands`, {
        command_type: commandType,
        payload_json: '{}',
      })
      setMessage(`${deviceId} -> ${response.data.commandType ?? response.data.command_type} ${response.data.status}`)
    } catch (err) {
      setMessage(err.message || '명령 전송 실패')
    } finally {
      setBusyKey('')
    }
  }

  return (
    <main style={{ fontFamily: 'Segoe UI, sans-serif', background: '#f4f7fb', minHeight: '100vh', padding: '32px' }}>
      <section style={{ maxWidth: '980px', margin: '0 auto' }}>
        <header style={{ marginBottom: '24px' }}>
          <div style={{ fontSize: '12px', fontWeight: 700, letterSpacing: '0.08em', color: '#4f6b8a' }}>HOME_IOT</div>
          <h1 style={{ margin: '8px 0 0', fontSize: '36px', color: '#132238' }}>Device Dashboard</h1>
          <p style={{ margin: '12px 0 0', color: '#506070' }}>장치 목록 조회와 기본 제어 명령을 위한 초기 대시보드입니다.</p>
        </header>

        <div style={{ display: 'flex', gap: '12px', marginBottom: '20px', alignItems: 'center', flexWrap: 'wrap' }}>
          <button
            onClick={loadDevices}
            style={{ background: '#132238', color: '#fff', border: 0, borderRadius: '12px', padding: '10px 14px', fontWeight: 700, cursor: 'pointer' }}
          >
            새로고침
          </button>
          {message ? <span style={{ color: '#35506b', fontWeight: 600 }}>{message}</span> : null}
        </div>

        <section style={{ display: 'grid', gap: '16px' }}>
          {loading ? <PanelText text="장치 목록을 불러오는 중입니다." /> : null}
          {!loading && error ? <PanelText text={error} tone="error" /> : null}
          {!loading && !error && devices.length === 0 ? <PanelText text="등록된 장치가 없습니다." /> : null}
          {!loading && !error && devices.map((device) => (
            <article
              key={device.deviceId}
              style={{
                background: '#ffffff',
                borderRadius: '20px',
                padding: '20px',
                boxShadow: '0 18px 40px rgba(22, 38, 64, 0.08)',
                border: '1px solid #dde6f0',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: '22px', fontWeight: 800, color: '#132238' }}>{device.name}</div>
                  <div style={{ marginTop: '6px', color: '#607086' }}>{device.deviceId}</div>
                </div>
                <span
                  style={{
                    background: device.online ? '#dcfce7' : '#fee2e2',
                    color: device.online ? '#166534' : '#991b1b',
                    borderRadius: '999px',
                    padding: '8px 14px',
                    fontSize: '13px',
                    fontWeight: 700,
                  }}
                >
                  {device.online ? 'ONLINE' : 'OFFLINE'}
                </span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '12px', marginTop: '18px' }}>
                <Info label="IP" value={device.ip || '-'} />
                <Info label="RSSI" value={device.rssi == null ? '-' : String(device.rssi)} />
                <Info label="FW" value={device.fwVersion || device.fw_version || '-'} />
                <Info label="마지막 응답" value={device.lastSeenAt || device.last_seen_at || '-'} />
              </div>

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', marginTop: '18px' }}>
                <CommandButton
                  busy={busyKey === `${device.deviceId}:LED_ON`}
                  label="LED ON"
                  onClick={() => sendCommand(device.deviceId, 'LED_ON')}
                />
                <CommandButton
                  busy={busyKey === `${device.deviceId}:LED_OFF`}
                  label="LED OFF"
                  onClick={() => sendCommand(device.deviceId, 'LED_OFF')}
                />
                <CommandButton
                  busy={busyKey === `${device.deviceId}:REBOOT`}
                  label="REBOOT"
                  onClick={() => sendCommand(device.deviceId, 'REBOOT')}
                />
              </div>
            </article>
          ))}
        </section>
      </section>
    </main>
  )
}

function CommandButton({ busy, label, onClick }) {
  return (
    <button
      onClick={onClick}
      disabled={busy}
      style={{
        background: busy ? '#94a3b8' : '#132238',
        color: '#fff',
        border: 0,
        borderRadius: '12px',
        padding: '10px 14px',
        fontWeight: 700,
        cursor: busy ? 'default' : 'pointer',
      }}
    >
      {busy ? '전송중...' : label}
    </button>
  )
}

function PanelText({ text, tone = 'normal' }) {
  return (
    <div
      style={{
        background: '#ffffff',
        borderRadius: '16px',
        padding: '18px 20px',
        border: `1px solid ${tone === 'error' ? '#fecaca' : '#dde6f0'}`,
        color: tone === 'error' ? '#b91c1c' : '#4b5f74',
        fontWeight: 600,
      }}
    >
      {text}
    </div>
  )
}

function Info({ label, value }) {
  return (
    <div style={{ background: '#f8fbff', borderRadius: '16px', padding: '14px 16px', border: '1px solid #e4edf7' }}>
      <div style={{ fontSize: '12px', fontWeight: 700, color: '#68809b' }}>{label}</div>
      <div style={{ marginTop: '6px', fontSize: '16px', fontWeight: 700, color: '#132238', wordBreak: 'break-word' }}>{value}</div>
    </div>
  )
}
