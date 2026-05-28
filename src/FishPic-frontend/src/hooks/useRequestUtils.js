import { useRef, useCallback, useEffect } from 'react'
import api from '../api'

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
      const result = await api.get('/system/list')
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
      const result = await api.get('/system/marquee')
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

export function useDebounce(fn, delay = 300) {
  const timerRef = useRef(null)
  const fnRef = useRef(fn)

  useEffect(() => {
    fnRef.current = fn
  }, [fn])

  const debouncedFn = useCallback((...args) => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }
    timerRef.current = setTimeout(() => {
      fnRef.current(...args)
    }, delay)
  }, [delay])

  const cancel = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  useEffect(() => {
    return () => cancel()
  }, [cancel])

  return { debouncedFn, cancel }
}

export function useThrottle(fn, delay = 300) {
  const lastRunRef = useRef(0)
  const timerRef = useRef(null)
  const fnRef = useRef(fn)

  useEffect(() => {
    fnRef.current = fn
  }, [fn])

  const throttledFn = useCallback((...args) => {
    const now = Date.now()
    if (now - lastRunRef.current >= delay) {
      lastRunRef.current = now
      fnRef.current(...args)
    } else {
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = setTimeout(() => {
        lastRunRef.current = Date.now()
        fnRef.current(...args)
      }, delay - (now - lastRunRef.current))
    }
  }, [delay])

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [])

  return throttledFn
}
