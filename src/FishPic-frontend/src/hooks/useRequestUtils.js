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

const systemTypesCache = {
  data: null,
  timestamp: 0,
  ttl: 5 * 60 * 1000,
}

export function useSystemTypes() {
  const fetchSystemTypes = useCallback(async () => {
    const now = Date.now()
    if (systemTypesCache.data && (now - systemTypesCache.timestamp) < systemTypesCache.ttl) {
      return systemTypesCache.data
    }
    try {
      const result = await getSystemTypes()
      systemTypesCache.data = result
      systemTypesCache.timestamp = now
      return result
    } catch {
      if (systemTypesCache.data) {
        systemTypesCache.timestamp = now
        return systemTypesCache.data
      }
      throw new Error('获取分类列表失败')
    }
  }, [])

  const invalidateCache = useCallback(() => {
    systemTypesCache.data = null
    systemTypesCache.timestamp = 0
  }, [])

  return { fetchSystemTypes, invalidateCache }
}

const marqueeCache = {
  data: null,
  timestamp: 0,
  ttl: 5 * 60 * 1000,
}

export function useMarquee() {
  const fetchMarquee = useCallback(async () => {
    const now = Date.now()
    if (marqueeCache.data && (now - marqueeCache.timestamp) < marqueeCache.ttl) {
      return marqueeCache.data
    }
    try {
      const result = await getMarquee()
      marqueeCache.data = result
      marqueeCache.timestamp = now
      return result
    } catch {
      if (marqueeCache.data) {
        marqueeCache.timestamp = now
        return marqueeCache.data
      }
      throw new Error('获取轮播图失败')
    }
  }, [])

  return { fetchMarquee }
}

