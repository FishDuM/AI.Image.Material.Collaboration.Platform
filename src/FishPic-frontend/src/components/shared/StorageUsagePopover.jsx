import { ApartmentOutlined, DatabaseOutlined, HddOutlined, UploadOutlined } from '@ant-design/icons'
import { Popover, Progress } from 'antd'
import { LEVEL_MAP, storageStrokeColor } from '../../utils/constants'

function StorageCenter({ levelInfo, percent }) {
  return (
    <div className="level-center">
      {levelInfo?.className ? (
        <span className={`level-text ${levelInfo.className}`}>{levelInfo.label}</span>
      ) : null}
      <span className="level-percent">{percent}%</span>
    </div>
  )
}

function PrivateStorageCard({ spaceInfo }) {
  const levelInfo = LEVEL_MAP[spaceInfo.level] || {}

  return (
    <div className={`storage-card ${levelInfo.cardClass || ''}`}>
      <div className="storage-card-header">
        <DatabaseOutlined className="storage-card-header-icon" />
        <span className="storage-card-header-text">空间详情</span>
      </div>
      <div className="storage-card-grid">
        <div className="storage-card-item">
          <ApartmentOutlined className="storage-card-item-icon" />
          <div className="storage-card-item-content">
            <span className="storage-card-item-label">等级</span>
            <span className={`storage-card-item-value ${levelInfo.className || ''}`}>{levelInfo.label || '-'}</span>
          </div>
        </div>
        <div className="storage-card-item">
          <HddOutlined className="storage-card-item-icon" />
          <div className="storage-card-item-content">
            <span className="storage-card-item-label">已用</span>
            <span className="storage-card-item-value">{spaceInfo.usedText}</span>
          </div>
        </div>
        <div className="storage-card-item">
          <DatabaseOutlined className="storage-card-item-icon" />
          <div className="storage-card-item-content">
            <span className="storage-card-item-label">总容量</span>
            <span className="storage-card-item-value">{spaceInfo.totalText}</span>
          </div>
        </div>
        <div className="storage-card-item">
          <UploadOutlined className="storage-card-item-icon" />
          <div className="storage-card-item-content">
            <span className="storage-card-item-label">占用率</span>
            <span className="storage-card-item-value">{spaceInfo.percent}%</span>
          </div>
        </div>
      </div>
    </div>
  )
}

function TeamStorageCard({ spaceInfo, levelInfo }) {
  return (
    <div className={`storage-card ${levelInfo.cardClass}`}>
      <div className="storage-card-title">空间详情</div>
      <div className="storage-card-row">
        <span className="storage-card-label">空间等级</span>
        <span className={`storage-card-value ${levelInfo.className}`}>{levelInfo.label}</span>
      </div>
      <div className="storage-card-row">
        <span className="storage-card-label">占用比例</span>
        <span className="storage-card-value">{spaceInfo.percent}%</span>
      </div>
      <div className="storage-card-row">
        <span className="storage-card-label">已占用空间</span>
        <span className="storage-card-value">{spaceInfo.usedText}</span>
      </div>
      <div className="storage-card-row">
        <span className="storage-card-label">总空间</span>
        <span className="storage-card-value">{spaceInfo.totalText}</span>
      </div>
      <div className="storage-card-row">
        <span className="storage-card-label">图片数量</span>
        <span className="storage-card-value">{spaceInfo.pictureCount ?? 0}</span>
      </div>
      <div className="storage-card-row">
        <span className="storage-card-label">创建人</span>
        <span className="storage-card-value">{spaceInfo.userName || '未知'}</span>
      </div>
    </div>
  )
}

function StorageUsagePopover({ spaceInfo, levelInfo, variant = 'private' }) {
  const resolvedLevelInfo = levelInfo || LEVEL_MAP[spaceInfo.level] || {}

  return (
    <Popover
      content={variant === 'team'
        ? <TeamStorageCard spaceInfo={spaceInfo} levelInfo={resolvedLevelInfo} />
        : <PrivateStorageCard spaceInfo={spaceInfo} />}
      trigger="hover"
      placement="bottom"
    >
      <Progress
        type="circle"
        percent={spaceInfo.percent}
        strokeColor={storageStrokeColor}
        size={72}
        className="storage-progress"
        format={() => <StorageCenter levelInfo={resolvedLevelInfo} percent={spaceInfo.percent} />}
      />
    </Popover>
  )
}

export default StorageUsagePopover
