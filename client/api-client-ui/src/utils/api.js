const API_BASE_PATH = normalizeBasePath(import.meta.env.VITE_API_BASE_PATH || '/api')
const AUTH_STORAGE_KEY = 'home_iot_admin_auth'

function normalizeBasePath(path) {
  if (!path || path === '/') {
    return ''
  }
  return path.endsWith('/') ? path.slice(0, -1) : path
}

function resolveApiPath(path) {
  if (!path.startsWith('/api')) {
    return path
  }
  return `${API_BASE_PATH}${path.slice('/api'.length) || ''}`
}

export async function fetchJson(path, options = {}) {
  const authHeader = getStoredAuthHeader()
  const response = await fetch(resolveApiPath(path), {
    headers: {
      'Content-Type': 'application/json',
      ...(authHeader && options.auth !== false ? { Authorization: authHeader } : {}),
      ...(options.headers || {}),
    },
    ...options,
  })

  if (!response.ok) {
    const error = new Error(response.status === 401 ? '로그인이 필요합니다.' : `API request failed: ${response.status}`)
    error.status = response.status
    throw error
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    const body = await response.text()
    throw new Error(`Expected JSON but received ${contentType || 'unknown content type'}: ${body.slice(0, 80)}`)
  }

  return response.json()
}

export async function postJson(path, body) {
  return fetchJson(path, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function patchJson(path, body) {
  return fetchJson(path, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export function storeBasicAuth(username, password) {
  const token = window.btoa(`${username}:${password}`)
  window.localStorage.setItem(AUTH_STORAGE_KEY, `Basic ${token}`)
}

export function clearStoredAuth() {
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
}

export function hasStoredAuth() {
  return Boolean(getStoredAuthHeader())
}

function getStoredAuthHeader() {
  return window.localStorage.getItem(AUTH_STORAGE_KEY)
}
