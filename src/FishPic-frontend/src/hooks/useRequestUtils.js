import { useRef, useCallback, useEffect } from 'react'
import { getMarquee, getSystemTypes } from '../api'

export function useFetchWithCleanup() {
  const abortRef = useRef(null)

  const createSignal = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort()
      abortRef.current = null
    }
    const controller = new AbortController()
    abortRef.current = { abort: () => controller.abort(), signal: controller.signal }
    return controller.signal
  }, [])

  const abort = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort()
      abortRef.current = null
    }
  }, [])

  useEffect(() => abort, [abort])

  return { createSignal, abort }
}

function useTtlCache(fetcher, cacheKey, ttlMs = 5 * 60 * 1000) {
  const cacheRef = useRef({ data: null, timestamp: 0 })

  const fetch = useCallback(async () => {
    const now = Date.now()
    const cache = cacheRef.current
    if (cache.data && (now - cache.timestamp) < ttlMs) {
      return cache.data
    }
    try {
      const result = await fetcher()
      cache.data = result
      cache.timestamp = now
      return result
    } catch {
      if (cache.data) {
        cache.timestamp = now
        return cache.data
      }
      throw new Error(`获取${cacheKey}失败`)
    }
  }, [fetcher, cacheKey, ttlMs])

  const invalidate = useCallback(() => {
    cacheRef.current = { data: null, timestamp: 0 }
  }, [])

  return { fetch, invalidate }
}

export function useSystemTypes() {
  const { fetch, invalidate } = useTtlCache(getSystemTypes, '分类列表')
  return { fetchSystemTypes: fetch, invalidateCache: invalidate }
}

export function useMarquee() {
  const { fetch } = useTtlCache(getMarquee, '轮播图')
  return { fetchMarquee: fetch }
}
