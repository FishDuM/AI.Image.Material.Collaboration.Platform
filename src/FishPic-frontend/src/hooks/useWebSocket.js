import { getToken } from '../utils/storage'
import { WS_RECONNECT_INTERVAL } from '../utils/constants'

let ws = null
let reconnectTimer = null
const listeners = new Set()
let isDestroyed = false
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 10

function getWsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/ws`
}

function notifyListeners(data) {
  listeners.forEach(cb => {
    try { cb(data) } catch (e) { console.error('websocket listener error', e) }
  })
}

function connect() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  if (isDestroyed) return

  const token = getToken()
  if (!token) return

  // 通过Sec-WebSocket-Protocol传递token，避免token出现在URL和服务器日志中
  const url = getWsUrl()

  try {
    ws = new WebSocket(url, ['access_token', token])
  } catch {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    reconnectAttempts = 0
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
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.warn('[WebSocket] 已达最大重连次数，停止重连')
    return
  }
  if (reconnectTimer) clearTimeout(reconnectTimer)
  // 指数退避：3s, 6s, 12s, 24s ... 最大60s
  const delay = Math.min(WS_RECONNECT_INTERVAL * Math.pow(2, reconnectAttempts), 60000)
  reconnectAttempts++
  reconnectTimer = setTimeout(() => {
    if (!isDestroyed) connect()
  }, delay)
}

export function createConnection() {
  isDestroyed = false
  reconnectAttempts = 0
  connect()
}

export function destroyConnection() {
  isDestroyed = true
  reconnectAttempts = 0
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
