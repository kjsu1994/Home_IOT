export async function fetchJson(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
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
