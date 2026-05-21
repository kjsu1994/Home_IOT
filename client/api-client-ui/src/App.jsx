import { useEffect, useRef, useState } from 'react'

import './App.css'
import { clearStoredAuth, fetchJson, hasStoredAuth, patchJson, postJson, resolveApiPath, storeTokens } from './utils/api.js'
import { createQrSvgDataUri } from './utils/qr.js'

const PENDING_CLAIM_KEY = 'home_iot_pending_claim'

const UI = {
  shareManagement: '\uacf5\uc720 \uad00\ub9ac',
  sharedUsers: '\uacf5\uc720 \uc0ac\uc6a9\uc790',
  directAssign: '\uc9c1\uc811 \ubc30\uc815',
  userEmail: '\uc0ac\uc6a9\uc790 \uc774\uba54\uc77c',
  assign: '\ubc30\uc815',
  assigning: '\ubc30\uc815 \uc911...',
  removeShare: '\uacf5\uc720 \ud574\uc81c',
  role: '\uad8c\ud55c',
  method: '\ub4f1\ub85d \ubc29\uc2dd',
  sharedAt: '\uacf5\uc720\uc77c',
  owner: '\uc18c\uc720\uc790',
  qr: 'QR',
  adminAssign: '\uad00\ub9ac\uc790 \ubc30\uc815',
  qrHistory: 'QR \uc774\ub825',
  status: '\uc0c1\ud0dc',
  issuer: '\ubc1c\uae09\uc790',
  usedBy: '\uc0ac\uc6a9\uc790',
  issuedAt: '\ubc1c\uae09\uc77c',
  expiresAt: '\ub9cc\ub8cc\uc77c',
  revoke: '\ucde8\uc18c',
  available: '\uc0ac\uc6a9 \uac00\ub2a5',
  used: '\uc0ac\uc6a9\ub428',
  expired: '\ub9cc\ub8cc\ub428',
  revoked: '\ucde8\uc18c\ub428',
  noShares: '\uacf5\uc720 \uc0ac\uc6a9\uc790\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.',
  noQrHistory: 'QR \uc774\ub825\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.',
  assignPlaceholder: '\uac00\uc785\ub41c \uc0ac\uc6a9\uc790 \uc774\uba54\uc77c',
  assignSaved: '\uc0ac\uc6a9\uc790\uc5d0\uac8c \uc7a5\uce58\ub97c \ubc30\uc815\ud588\uc2b5\ub2c8\ub2e4.',
  assignFailed: '\uc0ac\uc6a9\uc790 \ubc30\uc815\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4.',
  removeDone: '\uacf5\uc720\ub97c \ud574\uc81c\ud588\uc2b5\ub2c8\ub2e4.',
  removeFailed: '\uacf5\uc720\ub97c \ud574\uc81c\ud558\uc9c0 \ubabb\ud588\uc2b5\ub2c8\ub2e4.',
  revokeDone: 'QR\uc744 \ucde8\uc18c\ud588\uc2b5\ub2c8\ub2e4.',
  revokeFailed: 'QR\uc744 \ucde8\uc18c\ud558\uc9c0 \ubabb\ud588\uc2b5\ub2c8\ub2e4.',
  familyShare: '\uac00\uc871 \uacf5\uc720',
  pendingInvite: '\ucd08\ub300 \ub300\uae30',
  inviteExpires: '\ucd08\ub300 \ub9cc\ub8cc',
  inviteHint: '\ubbf8\uac00\uc785 \uc774\uba54\uc77c\uc740 \ucd08\ub300 \uba54\uc77c\uc744 \ubcf4\ub0b4\uace0, \uac00\uc785/\ub85c\uadf8\uc778 \uc2dc \uc790\ub3d9\uc73c\ub85c \uacf5\uc720\ub429\ub2c8\ub2e4.',
  viewer: '\ubcf4\uae30 \uc804\uc6a9',
  controller: '\uc81c\uc5b4 \uac00\ub2a5',
  manager: '\uad00\ub9ac\uc790',
  auditLog: '\uac10\uc0ac \ub85c\uadf8',
  noAuditLogs: '\uc544\uc9c1 \uae30\ub85d\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.',
  personalAlias: '\ub0b4 \uc7a5\uce58 \ubcc4\uce6d',
  personalAliasSaved: '\ub0b4 \uc7a5\uce58 \ubcc4\uce6d\uc744 \uc800\uc7a5\ud588\uc2b5\ub2c8\ub2e4.',
  saveAlias: '\ubcc4\uce6d \uc800\uc7a5',
  homeGroup: '\uc9d1/\uac00\uc871 \uadf8\ub8f9',
  createHome: '\uc9d1 \ub9cc\ub4e4\uae30',
  assignHome: '\uc120\ud0dd \uc7a5\uce58\ub97c \uc9d1\uc5d0 \ucd94\uac00',
  addFamily: '\uac00\uc871 \ucd94\uac00',
}

const COMMANDS = [
  { type: 'LED_ON', label: 'LED 켜기' },
  { type: 'LED_OFF', label: 'LED 끄기' },
  { type: 'NETWORK_STATUS', label: '상태 모드' },
  { type: 'REBOOT', label: '재부팅' },
]

