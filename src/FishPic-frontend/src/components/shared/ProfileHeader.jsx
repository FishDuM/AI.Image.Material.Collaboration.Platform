import { EditOutlined } from '@ant-design/icons'
import { Button, message } from 'antd'

function ProfileHeader({ userInfo, postsCount, collectsCount, likesCount, onEdit, onAvatarPreview }) {
  const handleCopyAccount = () => {
    if (userInfo?.userAccount) {
      navigator.clipboard?.writeText(userInfo.userAccount).then(
        () => message.success('账号已复制'),
        () => message.error('复制失败')
      )
    }
  }

  return (
    <div className="profile-header">
      <div className="profile-header-top">
        {userInfo?.avatarUrl ? (
          <img
            src={userInfo.avatarUrl}
            alt={userInfo.userName}
            className="profile-avatar"
            onClick={() => onAvatarPreview?.(userInfo.avatarUrl)}
            style={{ cursor: 'pointer' }}
          />
        ) : (
          <div className="profile-avatar-default">
            {userInfo?.userName?.charAt(0)?.toUpperCase() || '?'}
          </div>
        )}
        <div className="profile-info-group">
          <div className="profile-name">{userInfo?.userName || '未设置昵称'}</div>
          <div className="profile-account" onClick={handleCopyAccount}>
            账号: {userInfo?.userAccount || '-'}
          </div>
          {userInfo?.userRole === 1 && (
            <span style={{
              display: 'inline-block',
              marginTop: 4,
              padding: '1px 8px',
              background: 'var(--accent)',
              color: '#fff',
              borderRadius: 4,
              fontSize: 11,
              fontWeight: 600,
            }}>
              管理员
            </span>
          )}
        </div>
        <Button
          icon={<EditOutlined />}
          onClick={onEdit}
          style={{ marginLeft: 'auto' }}
        >
          编辑资料
        </Button>
      </div>
      <div className="profile-stats-row">
        <div className="profile-stat-item">
          <div className="profile-stat-value">{postsCount || 0}</div>
          <div className="profile-stat-label">图文</div>
        </div>
        <div className="profile-stat-item">
          <div className="profile-stat-value">{collectsCount || 0}</div>
          <div className="profile-stat-label">收藏</div>
        </div>
        <div className="profile-stat-item">
          <div className="profile-stat-value">{likesCount || 0}</div>
          <div className="profile-stat-label">点赞</div>
        </div>
      </div>
    </div>
  )
}

export default ProfileHeader
