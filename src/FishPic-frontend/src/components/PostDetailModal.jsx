import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Spin, Popover, QRCode, App } from 'antd'
import { LikeOutlined, LikeFilled, StarFilled, StarOutlined, MessageOutlined, ShareAltOutlined, CopyOutlined, SendOutlined } from '@ant-design/icons'
import MobilePageWrapper from './MobilePageWrapper'
import { copyToClipboard } from '../utils/clipboard'
import PostModal from './shared/PostModal'
import PostLayout from './shared/PostLayout'
import CommentSection from './CommentSection'
import { collectPost, followUser, likePost } from '../api'
import { formatTime } from '../utils/constants'
import './PostDetailModal.css'

function PostDetailModal({
  open,
  onClose,
  loading,
  postDetail,
  detailImageIndex,
  onImageIndexChange,
  currentUsername,
  onEdit,
  onCommentCountChange,
  mode = 'modal',
}) {
  const { message } = App.useApp()
  const navigate = useNavigate()

  const [isCollected, setIsCollected] = useState(postDetail?.isCollected ?? false)
  const [localCollectsNum, setLocalCollectsNum] = useState(null)
  const [isFollowing, setIsFollowing] = useState(false)
  const [isLiked, setIsLiked] = useState(postDetail?.isLiked ?? false)
  const [localLikesNum, setLocalLikesNum] = useState(null)

  useEffect(() => {
    setIsCollected(postDetail?.isCollected ?? false)
    setLocalCollectsNum(null)
    setIsFollowing(false)
    setIsLiked(postDetail?.isLiked ?? false)
    setLocalLikesNum(null)
  }, [postDetail?.id])

  const displayCollectsNum = localCollectsNum ?? postDetail?.collectsNum ?? 0
  const displayLikesNum = localLikesNum ?? postDetail?.likesNum ?? 0

  const handleCollect = async () => {
    if (!postDetail?.id) return
    try {
      const result = await collectPost(postDetail.id)
      setIsCollected(result)
      setLocalCollectsNum(prev => {
        const base = Number(prev ?? postDetail?.collectsNum ?? 0)
        return result ? base + 1 : Math.max(0, base - 1)
      })
    } catch (err) {
      message.error(err.message || '操作失败')
    }
  }

  const handleLike = async () => {
    if (!postDetail?.id) return
    try {
      const result = await likePost(postDetail.id)
      setIsLiked(result)
      setLocalLikesNum(prev => {
        const base = Number(prev ?? postDetail?.likesNum ?? 0)
        return result ? base + 1 : Math.max(0, base - 1)
      })
    } catch (err) {
      message.error(err.message || '操作失败')
    }
  }

  const handleUserClick = () => {
    if (postDetail?.userId) {
      const path = mode === 'page' ? '/mobile/profile' : '/profile'
      navigate(`${path}?userId=${postDetail.userId}`)
    }
  }

  const handleFollow = async () => {
    if (!postDetail?.userId) return
    try {
      const result = await followUser(postDetail.userId)
      setIsFollowing(result)
      message.success(result ? '已关注' : '已取消关注')
    } catch (err) {
      message.error(err.message || '操作失败')
    }
  }
  if (mode !== 'page' && !open) return null

  if (postDetail != null && typeof postDetail !== 'object') {
    return null
  }

  const currentUrl = postDetail?.id
    ? `${window.location.origin}/post/${postDetail.id}`
    : window.location.href

  const handleCopyLink = async () => {
    const ok = await copyToClipboard(currentUrl)
    if (ok) message.success('链接已复制到剪贴板')
    else message.error('复制失败')
  }

  const handleSendToFriend = () => {
    message.info('私信功能开发中，敬请期待')
  }

  const shareContent = (
    <div className="share-popover-content">
      <div className="share-popover-buttons">
        <button type="button" className="share-popover-btn" onClick={handleCopyLink}>
          <CopyOutlined className="share-popover-btn-icon" />
          <span>复制链接</span>
        </button>
        <button type="button" className="share-popover-btn" onClick={handleSendToFriend}>
          <SendOutlined className="share-popover-btn-icon" />
          <span>私信给好友</span>
        </button>
      </div>
      <div className="share-popover-qrcode">
        <QRCode value={currentUrl} size={120} />
        <span className="share-popover-qrcode-tip">扫码分享</span>
      </div>
    </div>
  )

  const validPics = Array.isArray(postDetail?.pictureUrl)
    ? postDetail.pictureUrl.filter(url => typeof url === 'string' && url.trim())
    : []

  if (mode === 'page') {
    return (
      <MobilePageWrapper
        title=""
        titleContent={
          <span onClick={handleUserClick} style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {postDetail?.avatar ? (
              <img src={postDetail.avatar} alt={postDetail.username} />
            ) : (
              <div className="mobile-page-title-avatar-default">
                {postDetail?.username?.charAt(0)?.toUpperCase()}
              </div>
            )}
            <span className="mobile-page-title-username">{postDetail?.username}</span>
          </span>
        }
        onClose={onClose}
        rightContent={
          postDetail && currentUsername === postDetail.username ? (
            <button type="button" className="edit-btn" onClick={onEdit}>编辑</button>
          ) : postDetail && currentUsername !== postDetail.username ? (
            <button type="button" className="follow-btn" onClick={handleFollow}>
              {isFollowing ? '已关注' : '关注'}
            </button>
          ) : null
        }
      >
        {postDetail && (
          <PostLayout
            images={validPics}
            currentIndex={detailImageIndex}
            onIndexChange={onImageIndexChange}
          >
            <div className="post-detail-title">{postDetail.title}</div>
            <div className="post-detail-content">{postDetail.content}</div>
            <span className="post-detail-time">
              {postDetail.updateTime ? `编辑于 ${formatTime(postDetail.updateTime)}` : ''}
            </span>
            <div className="post-detail-stats">
              <div className={`post-detail-stat-item post-detail-stat-item-clickable${isLiked ? ' liked' : ''}`} onClick={handleLike}>
                {isLiked ? <LikeFilled /> : <LikeOutlined />}
                <span>{displayLikesNum}</span>
              </div>
              <div className={`post-detail-stat-item post-detail-stat-item-clickable${isCollected ? ' collected' : ''}`} onClick={handleCollect}>
                {isCollected ? <StarFilled /> : <StarOutlined />}
                <span>{displayCollectsNum}</span>
              </div>
              <div className="post-detail-stat-item">
                <MessageOutlined />
                <span>{postDetail.commentNum || 0}</span>
              </div>
              <Popover
                content={shareContent}
                trigger="click"
                placement="top"
                overlayClassName="share-popover-overlay"
                align={{ offset: [0, -8] }}
              >
                <div className="post-detail-stat-item post-detail-share-btn">
                  <ShareAltOutlined />
                  <span>分享</span>
                </div>
              </Popover>
            </div>
            <CommentSection postId={postDetail.id} onCommentCountChange={onCommentCountChange} totalCommentCount={postDetail.commentNum} />
          </PostLayout>
        )}
      </MobilePageWrapper>
    )
  }

  return (
    <PostModal
      open={open}
      onClose={onClose}
      images={validPics}
      currentIndex={detailImageIndex}
      onIndexChange={onImageIndexChange}
    >
      {loading ? (
        <div className="post-detail-loading">
          <Spin size="large" />
        </div>
      ) : postDetail ? (
        <>
          <div className="post-detail-header">
            <div className="post-detail-user-info" onClick={handleUserClick} style={{ cursor: 'pointer' }}>
              {postDetail.avatar ? (
                <img src={postDetail.avatar} alt={postDetail.username} className="post-detail-avatar" />
              ) : (
                <div className="post-detail-avatar post-detail-avatar-default">
                  {postDetail.username?.charAt(0)?.toUpperCase()}
                </div>
              )}
              <span className="post-detail-username">{postDetail.username}</span>
            </div>
            {currentUsername === postDetail.username ? (
              <button type="button" className="edit-btn" onClick={onEdit}>编辑</button>
            ) : (
              <button type="button" className="follow-btn" onClick={handleFollow}>
                {isFollowing ? '已关注' : '关注'}
              </button>
            )}
          </div>
          <div className="post-detail-title">{postDetail.title}</div>
          <div className="post-detail-content">{postDetail.content}</div>
          <span className="post-detail-time">
            {postDetail.updateTime ? `编辑于 ${formatTime(postDetail.updateTime)}` : ''}
          </span>
          <div className="post-detail-stats">
            <div className={`post-detail-stat-item post-detail-stat-item-clickable${isLiked ? ' liked' : ''}`} onClick={handleLike}>
              {isLiked ? <LikeFilled /> : <LikeOutlined />}
              <span>{displayLikesNum}</span>
            </div>
            <div className={`post-detail-stat-item post-detail-stat-item-clickable${isCollected ? ' collected' : ''}`} onClick={handleCollect}>
              {isCollected ? <StarFilled /> : <StarOutlined />}
              <span>{displayCollectsNum}</span>
            </div>
            <div className="post-detail-stat-item">
              <MessageOutlined />
              <span>{postDetail.commentNum || 0}</span>
            </div>
            <Popover
              content={shareContent}
              trigger="click"
              placement="top"
              overlayClassName="share-popover-overlay"
              align={{ offset: [0, -8] }}
            >
              <div className="post-detail-stat-item post-detail-share-btn">
                <ShareAltOutlined />
                <span>分享</span>
              </div>
            </Popover>
          </div>
          <CommentSection postId={postDetail.id} onCommentCountChange={onCommentCountChange} totalCommentCount={postDetail.commentNum} />
        </>
      ) : null}
    </PostModal>
  )
}

export default PostDetailModal