export default function App() {
  const [authReady, setAuthReady] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [authMode, setAuthMode] = useState('login')
  const [loginForm, setLoginForm] = useState({ email: '', password: '' })
  const [signupForm, setSignupForm] = useState({ email: '', name: '', password: '', code: '' })
  const [resetForm, setResetForm] = useState({ email: '', code: '', newPassword: '' })
  const [verificationSent, setVerificationSent] = useState(false)
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
  const [claimForm, setClaimForm] = useState({ deviceId: '', deviceApiKey: '', displayName: '', room: '' })
  const [claimingDevice, setClaimingDevice] = useState(false)
  const [pendingClaim, setPendingClaim] = useState(() => readPendingClaim())
  const [claimQr, setClaimQr] = useState(null)
  const [claimQrLoading, setClaimQrLoading] = useState(false)
  const [shareUsers, setShareUsers] = useState([])
  const [claimHistory, setClaimHistory] = useState([])
  const [auditLogs, setAuditLogs] = useState([])
  const [homes, setHomes] = useState([])
  const [shareLoading, setShareLoading] = useState(false)
  const [assignEmail, setAssignEmail] = useState('')
  const [assignRole, setAssignRole] = useState('CONTROLLER')
  const [personalForm, setPersonalForm] = useState({ displayName: '', room: '' })
  const [homeForm, setHomeForm] = useState({ name: '', memberEmail: '', homeId: '' })
  const [assigningShare, setAssigningShare] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const [accountOpen, setAccountOpen] = useState(false)
  const [accountForm, setAccountForm] = useState({ name: '' })
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '' })
  const [savingAccount, setSavingAccount] = useState(false)
  const [savingPassword, setSavingPassword] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [roomFilter, setRoomFilter] = useState('ALL')
  const [showOffline, setShowOffline] = useState(true)
  const [activeView, setActiveView] = useState('dashboard')
  const [cameras, setCameras] = useState([])
  const [selectedCameraId, setSelectedCameraId] = useState('')
  const [cameraStreamPath, setCameraStreamPath] = useState('')
  const [cameraRecordings, setCameraRecordings] = useState([])
  const [cameraLoading, setCameraLoading] = useState(false)
  const [cameraRecording, setCameraRecording] = useState(false)

  useEffect(() => {
    if (consumeOAuthCallback()) {
      return
    }
    validateStoredSession()
  }, [])

  useEffect(() => {
    if (authenticated) {
      loadDevices()
      loadCameras()
    }
  }, [authenticated])

  useEffect(() => {
    if (!authenticated || activeView !== 'video') {
      return undefined
    }

    loadCameras()
    const timer = window.setInterval(loadCameras, 10000)
    return () => window.clearInterval(timer)
  }, [authenticated, activeView])

  useEffect(() => {
    if (authenticated && pendingClaim?.deviceId && pendingClaim?.claimToken) {
      claimPendingDevice(pendingClaim)
    }
  }, [authenticated, pendingClaim?.deviceId, pendingClaim?.claimToken])

  useEffect(() => {
    if (!selectedId) {
      return undefined
    }

    loadDeviceDetail(selectedId)
    const timer = window.setInterval(() => loadDeviceDetail(selectedId, { silent: true }), 10000)
    return () => window.clearInterval(timer)
  }, [selectedId])

  useEffect(() => {
    if (!selectedCameraId || activeView !== 'video') {
      setCameraStreamPath('')
      return
    }
    issueCameraStreamToken(selectedCameraId)
    loadCameraRecordings(selectedCameraId)
  }, [selectedCameraId, activeView])

  const selectedDevice = devices.find((device) => device.deviceId === selectedId) ?? devices[0] ?? null
  const selectedCamera = cameras.find((camera) => camera.deviceId === selectedCameraId) ?? cameras[0] ?? null
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

  const currentUserName = currentUser?.name || currentUser?.email || '사용자'
  const currentUserInitial = (currentUserName || 'U').charAt(0).toUpperCase()
  const isMasterAccount = currentUser?.roles?.includes('ROLE_MASTER')
  const canManageSharing = Boolean(selectedDevice?.shareManager || isMasterAccount)
  useEffect(() => {
    if (!selectedDevice) {
      setMetadataForm({ displayName: '', room: '' })
      setPersonalForm({ displayName: '', room: '' })
      setShareUsers([])
      setClaimHistory([])
      setAuditLogs([])
      return
    }

    setMetadataForm({
      displayName: selectedDevice.displayName || selectedDevice.name || '',
      room: selectedDevice.room || '',
    })
    setPersonalForm({
      displayName: selectedDevice.personalDisplayName || '',
      room: selectedDevice.personalRoom || '',
    })

    if (canManageSharing) {
      loadShareManagement(selectedDevice.deviceId)
    } else {
      setShareUsers([])
      setClaimHistory([])
      setAuditLogs([])
    }
  }, [selectedDevice?.deviceId, canManageSharing])

  async function loadDevices() {
    try {
      setLoading(true)
      setError('')
      const response = await fetchJson('/api/devices')
      const nextDevices = (response.data ?? []).map(normalizeDevice)
      setDevices(nextDevices)
      setSelectedId((current) => current || nextDevices[0]?.deviceId || '')
      loadHomes()
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setError(err.message || '장치 목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  async function loadHomes() {
    try {
      const response = await fetchJson('/api/homes')
      setHomes((response.data ?? []).map(normalizeHome))
    } catch {
      setHomes([])
    }
  }

  async function loadCameras() {
    try {
      setCameraLoading(true)
      const response = await fetchJson('/api/cameras')
      const nextCameras = (response.data ?? []).map(normalizeCamera)
      setCameras(nextCameras)
      setSelectedCameraId((current) => current || nextCameras[0]?.deviceId || '')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || 'Failed to load cameras.')
    } finally {
      setCameraLoading(false)
    }
  }

  async function issueCameraStreamToken(deviceId) {
    try {
      const response = await postJson(`/api/cameras/${deviceId}/stream-token`, {})
      setCameraStreamPath(resolveApiPath(response.data?.stream_path || response.data?.streamPath || ''))
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setCameraStreamPath('')
      setMessage(err.message || 'Failed to open camera stream.')
    }
  }

  async function loadCameraRecordings(deviceId) {
    try {
      const response = await fetchJson(`/api/cameras/${deviceId}/recordings`)
      const recordings = (response.data ?? []).map(normalizeCameraRecording)
      setCameraRecordings(recordings)
      setCameraRecording(recordings.some((recording) => recording.active))
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setCameraRecordings([])
    }
  }

  async function startCameraRecording() {
    if (!selectedCamera) {
      return
    }
    try {
      setMessage('')
      await postJson(`/api/cameras/${selectedCamera.deviceId}/recordings/start`, {})
      await loadCameraRecordings(selectedCamera.deviceId)
      setMessage('Camera recording started.')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || 'Failed to start camera recording.')
    }
  }

  async function stopCameraRecording() {
    if (!selectedCamera) {
      return
    }
    try {
      setMessage('')
      await postJson(`/api/cameras/${selectedCamera.deviceId}/recordings/stop`, {})
      await loadCameraRecordings(selectedCamera.deviceId)
      setMessage('Camera recording stopped.')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || 'Failed to stop camera recording.')
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

  async function loadShareManagement(deviceId) {
    try {
      setShareLoading(true)
      const [sharesResponse, historyResponse, auditsResponse] = await Promise.all([
        fetchJson(`/api/devices/${deviceId}/shares`),
        fetchJson(`/api/devices/${deviceId}/claim-tokens`),
        fetchJson(`/api/devices/${deviceId}/audits`),
      ])
      setShareUsers((sharesResponse.data ?? []).map(normalizeShare))
      setClaimHistory((historyResponse.data ?? []).map(normalizeClaimHistory))
      setAuditLogs((auditsResponse.data ?? []).map(normalizeAuditLog))
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || 'Failed to load device sharing data.')
    } finally {
      setShareLoading(false)
    }
  }

  async function assignDeviceShare(event) {
    event.preventDefault()
    if (!selectedDevice) {
      return
    }
    try {
      setAssigningShare(true)
      setMessage('')
      await postJson(`/api/devices/${selectedDevice.deviceId}/shares`, { email: assignEmail.trim(), role: assignRole })
      setAssignEmail('')
      await loadShareManagement(selectedDevice.deviceId)
      setMessage(UI.assignSaved)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.assignFailed)
    } finally {
      setAssigningShare(false)
    }
  }

  async function removeDeviceShare(memberId) {
    if (!selectedDevice) {
      return
    }
    try {
      setMessage('')
      const share = shareUsers.find((item) => item.memberId === memberId || item.inviteId === memberId)
      const targetPath = share?.status === 'PENDING'
        ? `/api/devices/${selectedDevice.deviceId}/share-invites/${share.inviteId}`
        : `/api/devices/${selectedDevice.deviceId}/shares/${memberId}`
      await fetchJson(targetPath, { method: 'DELETE' })
      await loadShareManagement(selectedDevice.deviceId)
      setMessage(UI.removeDone)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.removeFailed)
    }
  }

  async function revokeClaimToken(tokenId) {
    if (!selectedDevice) {
      return
    }
    try {
      setMessage('')
      await postJson(`/api/devices/${selectedDevice.deviceId}/claim-tokens/${tokenId}/revoke`, {})
      await loadShareManagement(selectedDevice.deviceId)
      setMessage(UI.revokeDone)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.revokeFailed)
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

  async function savePersonalPreference(event) {
    event.preventDefault()
    if (!selectedDevice) {
      return
    }
    try {
      setMessage('')
      const response = await patchJson(`/api/devices/${selectedDevice.deviceId}/preference`, {
        display_name: personalForm.displayName,
        room: personalForm.room,
      })
      const updated = normalizeDevice(response.data)
      setDevices((current) => current.map((device) => (
        device.deviceId === updated.deviceId ? { ...device, ...updated } : device
      )))
      setMessage(UI.personalAliasSaved)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.assignFailed)
    }
  }

  async function createHome(event) {
    event.preventDefault()
    try {
      setMessage('')
      await postJson('/api/homes', { name: homeForm.name.trim() })
      setHomeForm((current) => ({ ...current, name: '' }))
      await loadHomes()
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.assignFailed)
    }
  }

  async function assignDeviceHome() {
    if (!selectedDevice || !homeForm.homeId) {
      return
    }
    try {
      setMessage('')
      await postJson(`/api/homes/${homeForm.homeId}/devices/${selectedDevice.deviceId}`, {})
      await Promise.all([loadHomes(), loadDevices()])
      setMessage(UI.assignSaved)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.assignFailed)
    }
  }

  async function addHomeMember(event) {
    event.preventDefault()
    if (!homeForm.homeId) {
      return
    }
    try {
      setMessage('')
      await postJson(`/api/homes/${homeForm.homeId}/members`, { email: homeForm.memberEmail.trim(), role: assignRole })
      setHomeForm((current) => ({ ...current, memberEmail: '' }))
      await Promise.all([loadHomes(), selectedDevice ? loadShareManagement(selectedDevice.deviceId) : Promise.resolve()])
      setMessage(UI.assignSaved)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || UI.assignFailed)
    }
  }

  async function claimPendingDevice(claim) {
    try {
      setClaimingDevice(true)
      setMessage('')
      const response = await postJson('/api/devices/claim', {
        device_id: claim.deviceId,
        claim_token: claim.claimToken,
      })
      const claimed = normalizeDevice(response.data)
      clearPendingClaim()
      setPendingClaim(null)
      await loadDevices()
      setSelectedId(claimed.deviceId)
      setMessage('QR로 장치가 내 계정에 등록되었습니다.')
    } catch (err) {
      clearPendingClaim()
      setPendingClaim(null)
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || 'QR 장치 등록에 실패했습니다. 마스터에게 새 QR을 요청해 주세요.')
    } finally {
      setClaimingDevice(false)
    }
  }

  async function issueClaimQr() {
    if (!selectedDevice) {
      return
    }

    try {
      setClaimQrLoading(true)
      setMessage('')
      const response = await postJson(`/api/devices/${selectedDevice.deviceId}/claim-token`, {})
      const claimToken = response.data?.claim_token || response.data?.claimToken
      const claimUrl = buildClaimUrl(selectedDevice.deviceId, claimToken)
      setClaimQr({
        deviceId: selectedDevice.deviceId,
        deviceName: selectedDevice.name || selectedDevice.deviceId,
        claimUrl,
        qrSrc: createQrSvgDataUri(claimUrl),
        expiresAt: response.data?.expires_at || response.data?.expiresAt || '',
      })
      await loadShareManagement(selectedDevice.deviceId)
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || '등록 QR을 발급하지 못했습니다.')
    } finally {
      setClaimQrLoading(false)
    }
  }

  async function copyClaimUrl() {
    if (!claimQr?.claimUrl || !navigator.clipboard) {
      return
    }
    await navigator.clipboard.writeText(claimQr.claimUrl)
    setMessage('등록 링크를 복사했습니다.')
  }

  async function claimDevice(event) {
    event.preventDefault()
    try {
      setClaimingDevice(true)
      setMessage('')
      const response = await postJson('/api/devices/claim', {
        device_id: claimForm.deviceId.trim(),
        device_api_key: claimForm.deviceApiKey.trim(),
        display_name: claimForm.displayName,
        room: claimForm.room,
      })
      const claimed = normalizeDevice(response.data)
      await loadDevices()
      setSelectedId(claimed.deviceId)
      setClaimForm({ deviceId: '', deviceApiKey: '', displayName: '', room: '' })
      setMessage('장치가 내 계정에 등록되었습니다.')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || '장치를 내 계정에 등록하지 못했습니다.')
    } finally {
      setClaimingDevice(false)
    }
  }
  async function loadCurrentUser() {
    const response = await fetchJson('/api/members/me')
    const user = normalizeSession(response.data)
    setCurrentUser(user)
    setAccountForm({ name: user.name || '' })
    return user
  }
  async function validateStoredSession() {
    if (!hasStoredAuth()) {
      setAuthReady(true)
      return
    }

    try {
      await loadCurrentUser()
      setAuthenticated(true)
    } catch {
      clearStoredAuth()
    } finally {
      setAuthReady(true)
    }
  }

  function consumeOAuthCallback() {
    const params = new URLSearchParams(window.location.search)
    const accessToken = params.get('access_token') || params.get('accessToken')
    const refreshToken = params.get('refresh_token') || params.get('refreshToken')
    if (!accessToken || !refreshToken) {
      return false
    }
    storeTokens(accessToken, refreshToken)
    window.history.replaceState({}, document.title, window.location.pathname)
    setAuthReady(false)
    loadCurrentUser()
      .then(() => setAuthenticated(true))
      .catch(() => {
        clearStoredAuth()
        setAuthenticated(false)
      })
      .finally(() => setAuthReady(true))
    return true
  }

  async function handleLogin(event) {
    event.preventDefault()
    try {
      setLoginLoading(true)
      setLoginError('')
      const response = await postJson('/api/auth/login', {
        email: loginForm.email.trim(),
        password: loginForm.password,
      }, { auth: false })
      storeTokens(response.data?.access_token || response.data?.accessToken, response.data?.refresh_token || response.data?.refreshToken)
      await loadCurrentUser()
      setAuthenticated(true)
      setLoginForm({ email: '', password: '' })
    } catch {
      clearStoredAuth()
      setLoginError('이메일 또는 비밀번호가 올바르지 않습니다.')
    } finally {
      setLoginLoading(false)
    }
  }

  async function sendSignupCode() {
    try {
      setLoginLoading(true)
      setLoginError('')
      await postJson('/api/members/email-verification/send', { email: signupForm.email.trim() }, { auth: false })
      setVerificationSent(true)
      setLoginError('인증 코드가 발송되었습니다.')
    } catch (err) {
      setLoginError(err.message || '인증 코드 발송에 실패했습니다.')
    } finally {
      setLoginLoading(false)
    }
  }

  async function handleSignup(event) {
    event.preventDefault()
    try {
      setLoginLoading(true)
      setLoginError('')
      await postJson('/api/members/email-verification/confirm', {
        email: signupForm.email.trim(),
        code: signupForm.code.trim(),
      }, { auth: false })
      await postJson('/api/members', {
        email: signupForm.email.trim(),
        name: signupForm.name.trim(),
        password: signupForm.password,
      }, { auth: false })
      setAuthMode('login')
      setLoginForm({ email: signupForm.email.trim(), password: '' })
      setSignupForm({ email: '', name: '', password: '', code: '' })
      setVerificationSent(false)
      setLoginError('회원가입이 완료되었습니다. 로그인해주세요.')
    } catch (err) {
      setLoginError(err.message || '회원가입에 실패했습니다.')
    } finally {
      setLoginLoading(false)
    }
  }

  async function requestPasswordReset() {
    try {
      setLoginLoading(true)
      setLoginError('')
      await postJson('/api/auth/password-reset/request', { email: resetForm.email.trim() }, { auth: false })
      setLoginError('비밀번호 재설정 코드가 발송되었습니다.')
    } catch (err) {
      setLoginError(err.message || '비밀번호 재설정 요청에 실패했습니다.')
    } finally {
      setLoginLoading(false)
    }
  }

  async function handlePasswordReset(event) {
    event.preventDefault()
    try {
      setLoginLoading(true)
      setLoginError('')
      await postJson('/api/auth/password-reset/confirm', {
        email: resetForm.email.trim(),
        code: resetForm.code.trim(),
        new_password: resetForm.newPassword,
        newPassword: resetForm.newPassword,
      }, { auth: false })
      setAuthMode('login')
      setLoginForm({ email: resetForm.email.trim(), password: '' })
      setResetForm({ email: '', code: '', newPassword: '' })
      setLoginError('비밀번호가 변경되었습니다. 다시 로그인해주세요.')
    } catch (err) {
      setLoginError(err.message || '비밀번호 변경에 실패했습니다.')
    } finally {
      setLoginLoading(false)
    }
  }

  async function saveAccount(event) {
    event.preventDefault()
    try {
      setSavingAccount(true)
      setMessage('')
      const response = await patchJson('/api/members/me', { name: accountForm.name.trim() })
      const updated = normalizeSession(response.data)
      setCurrentUser(updated)
      setAccountForm({ name: updated.name || '' })
      setMessage('계정 정보가 변경되었습니다.')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || '계정 정보를 변경하지 못했습니다.')
    } finally {
      setSavingAccount(false)
    }
  }

  async function changePassword(event) {
    event.preventDefault()
    try {
      setSavingPassword(true)
      setMessage('')
      await patchJson('/api/members/me/password', {
        currentPassword: passwordForm.currentPassword,
        current_password: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
        new_password: passwordForm.newPassword,
      })
      setPasswordForm({ currentPassword: '', newPassword: '' })
      setMessage('비밀번호가 변경되었습니다.')
    } catch (err) {
      if (handleAuthError(err)) {
        return
      }
      setMessage(err.message || '비밀번호를 변경하지 못했습니다.')
    } finally {
      setSavingPassword(false)
    }
  }
  function startOAuth(provider) {
    window.location.href = resolveApiPath(`/api/oauth2/authorization/${provider}`)
  }

  async function handleLogout() {
    try {
      await postJson('/api/auth/logout', {})
    } catch {
      // local logout should still clear tokens if the server is unavailable.
    }
    clearStoredAuth()
    setAuthenticated(false)
    setCurrentUser(null)
    setAccountOpen(false)
    setAccountForm({ name: '' })
    setPasswordForm({ currentPassword: '', newPassword: '' })
    setDevices([])
    setSelectedId('')
    setDeviceState(null)
    setCommands([])
    setTelemetry([])
    setCameras([])
    setSelectedCameraId('')
    setCameraStreamPath('')
    setCameraRecordings([])
    setCameraRecording(false)
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
        <section className="login-panel">
          <p className="eyebrow">Home IoT Account</p>
          <h1>{authMode === 'signup' ? '회원가입' : authMode === 'reset' ? '비밀번호 찾기' : '로그인'}</h1>
          <p className="hero-copy">
            이메일 계정 또는 OAuth2 로그인으로 장치 대시보드에 접근합니다.
          </p>
          {loginError ? <div className="banner error">{loginError}</div> : null}
          {pendingClaim ? <div className="banner">장치 등록 QR이 확인되었습니다. 로그인하면 자동으로 내 계정에 등록됩니다.</div> : null}

          {authMode === 'login' ? (
            <form onSubmit={handleLogin} className="auth-form-stack">
              <label>
                <span>이메일</span>
                <input type="email" value={loginForm.email} onChange={(event) => setLoginForm((current) => ({ ...current, email: event.target.value }))} autoComplete="email" required />
              </label>
              <label>
                <span>비밀번호</span>
                <input type="password" value={loginForm.password} onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))} autoComplete="current-password" required />
              </label>
              <button className="refresh-button" disabled={loginLoading}>{loginLoading ? '로그인 중...' : '로그인'}</button>
              <button type="button" className="refresh-button" onClick={() => startOAuth('google')}>Google 로그인</button>
              <button type="button" className="refresh-button" onClick={() => startOAuth('naver')}>Naver 로그인</button>
              <div className="auth-links">
                <button type="button" onClick={() => setAuthMode('signup')}>회원가입</button>
                <button type="button" onClick={() => setAuthMode('reset')}>비밀번호 찾기</button>
              </div>
            </form>
          ) : null}

          {authMode === 'signup' ? (
            <form onSubmit={handleSignup} className="auth-form-stack">
              <label><span>이메일</span><input type="email" value={signupForm.email} onChange={(event) => setSignupForm((current) => ({ ...current, email: event.target.value }))} autoComplete="email" required /></label>
              <button type="button" className="refresh-button" onClick={sendSignupCode} disabled={loginLoading || !signupForm.email}>{verificationSent ? '인증 코드 재발송' : '인증 코드 발송'}</button>
              <label><span>인증 코드</span><input value={signupForm.code} onChange={(event) => setSignupForm((current) => ({ ...current, code: event.target.value }))} inputMode="numeric" required /></label>
              <label><span>이름</span><input value={signupForm.name} onChange={(event) => setSignupForm((current) => ({ ...current, name: event.target.value }))} autoComplete="name" required /></label>
              <label><span>비밀번호</span><input type="password" value={signupForm.password} onChange={(event) => setSignupForm((current) => ({ ...current, password: event.target.value }))} autoComplete="new-password" required /></label>
              <button className="refresh-button" disabled={loginLoading}>가입 완료</button>
              <div className="auth-links"><button type="button" onClick={() => setAuthMode('login')}>로그인으로 돌아가기</button></div>
            </form>
          ) : null}

          {authMode === 'reset' ? (
            <form onSubmit={handlePasswordReset} className="auth-form-stack">
              <label><span>이메일</span><input type="email" value={resetForm.email} onChange={(event) => setResetForm((current) => ({ ...current, email: event.target.value }))} autoComplete="email" required /></label>
              <button type="button" className="refresh-button" onClick={requestPasswordReset} disabled={loginLoading || !resetForm.email}>재설정 코드 발송</button>
              <label><span>재설정 코드</span><input value={resetForm.code} onChange={(event) => setResetForm((current) => ({ ...current, code: event.target.value }))} inputMode="numeric" required /></label>
              <label><span>새 비밀번호</span><input type="password" value={resetForm.newPassword} onChange={(event) => setResetForm((current) => ({ ...current, newPassword: event.target.value }))} autoComplete="new-password" required /></label>
              <button className="refresh-button" disabled={loginLoading}>비밀번호 변경</button>
              <div className="auth-links"><button type="button" onClick={() => setAuthMode('login')}>로그인으로 돌아가기</button></div>
            </form>
          ) : null}
        </section>
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
          <button className="account-button" type="button" onClick={() => setAccountOpen((open) => !open)}>
            <span className="account-avatar">{currentUserInitial}</span>
            <span>{currentUserName}</span>
            {isMasterAccount ? <b className="master-badge">MASTER</b> : null}
          </button>
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

      <nav className="view-tabs" aria-label="Home IoT views">
        <button
          type="button"
          className={activeView === 'dashboard' ? 'active' : ''}
          onClick={() => setActiveView('dashboard')}
        >
          Dashboard
        </button>
        <button
          type="button"
          className={activeView === 'video' ? 'active' : ''}
          onClick={() => setActiveView('video')}
        >
          Live Video
        </button>
      </nav>

      {accountOpen ? (
        <section className="account-panel">
          <div className="account-summary">
            <div className="account-avatar large">{currentUserInitial}</div>
            <div>
              <p className="eyebrow">My Page</p>
              <h2>{currentUserName}</h2>
              <p>{currentUser?.email || '-'}</p>
            </div>
            <div className="account-badges">
              <span className={`role-pill ${isMasterAccount ? 'master' : ''}`}>{isMasterAccount ? '마스터 계정' : '일반 사용자'}</span>
              {currentUser?.authProvider ? <span className="role-pill">{formatProvider(currentUser.authProvider)}</span> : null}
            </div>
          </div>

          <div className="account-grid">
            <form className="account-card" onSubmit={saveAccount}>
              <div className="section-title">
                <span>계정 정보 변경</span>
                <small>이메일은 로그인 ID로 유지됩니다</small>
              </div>
              <label>
                <span>이메일</span>
                <input value={currentUser?.email || ''} disabled />
              </label>
              <label>
                <span>이름</span>
                <input value={accountForm.name} onChange={(event) => setAccountForm({ name: event.target.value })} required />
              </label>
              <button className="save-button" disabled={savingAccount}>{savingAccount ? '저장 중...' : '계정 정보 저장'}</button>
            </form>

            <form className="account-card" onSubmit={changePassword}>
              <div className="section-title">
                <span>비밀번호 변경</span>
                <small>소셜 로그인 계정은 현재 비밀번호를 비워둘 수 있습니다</small>
              </div>
              <label>
                <span>현재 비밀번호</span>
                <input type="password" value={passwordForm.currentPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, currentPassword: event.target.value }))} autoComplete="current-password" />
              </label>
              <label>
                <span>새 비밀번호</span>
                <input type="password" value={passwordForm.newPassword} onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))} autoComplete="new-password" required />
              </label>
              <button className="save-button" disabled={savingPassword}>{savingPassword ? '변경 중...' : '비밀번호 변경'}</button>
            </form>
          </div>
        </section>
      ) : null}

      {activeView === 'video' ? (
        <CameraPanel
          cameras={cameras}
          selectedCamera={selectedCamera}
          selectedCameraId={selectedCameraId}
          streamPath={cameraStreamPath}
          recordings={cameraRecordings}
          loading={cameraLoading}
          recording={cameraRecording}
          onSelectCamera={setSelectedCameraId}
          onRefresh={loadCameras}
          onStartRecording={startCameraRecording}
          onStopRecording={stopCameraRecording}
        />
      ) : (
        <>
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
          <form className="claim-form" onSubmit={claimDevice}>
            <div className="section-title claim-title">
              <span>장치 등록</span>
            </div>
            <input
              className="filter-input"
              value={claimForm.deviceId}
              onChange={(event) => setClaimForm((current) => ({ ...current, deviceId: event.target.value }))}
              placeholder="Device ID"
              required
            />
            <input
              className="filter-input"
              type="password"
              value={claimForm.deviceApiKey}
              onChange={(event) => setClaimForm((current) => ({ ...current, deviceApiKey: event.target.value }))}
              placeholder="등록 키"
              required
            />
            <input
              className="filter-input"
              value={claimForm.displayName}
              onChange={(event) => setClaimForm((current) => ({ ...current, displayName: event.target.value }))}
              placeholder="표시 이름"
            />
            <input
              className="filter-input"
              value={claimForm.room}
              onChange={(event) => setClaimForm((current) => ({ ...current, room: event.target.value }))}
              placeholder="위치/방"
            />
            <button className="save-button" disabled={claimingDevice}>
              {claimingDevice ? '등록 중...' : '등록'}
            </button>
          </form>          {!loading && devices.length === 0 ? <Empty text="등록된 장치가 없습니다." /> : null}
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
                  <small>{device.room || '위치 미지정'} / {device.deviceType} / 최초 {formatDate(device.createdAt)}</small>
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
                <div className="detail-actions">
                  <span className={`pill ${selectedDevice.online ? 'online' : 'offline'}`}>
                    {selectedDevice.online ? '온라인' : '오프라인'}
                  </span>
                  {canManageSharing ? (
                    <button className="logout-button" type="button" onClick={issueClaimQr} disabled={claimQrLoading}>
                      {claimQrLoading ? 'QR 발급 중...' : '등록 QR 보기'}
                    </button>
                  ) : null}
                </div>
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

              <form className="metadata-form" onSubmit={savePersonalPreference}>
                <label>
                  <span>{UI.personalAlias}</span>
                  <input
                    value={personalForm.displayName}
                    onChange={(event) => setPersonalForm((current) => ({
                      ...current,
                      displayName: event.target.value,
                    }))}
                    placeholder={selectedDevice.displayName || selectedDevice.name || selectedDevice.deviceId}
                  />
                </label>
                <label>
                  <span>{UI.method}</span>
                  <input
                    value={personalForm.room}
                    onChange={(event) => setPersonalForm((current) => ({
                      ...current,
                      room: event.target.value,
                    }))}
                    placeholder={selectedDevice.room || '-'}
                  />
                </label>
                <button className="save-button">
                  {UI.saveAlias}
                </button>
              </form>



              {canManageSharing ? (
                <Panel title={UI.shareManagement} loading={shareLoading}>
                  <div className="share-admin-grid">
                    <section className="share-admin-card">
                      <div className="section-title">
                        <span>{UI.directAssign}</span>
                        <small>{UI.inviteHint}</small>
                      </div>
                      <form className="share-assign-form" onSubmit={assignDeviceShare}>
                        <input
                          className="filter-input"
                          type="email"
                          value={assignEmail}
                          onChange={(event) => setAssignEmail(event.target.value)}
                          placeholder={UI.assignPlaceholder}
                          required
                        />
                        <select className="filter-input" value={assignRole} onChange={(event) => setAssignRole(event.target.value)}>
                          <option value="CONTROLLER">{UI.controller}</option>
                          <option value="VIEWER">{UI.viewer}</option>
                          <option value="OWNER">{UI.manager}</option>
                        </select>
                        <button className="save-button" disabled={assigningShare}>
                          {assigningShare ? UI.assigning : UI.assign}
                        </button>
                      </form>
                    </section>

                    <section className="share-admin-card">
                      <div className="section-title">
                        <span>{UI.sharedUsers}</span>
                        <small>{shareUsers.length}</small>
                      </div>
                      {!shareUsers.length ? <Empty text={UI.noShares} /> : (
                        <div className="share-list">
                          {shareUsers.map((share) => (
                            <div className="share-row" key={share.memberId || `invite-${share.inviteId}`}>
                              <div>
                                <strong>{share.name || share.email}</strong>
                                <span>{share.email}</span>
                              </div>
                              <div className="share-meta">
                                <span>{share.status === 'PENDING' ? UI.pendingInvite : share.owner ? UI.owner : formatShareRole(share.role)}</span>
                                <span>{formatShareSource(share.source)}</span>
                                <span>{share.status === 'PENDING' ? `${UI.inviteExpires}: ${formatDate(share.expiresAt)}` : formatDate(share.sharedAt)}</span>
                              </div>
                              {share.removable ? (
                                <button className="logout-button compact" type="button" onClick={() => removeDeviceShare(share.status === 'PENDING' ? share.inviteId : share.memberId)}>
                                  {UI.removeShare}
                                </button>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      )}
                    </section>
                  </div>

                  <div className="share-admin-card qr-history-card">
                    <div className="section-title">
                      <span>{UI.homeGroup}</span>
                      <small>{selectedDevice.homeName || '-'}</small>
                    </div>
                    <form className="share-assign-form" onSubmit={createHome}>
                      <input
                        className="filter-input"
                        value={homeForm.name}
                        onChange={(event) => setHomeForm((current) => ({ ...current, name: event.target.value }))}
                        placeholder={UI.createHome}
                      />
                      <button className="save-button">{UI.createHome}</button>
                    </form>
                    <form className="share-assign-form" onSubmit={addHomeMember}>
                      <select className="filter-input" value={homeForm.homeId} onChange={(event) => setHomeForm((current) => ({ ...current, homeId: event.target.value }))}>
                        <option value="">{UI.homeGroup}</option>
                        {homes.map((home) => (
                          <option key={home.homeId} value={home.homeId}>{home.name}</option>
                        ))}
                      </select>
                      <input
                        className="filter-input"
                        type="email"
                        value={homeForm.memberEmail}
                        onChange={(event) => setHomeForm((current) => ({ ...current, memberEmail: event.target.value }))}
                        placeholder={UI.assignPlaceholder}
                      />
                      <button className="save-button">{UI.addFamily}</button>
                      <button className="logout-button compact" type="button" onClick={assignDeviceHome}>{UI.assignHome}</button>
                    </form>
                  </div>

                  <div className="share-admin-card qr-history-card">
                    <div className="section-title">
                      <span>{UI.qrHistory}</span>
                      <small>{claimHistory.length}</small>
                    </div>
                    {!claimHistory.length ? <Empty text={UI.noQrHistory} /> : (
                      <div className="qr-history-list">
                        {claimHistory.map((token) => (
                          <div className="qr-history-row" key={token.tokenId}>
                            <span className={`qr-status ${token.status.toLowerCase()}`}>{formatClaimStatus(token.status)}</span>
                            <span>{UI.issuedAt}: {formatDate(token.createdAt)}</span>
                            <span>{UI.expiresAt}: {formatDate(token.expiresAt)}</span>
                            <span>{UI.issuer}: {token.createdByEmail || '-'}</span>
                            <span>{UI.usedBy}: {token.usedByEmail || '-'}</span>
                            {token.status === 'AVAILABLE' ? (
                              <button className="logout-button compact" type="button" onClick={() => revokeClaimToken(token.tokenId)}>
                                {UI.revoke}
                              </button>
                            ) : null}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="share-admin-card qr-history-card">
                    <div className="section-title">
                      <span>{UI.auditLog}</span>
                      <small>{auditLogs.length}</small>
                    </div>
                    {!auditLogs.length ? <Empty text={UI.noAuditLogs} /> : (
                      <div className="qr-history-list">
                        {auditLogs.map((log) => (
                          <div className="qr-history-row" key={log.auditId}>
                            <span>{formatAuditAction(log.action)}</span>
                            <span>{formatDate(log.createdAt)}</span>
                            <span>{log.actorEmail || '-'}</span>
                            <span>{log.targetValue || '-'}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </Panel>
              ) : null}

              <div className="command-bar">
                {COMMANDS.map((command) => (
                  <button
                    key={command.type}
                    className="command-button"
                    disabled={busyCommand === command.type || !selectedDevice.controlAllowed}
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
        </>
      )}

      {claimQr ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setClaimQr(null)}>
          <section className="qr-modal" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
            <div className="section-title">
              <span>사용자 등록 QR</span>
              <button className="icon-close" type="button" onClick={() => setClaimQr(null)} aria-label="닫기">X</button>
            </div>
            <div className="qr-box">
              <img src={claimQr.qrSrc} alt="장치 등록 QR" />
            </div>
            <div className="qr-meta">
              <strong>{claimQr.deviceName}</strong>
              <span>{claimQr.deviceId}</span>
              <span>만료: {formatDate(claimQr.expiresAt)}</span>
            </div>
            <input className="qr-link" value={claimQr.claimUrl} readOnly onFocus={(event) => event.target.select()} />
            <div className="qr-actions">
              <button className="refresh-button" type="button" onClick={copyClaimUrl}>링크 복사</button>
              <button className="logout-button" type="button" onClick={() => setClaimQr(null)}>닫기</button>
            </div>
          </section>
        </div>
      ) : null}
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

function CameraPanel({
  cameras,
  selectedCamera,
  selectedCameraId,
  streamPath,
  recordings,
  loading,
  recording,
  onSelectCamera,
  onRefresh,
  onStartRecording,
  onStopRecording,
}) {
  const [displayRotated, setDisplayRotated] = useState(false)

  return (
    <section className="camera-workspace">
      <aside className="camera-list panel">
        <div className="section-title">
          <span>Live Video</span>
          <small>{loading ? 'Loading' : `${cameras.length} cameras`}</small>
        </div>
        {!cameras.length ? <Empty text="No camera devices are registered." /> : (
          <div className="device-list">
            {cameras.map((camera) => (
              <button
                key={camera.deviceId}
                className={`device-card ${camera.deviceId === selectedCameraId ? 'active' : ''}`}
                type="button"
                onClick={() => onSelectCamera(camera.deviceId)}
              >
                <span className={`status-dot ${camera.online ? 'online' : ''}`} />
                <span>
                  <strong>{camera.name || camera.deviceId}</strong>
                  <small>{camera.room || '-'} / {camera.ip || '-'}</small>
                </span>
              </button>
            ))}
          </div>
        )}
      </aside>

      <section className="camera-stage panel">
        <div className="section-title">
          <span>{selectedCamera?.name || 'Camera stream'}</span>
          <div className="camera-title-actions">
            <button
              className={`refresh-button ${displayRotated ? 'active' : ''}`}
              type="button"
              disabled={!selectedCamera}
              onClick={() => setDisplayRotated((rotated) => !rotated)}
            >
              Rotate 180
            </button>
            <button className="refresh-button" type="button" onClick={onRefresh}>Refresh</button>
          </div>
        </div>
        <CameraLiveView selectedCamera={selectedCamera} streamPath={streamPath} displayRotated={displayRotated} />
        <div className="camera-meta">
          <Info label="Online" value={selectedCamera?.online ? 'YES' : 'NO'} />
          <Info label="Camera" value={selectedCamera?.cameraReady ? 'READY' : 'WAITING'} />
          <Info label="Frame" value={selectedCamera?.width && selectedCamera?.height ? `${selectedCamera.width}x${selectedCamera.height}` : '-'} />
          <Info label="Ingest FPS" value={formatFps(selectedCamera?.ingestFps)} />
          <Info label="Frame size" value={formatBytes(selectedCamera?.frameBytes)} />
          <Info label="Frame age" value={formatDurationMs(selectedCamera?.lastFrameAgeMs)} />
        </div>
        <div className="command-bar">
          <button className="command-button" type="button" disabled={!selectedCamera || recording} onClick={onStartRecording}>
            Start recording
          </button>
          <button className="logout-button" type="button" disabled={!selectedCamera || !recording} onClick={onStopRecording}>
            Stop recording
          </button>
        </div>
        <Panel title="Recordings">
          {!recordings.length ? <Empty text="No recordings yet." /> : (
            <div className="timeline">
              {recordings.slice(0, 10).map((recording) => (
                <div className="timeline-item" key={recording.recordingId}>
                  <strong>{recording.active ? 'Recording' : 'Recorded'} / {recording.frameCount} frames</strong>
                  <span>{formatDate(recording.startedAt)} - {recording.active ? 'active' : formatDate(recording.stoppedAt)}</span>
                </div>
              ))}
            </div>
          )}
        </Panel>
      </section>
    </section>
  )
}

function CameraLiveView({ selectedCamera, streamPath, displayRotated }) {
  const canvasRef = useRef(null)
  const [viewerStatus, setViewerStatus] = useState('Waiting')
  const [useMjpegFallback, setUseMjpegFallback] = useState(false)
  const [viewerStats, setViewerStats] = useState({
    fps: 0,
    frameBytes: 0,
    renderedFrames: 0,
    lastReceivedAt: '',
  })

  useEffect(() => {
    setViewerStatus(selectedCamera && streamPath ? 'Connecting' : 'Waiting')
    setUseMjpegFallback(false)
    setViewerStats({ fps: 0, frameBytes: 0, renderedFrames: 0, lastReceivedAt: '' })
    if (!selectedCamera || !streamPath) {
      return undefined
    }

    let stopped = false
    let renderedFrames = 0
    let pendingObjectUrl = ''
    let drawing = false
    const receivedAtWindow = []
    const webSocket = new WebSocket(resolveCameraWebSocketUrl(streamPath))
    webSocket.binaryType = 'blob'

    const drawLatestFrame = () => {
      if (stopped) {
        return
      }
      const objectUrl = pendingObjectUrl
      pendingObjectUrl = ''
      if (!objectUrl) {
        drawing = false
        return
      }

      drawing = true
      const image = new Image()
      image.onload = () => {
        const canvas = canvasRef.current
        const context = canvas?.getContext('2d')
        if (canvas && context) {
          if (canvas.width !== image.naturalWidth) {
            canvas.width = image.naturalWidth
          }
          if (canvas.height !== image.naturalHeight) {
            canvas.height = image.naturalHeight
          }
          context.drawImage(image, 0, 0, canvas.width, canvas.height)
          renderedFrames += 1
          if (renderedFrames === 1) {
            setViewerStatus('Live')
          }
          setViewerStats((current) => ({
            ...current,
            renderedFrames,
          }))
        }
        URL.revokeObjectURL(objectUrl)
        window.requestAnimationFrame(drawLatestFrame)
      }
      image.onerror = () => {
        URL.revokeObjectURL(objectUrl)
        window.requestAnimationFrame(drawLatestFrame)
      }
      image.src = objectUrl
    }

    webSocket.onopen = () => {
      if (!stopped) {
        setViewerStatus('Connected')
      }
    }
    webSocket.onmessage = (event) => {
      if (stopped || !(event.data instanceof Blob)) {
        return
      }
      if (pendingObjectUrl) {
        URL.revokeObjectURL(pendingObjectUrl)
      }
      const now = Date.now()
      receivedAtWindow.push(now)
      while (receivedAtWindow.length && receivedAtWindow[0] < now - 5000) {
        receivedAtWindow.shift()
      }
      const elapsed = receivedAtWindow.length > 1 ? Math.max(1, receivedAtWindow[receivedAtWindow.length - 1] - receivedAtWindow[0]) : 0
      const fps = elapsed ? ((receivedAtWindow.length - 1) * 1000) / elapsed : 0
      setViewerStats((current) => ({
        ...current,
        fps,
        frameBytes: event.data.size,
        lastReceivedAt: new Date(now).toISOString(),
      }))
      pendingObjectUrl = URL.createObjectURL(event.data)
      if (!drawing) {
        drawLatestFrame()
      }
    }
    webSocket.onerror = () => {
      if (!stopped && renderedFrames === 0) {
        setUseMjpegFallback(true)
        setViewerStatus('MJPEG fallback')
      }
    }
    webSocket.onclose = () => {
      if (!stopped && renderedFrames === 0) {
        setUseMjpegFallback(true)
        setViewerStatus('MJPEG fallback')
      }
    }

    return () => {
      stopped = true
      if (pendingObjectUrl) {
        URL.revokeObjectURL(pendingObjectUrl)
      }
      webSocket.close()
    }
  }, [selectedCamera?.deviceId, streamPath])

  if (!selectedCamera || !streamPath) {
    return (
      <div className="camera-frame">
        <Empty text={selectedCamera ? 'Waiting for camera stream.' : 'Select a camera.'} />
      </div>
    )
  }

  return (
    <div className={`camera-frame ${displayRotated ? 'display-rotated' : ''}`}>
      {useMjpegFallback ? (
        <img src={streamPath} alt={`${selectedCamera.name || selectedCamera.deviceId} live stream`} />
      ) : (
        <canvas ref={canvasRef} aria-label={`${selectedCamera.name || selectedCamera.deviceId} live stream`} />
      )}
      <div className="camera-viewer-status">
        <span>{viewerStatus}</span>
        <span>{formatFps(viewerStats.fps)} render</span>
        <span>{formatBytes(viewerStats.frameBytes)}</span>
        <span>{viewerStats.lastReceivedAt ? formatTime(viewerStats.lastReceivedAt) : '-'}</span>
      </div>
    </div>
  )
}

function resolveCameraWebSocketUrl(streamPath) {
  const path = streamPath.replace('/mjpeg', '/ws')
  const url = new URL(path, window.location.href)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function formatTime(value) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

function formatFps(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    return '0.0 fps'
  }
  return `${number.toFixed(1)} fps`
}

function formatBytes(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    return '-'
  }
  if (number < 1024) {
    return `${number} B`
  }
  if (number < 1024 * 1024) {
    return `${(number / 1024).toFixed(1)} KB`
  }
  return `${(number / (1024 * 1024)).toFixed(1)} MB`
}

function formatDurationMs(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    return '-'
  }
  if (number < 1000) {
    return `${Math.round(number)} ms`
  }
  return `${(number / 1000).toFixed(1)} s`
}

function consumeClaimCallback() {
  const params = new URLSearchParams(window.location.search)
  const deviceId = params.get('d') || params.get('deviceId')
  const claimToken = params.get('t') || params.get('claimToken')
  if (!deviceId || !claimToken) {
    return null
  }
  const claim = { deviceId, claimToken }
  window.localStorage.setItem(PENDING_CLAIM_KEY, JSON.stringify(claim))
  window.history.replaceState({}, document.title, window.location.pathname)
  return claim
}

function readPendingClaim() {
  try {
    const raw = window.localStorage.getItem(PENDING_CLAIM_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function clearPendingClaim() {
  window.localStorage.removeItem(PENDING_CLAIM_KEY)
}

function buildClaimUrl(deviceId, claimToken) {
  const claimPath = window.location.pathname.startsWith('/iot') ? '/iot/claim' : '/claim'
  const url = new URL(claimPath, window.location.origin)
  url.searchParams.set('d', deviceId)
  url.searchParams.set('t', claimToken)
  return url.toString()
}

function normalizeSession(session) {
  return {
    memberId: session?.member_id ?? session?.memberId ?? null,
    email: session?.email ?? '',
    name: session?.name ?? '',
    authProvider: session?.auth_provider ?? session?.authProvider ?? '',
    roles: session?.roles ?? [],
  }
}

function formatProvider(provider) {
  switch (provider) {
    case 'GOOGLE':
      return 'Google 로그인'
    case 'NAVER':
      return 'Naver 로그인'
    case 'LOCAL':
      return '이메일 로그인'
    default:
      return provider || '-'
  }
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
    createdAt: device?.created_at ?? device?.createdAt ?? '',
    homeId: device?.home_id ?? device?.homeId ?? null,
    homeName: device?.home_name ?? device?.homeName ?? '',
    personalDisplayName: device?.personal_display_name ?? device?.personalDisplayName ?? '',
    personalRoom: device?.personal_room ?? device?.personalRoom ?? '',
    controlAllowed: Boolean(device?.control_allowed ?? device?.controlAllowed ?? true),
    shareManager: Boolean(device?.share_manager ?? device?.shareManager ?? false),
  }
}

function normalizeCamera(camera) {
  return {
    deviceId: camera?.device_id ?? camera?.deviceId ?? '',
    name: camera?.name ?? '',
    room: camera?.room ?? '',
    deviceType: camera?.device_type ?? camera?.deviceType ?? '',
    ip: camera?.ip ?? '',
    online: Boolean(camera?.online),
    cameraReady: Boolean(camera?.camera_ready ?? camera?.cameraReady),
    lastFrameAt: camera?.last_frame_at ?? camera?.lastFrameAt ?? '',
    width: camera?.width ?? null,
    height: camera?.height ?? null,
    frameBytes: camera?.frame_bytes ?? camera?.frameBytes ?? 0,
    ingestFps: Number(camera?.ingest_fps ?? camera?.ingestFps ?? 0),
    lastFrameAgeMs: Number(camera?.last_frame_age_ms ?? camera?.lastFrameAgeMs ?? 0),
  }
}

function normalizeCameraRecording(recording) {
  return {
    recordingId: recording?.recording_id ?? recording?.recordingId ?? '',
    deviceId: recording?.device_id ?? recording?.deviceId ?? '',
    startedAt: recording?.started_at ?? recording?.startedAt ?? '',
    stoppedAt: recording?.stopped_at ?? recording?.stoppedAt ?? '',
    frameCount: recording?.frame_count ?? recording?.frameCount ?? 0,
    totalBytes: recording?.total_bytes ?? recording?.totalBytes ?? 0,
    active: Boolean(recording?.active),
  }
}

function normalizeShare(share) {
  return {
    inviteId: share?.invite_id ?? share?.inviteId ?? null,
    memberId: share?.member_id ?? share?.memberId ?? null,
    email: share?.email ?? '',
    name: share?.name ?? '',
    role: share?.role ?? '',
    source: share?.source ?? '',
    status: share?.status ?? 'ACTIVE',
    owner: Boolean(share?.owner),
    removable: share?.removable !== false,
    sharedAt: share?.shared_at ?? share?.sharedAt ?? '',
    expiresAt: share?.expires_at ?? share?.expiresAt ?? '',
  }
}

function normalizeAuditLog(log) {
  return {
    auditId: log?.audit_id ?? log?.auditId ?? null,
    action: log?.action ?? '',
    targetValue: log?.target_value ?? log?.targetValue ?? '',
    actorEmail: log?.actor_email ?? log?.actorEmail ?? '',
    actorName: log?.actor_name ?? log?.actorName ?? '',
    message: log?.message ?? '',
    createdAt: log?.created_at ?? log?.createdAt ?? '',
  }
}

function normalizeHome(home) {
  return {
    homeId: home?.home_id ?? home?.homeId ?? null,
    name: home?.name ?? '',
    ownerEmail: home?.owner_email ?? home?.ownerEmail ?? '',
    members: home?.members ?? [],
  }
}

function normalizeClaimHistory(token) {
  return {
    tokenId: token?.token_id ?? token?.tokenId ?? null,
    status: token?.status ?? '',
    createdByEmail: token?.created_by_email ?? token?.createdByEmail ?? '',
    usedByEmail: token?.used_by_email ?? token?.usedByEmail ?? '',
    createdAt: token?.created_at ?? token?.createdAt ?? '',
    expiresAt: token?.expires_at ?? token?.expiresAt ?? '',
    usedAt: token?.used_at ?? token?.usedAt ?? '',
    revokedAt: token?.revoked_at ?? token?.revokedAt ?? '',
  }
}

function formatShareSource(source) {
  switch (source) {
    case 'ADMIN':
      return UI.adminAssign
    case 'QR':
      return UI.qr
    case 'FAMILY':
      return UI.familyShare
    case 'OWNER':
      return UI.owner
    default:
      return source || '-'
  }
}

function formatShareRole(role) {
  switch (role) {
    case 'VIEWER':
      return UI.viewer
    case 'OWNER':
      return UI.manager
    case 'CONTROLLER':
    case 'USER':
      return UI.controller
    default:
      return role || '-'
  }
}

function formatAuditAction(action) {
  switch (action) {
    case 'COMMAND_CREATED':
      return '\uba85\ub839 \uc2e4\ud589'
    case 'SHARE_INVITED':
      return '\uacf5\uc720 \ucd08\ub300'
    case 'SHARE_ASSIGNED':
      return '\uacf5\uc720 \ubc30\uc815'
    case 'SHARE_REMOVED':
      return '\uacf5\uc720 \ud574\uc81c'
    case 'SHARE_INVITE_REVOKED':
      return '\ucd08\ub300 \ucde8\uc18c'
    case 'CLAIM_QR_ISSUED':
      return 'QR \ubc1c\uae09'
    case 'CLAIM_QR_REVOKED':
      return 'QR \ucde8\uc18c'
    case 'CLAIM_QR_USED':
      return 'QR \ub4f1\ub85d'
    case 'DEVICE_CLAIMED':
      return '\uc7a5\uce58 \ub4f1\ub85d'
    case 'METADATA_UPDATED':
      return '\uc7a5\uce58 \uc815\ubcf4 \uc218\uc815'
    case 'PREFERENCE_UPDATED':
      return '\ub0b4 \ubcc4\uce6d \uc218\uc815'
    case 'HOME_ASSIGNED':
      return '\uc9d1 \uc5f0\uacb0'
    case 'HOME_SHARE_SYNCED':
      return '\uac00\uc871 \uacf5\uc720 \ub3d9\uae30\ud654'
    default:
      return action || '-'
  }
}

function formatClaimStatus(status) {
  switch (status) {
    case 'AVAILABLE':
      return UI.available
    case 'USED':
      return UI.used
    case 'EXPIRED':
      return UI.expired
    case 'REVOKED':
      return UI.revoked
    default:
      return status || '-'
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




