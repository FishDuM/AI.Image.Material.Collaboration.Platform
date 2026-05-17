import { Modal, Image as AntImage, Spin, Popover, QRCode, App } from 'antd'
import { LikeOutlined, HeartOutlined, StarOutlined, LeftOutlined, RightOutlined, FileTextOutlined, ShareAltOutlined, CopyOutlined, SendOutlined } from '@ant-design/icons'
import MobilePageWrapper from './MobilePageWrapper'
import './PostDetailModal.css'

const formatTime = (timeString) => {
  if (!timeString) return ''
  const now = new Date()
  const updateTime = new Date(timeString)
  const diffMs = now - updateTime
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffDays >= 7) {
    return updateTime.toLocaleString('zh-CN')
  } else if (diffDays >= 1) {
    return `${diffDays}天前`
  } else if (diffHours >= 1) {
    return `${diffHours}小时前`
  } else if (diffMinutes >= 1) {
    return `${diffMinutes}分钟前`
  } else {
    return '刚刚'
  }
}

function PostDetailModal({
  open,
  onClose,
  loading,
  postDetail,
  detailImageIndex,
  onImageIndexChange,
  currentUsername,
  onEdit,
  mode = 'modal',
}) {
  const { message } = App.useApp()
  if (mode !== 'page' && !open) return null

  if (postDetail != null && typeof postDetail !== 'object') {
    return null
  }

  const currentUrl = postDetail?.id
    ? `${window.location.origin}/post/${postDetail.id}`
    : window.location.href

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(currentUrl)
      message.success('链接已复制到剪贴板')
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = currentUrl
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      message.success('链接已复制到剪贴板')
    }
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

  const handlePrevImage = () => {
    if (detailImageIndex > 0) {
      onImageIndexChange(detailImageIndex - 1)
    }
  }

  const handleNextImage = () => {
    if (detailImageIndex < validPics.length - 1) {
      onImageIndexChange(detailImageIndex + 1)
    }
  }

  const renderContent = () => (
    loading ? (
      <div className="post-detail-loading">
        <Spin size="large" />
      </div>
    ) : postDetail ? (
      <div className="xiaohongshu-layout">
        <div className="left-image-area">
          {validPics.length > 0 ? (
            <div className="carousel-main">
              <AntImage
                src={validPics[detailImageIndex]}
                alt={postDetail.title}
                className="carousel-main-image"
                preview={true}
              />
              {validPics.length > 1 && (
                <>
                  <button
                    type="button"
                    className="carousel-arrow carousel-arrow-left"
                    onClick={handlePrevImage}
                    disabled={detailImageIndex === 0}
                  >
                    <LeftOutlined />
                  </button>
                  <button
                    type="button"
                    className="carousel-arrow carousel-arrow-right"
                    onClick={handleNextImage}
                    disabled={detailImageIndex === validPics.length - 1}
                  >
                    <RightOutlined />
                  </button>
                  <div className="carousel-counter">
                    {detailImageIndex + 1} / {validPics.length}
                  </div>
                </>
              )}
            </div>
          ) : (
            <div className="no-image-placeholder">
              <FileTextOutlined style={{ fontSize: 64, color: 'rgba(255,255,255,0.3)' }} />
              <p>暂无图片</p>
            </div>
          )}
        </div>
        <div className="right-form-area">
          {mode !== 'page' && (
            <div className="post-detail-header">
              <div className="post-detail-user-info">
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
                <button type="button" className="follow-btn">关注</button>
              )}
            </div>
          )}
          <div className="post-detail-title">{postDetail.title}</div>
          <div className="post-detail-content">{postDetail.content}</div>
          <span className="post-detail-time">
            {postDetail.updateTime ? `编辑于 ${formatTime(postDetail.updateTime)}` : ''}
          </span>
          <div className="post-detail-stats">
            <div className="post-detail-stat-item">
              <LikeOutlined />
              <span>{postDetail.likesNum || 0}</span>
            </div>
            <div className="post-detail-stat-item">
              <HeartOutlined />
              <span>{postDetail.collectsNum || 0}</span>
            </div>
            <div className="post-detail-stat-item">
              <StarOutlined />
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
        </div>
      </div>
    ) : null
  )

  if (mode === 'page') {
    return (
      <MobilePageWrapper
        title=""
        titleContent={
          <>
            {postDetail?.avatar ? (
              <img src={postDetail.avatar} alt={postDetail.username} />
            ) : (
              <div className="mobile-page-title-avatar-default">
                {postDetail?.username?.charAt(0)?.toUpperCase()}
              </div>
            )}
            <span className="mobile-page-title-username">{postDetail?.username}</span>
          </>
        }
        onClose={onClose}
        rightContent={
          postDetail && currentUsername === postDetail.username ? (
            <button type="button" className="edit-btn" onClick={onEdit}>编辑</button>
          ) : postDetail && currentUsername !== postDetail.username ? (
            <button type="button" className="follow-btn">关注</button>
          ) : null
        }
      >
        {renderContent()}
      </MobilePageWrapper>
    )
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      className="post-detail-modal"
      width="80vw"
      style={{ maxHeight: '75vh' }}
      closable={false}
      destroyOnHidden
    >
      {renderContent()}
    </Modal>
  )
}

export default PostDetailModal
