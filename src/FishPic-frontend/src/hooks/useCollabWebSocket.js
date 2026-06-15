import { useEffect, useRef, useCallback } from 'react'
import { getToken } from '../utils/storage'

export function useCollabWebSocket(spaceId, onMessage, onCleanup, onReady) {
  const wsRef = useRef(null)
  const retryRef = useRef(0)
  const timerRef = useRef(null)
  const closedSockets = useRef(new WeakSet())
  const onMessageRef = useRef(onMessage)
  const onCleanupRef = useRef(onCleanup)
  const onReadyRef = useRef(onReady)

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
      console.log('[CollabWS] 已连接')
      try {
        ws.send(JSON.stringify({ type: 'resync', spaceId }))
      } catch (e) {
        console.warn('[CollabWS] resync 消息发送失败:', e)
      }
      onReadyRef.current?.()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        console.log('[CollabWS] 收到消息:', data.type, data)
        onMessageRef.current?.(data)
      } catch { /* 忽略 */ }
    }

    ws.onclose = (event) => {
      if (wsRef.current === ws) wsRef.current = null
      if (closedSockets.current.has(ws)) return
      if (event.code !== 1000 && spaceId && retryRef.current < 10) {
        const delay = Math.min(1000 * Math.pow(2, retryRef.current), 30000)
        retryRef.current++
        timerRef.current = setTimeout(connect, delay)
      } else if (retryRef.current >= 10) {
        console.warn('[CollabWS] 重连超过 10 次,停止重连。请检查网络或刷新页面。')
        // 不重连,让用户手动刷新
      }
    }

    ws.onerror = (err) => {
      console.warn('[CollabWS] 错误类型:', err?.type || 'unknown')
      ws.close()
    }
  }, [spaceId])

  const disconnect = useCallback(() => {
    if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = null }
    if (wsRef.current) {
      closedSockets.current.add(wsRef.current)
      try { wsRef.current.close(1000) } catch { /* 忽略 */ }
      wsRef.current = null
    }
    onCleanupRef.current?.()
  }, [])

  // 用 ref 跟踪 unmounted,避免快速 unmount 后 setTimeout 仍触发 connect
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
      console.log('[CollabWS] 发送消息:', data.type, data)
    } else {
      console.warn('[CollabWS] 发送失败，WebSocket 未就绪, readyState=', wsRef.current?.readyState)
    }
  }, [])

  return { sendMessage, disconnect, wsRef }
}
