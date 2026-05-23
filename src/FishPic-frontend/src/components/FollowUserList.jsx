import { useState, useEffect, useRef, useCallback } from 'react'
import { App, Avatar, Button, Empty, Skeleton, Spin } from 'antd'
import { followUser, getFans, getFollows } from '../api'
import { useFetchWithCleanup } from '../hooks/useRequestUtils'
import './FollowUserList.css'

export default function FollowUserList({ type, onCountChange, onUserClick, targetUserId }) {
  const { message } = App.useApp()
  const { createSignal } = useFetchWithCleanup()

  const [items, setItems] = useState([])
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [followedFanIds, setFollowedFanIds] = useState(new Set())
  const pageRef = useRef(1)
  const loadingMoreRef = useRef(false)
  const containerRef = useRef(null)

  const fetchData = useCallback(async (page, append) => {
    if (append) {
      loadingMoreRef.current = true
      setLoadingMore(true)
    } else {
      setLoading(true)
    }

    try {
      const fetchFn = type === 'follows' ? getFollows : getFans
      const signal = createSignal()
      const params = { current: page, pageSize: 20 }
      if (targetUserId) params.userId = targetUserId
      const result = await fetchFn(params, { signal })
      const records = result.records || []
      const totalPages = result.pages || 0
      const more = page < totalPages

      if (append) {
        setItems(prev => {
          const existingIds = new Set(prev.map(u => u.id))
          const newRecords = records.filter(u => !existingIds.has(u.id))
          return [...prev, ...newRecords]
        })
      } else {
        setItems(records)
      }

      setHasMore(more)
      pageRef.current = page
      if (onCountChange) {
        onCountChange(type, result.total || 0)
      }
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '获取数据失败')
    } finally {
      setLoading(false)
      if (append) {
        loadingMoreRef.current = false
        setLoadingMore(false)
      }
    }
  }, [type, createSignal, message, onCountChange, targetUserId])

  useEffect(() => {
    setItems([])
    setHasMore(true)
    setFollowedFanIds(new Set())
    pageRef.current = 1
    fetchData(1, false)
  }, [type, targetUserId])

  const handleScroll = useCallback(() => {
    const el = containerRef.current
    if (!el || loadingMoreRef.current || !hasMore) return
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 100) {
      fetchData(pageRef.current + 1, true)
    }
  }, [hasMore, fetchData])

  const handleFollowToggle = async (targetUserId) => {
    try {
      const result = await followUser(targetUserId)
      if (type === 'follows' && !result) {
        setItems(prev => prev.filter(u => u.id !== targetUserId))
      } else if (type === 'fans') {
        setFollowedFanIds(prev => {
          const next = new Set(prev)
          if (result) next.add(targetUserId)
          else next.delete(targetUserId)
          return next
        })
      }
      message.success(result ? '已关注' : '已取消关注')
    } catch (error) {
      message.error(error.message || '操作失败')
    }
  }

  if (loading) {
    return <Skeleton active paragraph={{ rows: 6 }} />
  }

  if (!items || items.length === 0) {
    return (
      <div className="follow-list-empty">
        <Empty description={type === 'follows' ? '暂无关注用户' : '暂无粉丝'} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </div>
    )
  }

  const isFollowing = (user) => {
    if (type === 'follows') return true
    return followedFanIds.has(user.id)
  }

  return (
    <div className="follow-list-container" ref={containerRef} onScroll={handleScroll}>
      <div className="follow-user-list">
        {items.map((user) => (
          <div key={user.id} className="follow-user-item">
            <div className="follow-user-info" onClick={() => onUserClick && onUserClick(user.id)}>
              <Avatar
                size={44}
                src={user.avatar}
                style={{ backgroundColor: user.avatar ? 'transparent' : 'var(--accent)' }}
              >
                {(user.nickname || user.username)?.charAt(0)?.toUpperCase()}
              </Avatar>
              <div className="follow-user-text">
                <span className="follow-user-nickname">{user.nickname || user.username}</span>
                {user.nickname && <span className="follow-user-username">@{user.username}</span>}
              </div>
            </div>
            <Button
              size="small"
              type={isFollowing(user) ? 'default' : 'primary'}
              className={isFollowing(user) ? 'follow-btn-following' : 'follow-btn-tofollow'}
              onClick={() => handleFollowToggle(user.id)}
            >
              {isFollowing(user) ? '已关注' : '关注'}
            </Button>
          </div>
        ))}
      </div>
      {hasMore ? (
        loadingMore ? (
          <div className="loading-more"><Spin /> 加载中...</div>
        ) : null
      ) : (
        items.length > 0 && <div className="no-more-posts">没有更多了</div>
      )}
    </div>
  )
}
