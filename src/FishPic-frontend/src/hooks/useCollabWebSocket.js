import { useEffect, useRef, useCallback, useState } from 'react'
import { getToken } from '../utils/storage'

export function useCollabWebSocket(spaceId, onMessage, onCleanup, onReady) {
  const wsRef = useRef(null)
  const retryRef = useRef(0)
  const timerRef = useRef(null)
  const connectRef = useRef(null)
  const closedSockets = useRef(new WeakSet())
  const onMessageRef = useRef(onMessage)
  const onCleanupRef = useRef(onCleanup)
  const onReadyRef = useRef(onReady)
  const [connected, setConnected] = useState(false)

  useEffect(() => { onMessageRef.current = onMessage }, [onMessage])
  useEffect(() => { onCleanupRef.current = onCleanup }, [onCleanup])
  useEffect(() => { onReadyRef.current = onReady }, [onReady])

  const connect = useCallback(() => {
    if (!spaceId) return
    if (wsRef.current?.readyState === WebSocket.OPEN ||
        wsRef.current?.readyState === WebSocket.CONNECTING) return

    const token = getToken()
    if (!token) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/api/ws/collab?spaceId=${spaceId}&token=${token}`
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      retryRef.current = 0
      setConnected(true)
      try {
        ws.send(JSON.stringify({ type: 'resync', spaceId }))
      } catch { /* 忽略 */ }
      onReadyRef.current?.()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        onMessageRef.current?.(data)
      } catch { /* 忽略 */ }
    }

    ws.onclose = (event) => {
      if (wsRef.current === ws) { wsRef.current = null; setConnected(false) }
      if (closedSockets.current.has(ws)) return
      if (event.code !== 1000 && spaceId && retryRef.current < 10) {
        const delay = Math.min(1000 * Math.pow(2, retryRef.current), 30000)
        retryRef.current++
        timerRef.current = setTimeout(() => {
          connectRef.current?.()
        }, delay)
      } else if (retryRef.current >= 10) {
        // 不重连,让用户手动刷新
      }
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [spaceId])

  useEffect(() => {
    connectRef.current = connect
  }, [connect])

  const disconnect = useCallback(() => {
    if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = null }
    if (wsRef.current) {
      closedSockets.current.add(wsRef.current)
      try { wsRef.current.close(1000) } catch { /* 忽略 */ }
      wsRef.current = null
    }
    setConnected(false)
    onCleanupRef.current?.()
  }, [])

  const unmountedRef = useRef(false)
  useEffect(() => {
    unmountedRef.current = false
    const timer = setTimeout(() => {
      if (unmountedRef.current) return
      connect()
    }, 150)
    return () => {
      unmountedRef.current = true
      clearTimeout(timer)
      disconnect()
    }
  }, [connect, disconnect])

  useEffect(() => {
    const onVis = () => {
      if (document.visibilityState === 'visible' && spaceId &&
          (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN)) {
        connect()
      }
    }
    document.addEventListener('visibilitychange', onVis)
    return () => document.removeEventListener('visibilitychange', onVis)
  }, [spaceId, connect])

  const sendMessage = useCallback((data) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(data))
    }
  }, [])

  return { sendMessage, disconnect, wsRef, connected }
}
