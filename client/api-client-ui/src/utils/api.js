const API_BASE_PATH = normalizeBasePath(import.meta.env.VITE_API_BASE_PATH || '/api')
const ACCESS_TOKEN_KEY = 'home_iot_access_token'
const REFRESH_TOKEN_KEY = 'home_iot_refresh_token'

function normalizeBasePath(path) {
  if (!path || path === '/') {
    return ''
  }
  return path.endsWith('/') ? path.slice(0, -1) : path
}

export function resolveApiPath(path) {
  if (!path.startsWith('/api')) {
    return path
  }
  return `${API_BASE_PATH}${path.slice('/api'.length) || ''}`
}

export async function fetchJson(path, options = {}) {
  return fetchJsonInternal(path, options, true)
}

async function fetchJsonInternal(path, options = {}, allowRefresh) {
  const accessToken = getAccessToken()
  const response = await fetch(resolveApiPath(path), {
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken && options.auth !== false ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(options.headers || {}),
    },
    ...options,
  })

  if (response.status === 401 && allowRefresh && options.auth !== false && getRefreshToken()) {
    const refreshed = await reissueToken()
    if (refreshed) {
      return fetchJsonInternal(path, options, false)
    }
  }

  if (!response.ok) {
    const error = new Error(response.status === 401 ? 'Login required.' : `API request failed: ${response.status}`)
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

async function reissueToken() {
  try {
    const response = await fetch(resolveApiPath('/api/auth/reissue'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Refresh: getRefreshToken(),
      },
      body: JSON.stringify({ refresh_token: getRefreshToken(), refreshToken: getRefreshToken() }),
    })
    if (!response.ok) {
      clearStoredAuth()
      return false
    }
    const body = await response.json()
    storeTokens(body.data?.access_token || body.data?.accessToken, body.data?.refresh_token || body.data?.refreshToken)
    return true
  } catch {
    clearStoredAuth()
    return false
  }
}

export async function postJson(path, body, options = {}) {
  return fetchJson(path, {
    method: 'POST',
    body: JSON.stringify(body),
    ...options,
  })
}

export async function patchJson(path, body, options = {}) {
  return fetchJson(path, {
    method: 'PATCH',
    body: JSON.stringify(body),
    ...options,
  })
}

export function storeTokens(accessToken, refreshToken) {
  if (accessToken) {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  }
  if (refreshToken) {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
}

export function clearStoredAuth() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function hasStoredAuth() {
  return Boolean(getAccessToken())
}

export function getRefreshToken() {
  return window.localStorage.getItem(REFRESH_TOKEN_KEY)
}

function getAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY)
}
