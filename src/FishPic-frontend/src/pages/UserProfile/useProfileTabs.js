import { useCallback, useEffect, useRef, useState } from 'react'
import { App as AntApp } from 'antd'
import { getMyPosts, getMyCollects, getMyLikes, getPostList } from '../../api'
import { useFetchWithCleanup } from '../../hooks/useRequestUtils'
import { LOAD_MORE_THRESHOLD } from '../../utils/constants'

const INITIAL_TAB_STATE = {
  notes: { items: [], hasMore: true, loaded: false, loading: false },
  favorites: { items: [], hasMore: true, loaded: false, loading: false },
  likes: { items: [], hasMore: true, loaded: false, loading: false },
}

const INITIAL_COUNTS = { notes: 0, favorites: 0, likes: 0, follows: 0, fans: 0 }

export function useProfileTabs({ isOwnProfile, profileUserId }) {
  const { message } = AntApp.useApp()
  const { createSignal } = useFetchWithCleanup()

  const [activeTab, setActiveTab] = useState('notes')
  const [tabState, setTabState] = useState(INITIAL_TAB_STATE)
  const [counts, setCounts] = useState(INITIAL_COUNTS)
  const pageRefs = useRef({ notes: 1, favorites: 1, likes: 1 })
  const loadingMoreRef = useRef(false)
  const [loadingMore, setLoadingMore] = useState(false)

  const apiMap = { notes: getMyPosts, favorites: getMyCollects, likes: getMyLikes }

  const fetchTabData = useCallback(async (tabKey, page = 1, append = false, signal) => {
    if (append) {
      loadingMoreRef.current = true
      setLoadingMore(true)
    } else {
      setTabState(prev => ({
        ...prev,
        [tabKey]: { ...prev[tabKey], loading: true },
      }))
    }

    try {
      let result
      if (!isOwnProfile && tabKey === 'notes') {
        result = await getPostList({ userId: Number(profileUserId), current: page, pageSize: 20 }, signal ? { signal } : {})
      } else {
        const fetchFn = apiMap[tabKey]
        if (!fetchFn) return
        result = await fetchFn({ current: page, pageSize: 20 }, signal ? { signal } : {})
      }
      const records = result.records || []
      const totalPages = result.pages || 0
      const hasMore = page < totalPages

      if (append) {
        setTabState(prev => {
          const existing = prev[tabKey]
          const existingIds = new Set(existing.items.map(p => p.id))
          const newRecords = records.filter(p => !existingIds.has(p.id))
          return {
            ...prev,
            [tabKey]: {
              ...existing,
              items: [...existing.items, ...newRecords],
              hasMore,
              loading: false,
            },
          }
        })
      } else {
        setTabState(prev => ({
          ...prev,
          [tabKey]: { items: records, hasMore, loaded: true, loading: false },
        }))
      }

      setCounts(prev => ({ ...prev, [tabKey]: result.total || 0 }))
      pageRefs.current[tabKey] = page
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '获取数据失败')
      if (!append) {
        setTabState(prev => ({
          ...prev,
          [tabKey]: { ...prev[tabKey], loading: false },
        }))
      }
    } finally {
      if (append) {
        loadingMoreRef.current = false
        setLoadingMore(false)
      }
    }
  }, [message, isOwnProfile, profileUserId])

  // Load tab data when active tab changes
  useEffect(() => {
    if (!tabState[activeTab].loading) {
      const signal = createSignal()
      fetchTabData(activeTab, 1, false, signal)
    }
  }, [activeTab])

  // Infinite scroll for loading more items
  useEffect(() => {
    const handleScroll = () => {
      const currentTab = activeTab
      if (loadingMoreRef.current || !tabState[currentTab]?.hasMore) return

      const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
      const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
      const clientHeight = document.documentElement.clientHeight || window.innerHeight

      if (scrollTop + clientHeight >= scrollHeight - LOAD_MORE_THRESHOLD) {
        const signal = createSignal()
        fetchTabData(currentTab, pageRefs.current[currentTab] + 1, true, signal)
      }
    }

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [activeTab, tabState, fetchTabData, createSignal])

  const resetTabState = useCallback(() => {
    setTabState(INITIAL_TAB_STATE)
    setCounts(INITIAL_COUNTS)
    setActiveTab('notes')
    pageRefs.current = { notes: 1, favorites: 1, likes: 1 }
  }, [])

  return {
    activeTab,
    setActiveTab,
    tabState,
    counts,
    setCounts,
    loadingMore,
    fetchTabData,
    resetTabState,
  }
}
