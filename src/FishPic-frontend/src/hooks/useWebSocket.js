import { getToken } from '../utils/storage'

let ws = null
let reconnectTimer = null
const listeners = new Set()
let isDestroyed = false

function getWsUrl() {
  const token = getToken()
  if (!token) return null
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/ws?token=${token}`
}

function notifyListeners(data) {
  listeners.forEach(cb => {
    try { cb(data) } catch (e) { console.error('websocket listener error', e) }
  })
}

function connect() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  if (isDestroyed) return

  const url = getWsUrl()
  if (!url) return

  try {
    ws = new WebSocket(url)
  } catch {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    notifyListeners({ type: '__WS_OPEN__' })
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      notifyListeners(data)
    } catch {
      // ignore non-JSON messages
    }
  }

  ws.onclose = () => {
    ws = null
    notifyListeners({ type: '__WS_CLOSE__' })
    scheduleReconnect()
  }

  ws.onerror = () => {
    // onclose will fire after onerror, reconnect handled there
  }
}

function scheduleReconnect() {
  if (isDestroyed) return
  if (reconnectTimer) clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(() => {
    if (!isDestroyed) connect()
  }, 3000)
}

export function createConnection() {
  isDestroyed = false
  connect()
}

export function destroyConnection() {
  isDestroyed = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.onerror = null
    ws.onmessage = null
    ws.onopen = null
    ws.close()
    ws = null
  }
}

export function onMessage(callback) {
  listeners.add(callback)
  return () => listeners.delete(callback)
}

export function offMessage(callback) {
  listeners.delete(callback)
}

export function getConnectionStatus() {
  if (!ws) return 'CLOSED'
  switch (ws.readyState) {
    case WebSocket.CONNECTING: return 'CONNECTING'
    case WebSocket.OPEN: return 'OPEN'
    case WebSocket.CLOSING: return 'CLOSING'
    default: return 'CLOSED'
  }
}
