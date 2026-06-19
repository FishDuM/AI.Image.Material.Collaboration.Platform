import { useState, useEffect, useRef } from 'react'
import { getToken } from '../utils/storage'
import { API_BASE_URL } from '../api'

const MAX_SSE_RETRIES = 3
const RETRY_DELAY_MS = 2000

export function useAiSse(taskId) {
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [connected, setConnected] = useState(false)
  const doneRef = useRef(false)

  useEffect(() => {
    if (!taskId) {
      setResult(null)
      setError(null)
      setConnected(false)
      doneRef.current = false
      return
    }

    setResult(null)
    setError(null)
    doneRef.current = false

    let es = null
    let retryCount = 0
    let retryTimer = null
    let unmounted = false

    function connect() {
      if (unmounted || doneRef.current) return

      const token = getToken()
      const url = `${API_BASE_URL}/ai/result-sse/${taskId}`
      es = new EventSource(token ? `${url}?token=${encodeURIComponent(token)}` : url)

      es.addEventListener('result', (e) => {
        if (doneRef.current) return
        try {
          const data = JSON.parse(e.data)
          if (data.status === 'DONE') {
            doneRef.current = true
            setResult(data)
            es.close()
          } else if (data.status === 'FAILED') {
            doneRef.current = true
            setError(data.errorMsg || '任务失败')
            es.close()
          }
        } catch {
          doneRef.current = true
          setError('解析推送数据失败')
          es.close()
        }
      })

      es.onopen = () => {
        setConnected(true)
        retryCount = 0
      }

      es.onerror = () => {
        setConnected(false)
        es.close()
        if (!doneRef.current && !unmounted) {
          if (retryCount < MAX_SSE_RETRIES) {
            retryCount++
            retryTimer = setTimeout(connect, RETRY_DELAY_MS)
          } else {
            doneRef.current = true
            setError('SSE 连接断开，请重试')
          }
        }
      }
    }

    connect()

    return () => {
      unmounted = true
      if (retryTimer) clearTimeout(retryTimer)
      if (es) es.close()
    }
  }, [taskId])

  return { result, error, connected }
}
