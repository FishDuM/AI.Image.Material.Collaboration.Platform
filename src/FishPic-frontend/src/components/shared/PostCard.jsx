import { LikeOutlined, StarOutlined } from '@ant-design/icons'

function PostCard({ post, onClick }) {
  const coverUrl = post.pictureUrl?.[0] || ''

  return (
    <div className="post-card" onClick={() => onClick?.(post)}>
      <div className="post-card-image-wrapper">
        {coverUrl ? (
          <img src={coverUrl} alt={post.title} className="post-card-image" loading="lazy" />
        ) : (
          <div className="post-card-image" style={{ height: 120, background: 'var(--bg-hover)' }} />
        )}
        {post.pictureUrl?.length > 1 && (
          <span className="post-card-cover-badge">{post.pictureUrl.length}图</span>
        )}
      </div>
      <div className="post-card-footer">
        {post.title && <div className="post-card-title">{post.title}</div>}
        <div className="post-card-meta">
          <div className="post-card-user">
            {post.avatar ? (
              <img src={post.avatar} alt={post.username} className="post-card-avatar" />
            ) : (
              <div className="post-card-avatar-default">
                {post.username?.charAt(0)?.toUpperCase() || '?'}
              </div>
            )}
            <span className="post-card-username">{post.username}</span>
          </div>
          <div className="post-card-stats">
            <span className="post-card-stat">
              <LikeOutlined /> {post.likesNum || 0}
            </span>
            <span className="post-card-stat">
              <StarOutlined /> {post.collectsNum || 0}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default PostCard
