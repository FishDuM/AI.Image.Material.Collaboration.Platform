import { useState, useEffect } from 'react'
import { LikeOutlined, HeartFilled, HeartOutlined, StarFilled, StarOutlined } from '@ant-design/icons'
import { Image as AntImage, App } from 'antd'
import { collectPost } from '../../api'

function PostCard({ post, onClick, variant = 'community' }) {
  const { message } = App.useApp()
  const coverUrl = post.url || post.pictureUrl?.[0] || ''
  const [isCollected, setIsCollected] = useState(post?.isCollected ?? false)
  const [localCollectsNum, setLocalCollectsNum] = useState(null)

  useEffect(() => {
    setIsCollected(post?.isCollected ?? false)
    setLocalCollectsNum(null)
  }, [post?.id])

  const displayCollectsNum = localCollectsNum ?? post.collectsNum ?? 0

  const handleClick = () => {
    onClick?.(post)
  }

  const handleCollect = async (e) => {
    e.stopPropagation()
    if (!post.id) return
    try {
      const result = await collectPost(post.id)
      setIsCollected(result)
      setLocalCollectsNum(prev => {
        const base = Number(prev ?? post.collectsNum ?? 0)
        return result ? base + 1 : Math.max(0, base - 1)
      })
    } catch (err) {
      message.error(err.message || '操作失败')
    }
  }

  if (variant === 'profile') {
    return (
      <div className="post-card" onClick={handleClick}>
        <div className="post-cover-wrapper">
          <AntImage src={coverUrl} alt={post.title} preview={false} />
        </div>
        <div className="post-card-body">
          <h3 className="post-card-title">{post.title}</h3>
          <div className="post-card-footer">
            <span className="post-likes">
              {post.likesNum > 0 ? (
                <HeartFilled style={{ color: 'var(--error)', marginRight: 4 }} />
              ) : (
                <HeartOutlined style={{ marginRight: 4 }} />
              )}
              {post.likesNum || 0}
            </span>
            <span className={`post-card-stat post-card-collect${isCollected ? ' collected' : ''}`} onClick={handleCollect}>
              {isCollected ? <StarFilled /> : <StarOutlined />}
              <span>{displayCollectsNum}</span>
            </span>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="post-card" onClick={handleClick}>
      {coverUrl ? (
        <AntImage
          src={coverUrl}
          alt={post.title}
          className="post-card-image"
          preview={false}
          style={{ objectFit: 'cover', borderRadius: '12px 12px 0 0', overflow: 'hidden' }}
        />
      ) : (
        <div className="post-card-image-placeholder" />
      )}
      <div className="post-card-content">
        <div className="post-card-title">{post.title}</div>
        <div className="post-card-footer">
          <div className="post-card-author">
            {post.avatar ? (
              <img src={post.avatar} alt={post.username} className="post-card-avatar" />
            ) : (
              <div className="post-card-avatar post-card-avatar-default">{post.username?.charAt(0)?.toUpperCase()}</div>
            )}
            <span className="post-card-username">{post.username}</span>
          </div>
          <div className="post-card-stats">
            <span className="post-card-likes">
              <LikeOutlined />
              <span>{post.likesNum || 0}</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default PostCard