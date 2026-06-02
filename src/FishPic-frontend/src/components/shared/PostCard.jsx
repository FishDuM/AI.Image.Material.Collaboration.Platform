import React from 'react'
import { LikeOutlined, HeartFilled, HeartOutlined } from '@ant-design/icons'
import { Image as AntImage } from 'antd'

function PostCard({ post, onClick, variant = 'community' }) {
  const coverUrl = post.url || post.pictureUrl?.[0] || ''

  const handleClick = () => {
    onClick?.(post)
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

export default React.memo(PostCard)