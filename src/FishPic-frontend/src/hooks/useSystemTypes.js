import { useState, useEffect } from 'react'
import { useSystemTypes as useSystemTypesBase } from './useRequestUtils'

/**
 * Convenience wrapper: fetches system types once on mount and returns the array directly.
 * Backed by a 5-minute TTL cache (shared across all consumers).
 */
export function useSystemTypes() {
  const { fetchSystemTypes } = useSystemTypesBase()
  const [types, setTypes] = useState([])

  useEffect(() => {
    let cancelled = false
    fetchSystemTypes()
      .then(res => {
        if (!cancelled) setTypes(Array.isArray(res) ? res : [])
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [fetchSystemTypes])

  return types
}

// Also re-export the base hook for callers that need the full API
export { useSystemTypesBase }
