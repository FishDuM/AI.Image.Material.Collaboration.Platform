import { useState, useEffect, useRef } from 'react'
import { getToken } from '../utils/storage'
import { API_BASE_URL } from '../api'

/**
 * SSE hook：监听 AI 任务结果推送
 */
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

    const token = getToken()
    const url = `${API_BASE_URL}/ai/result-sse/${taskId}`
    const es = new EventSource(token ? `${url}?token=${encodeURIComponent(token)}` : url)

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

    es.onopen = () => setConnected(true)

    es.onerror = () => {
      setConnected(false)
      if (!doneRef.current) {
        doneRef.current = true
        setError('SSE 连接断开，请重试')
      }
      es.close()
    }

    return () => {
      es.close()
    }
  }, [taskId])

  return { result, error, connected }
}
