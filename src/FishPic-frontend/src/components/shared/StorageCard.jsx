import { Progress, Button } from 'antd'
import { CloudOutlined, CrownOutlined } from '@ant-design/icons'

function StorageCard({ usedStorage, totalStorage, level, onUpgrade }) {
  const usedMB = (usedStorage / 1024 / 1024).toFixed(1)
  const totalMB = (totalStorage / 1024 / 1024).toFixed(0)
  const percent = totalStorage > 0 ? Math.min(100, Math.round((usedStorage / totalStorage) * 100)) : 0

  return (
    <div className="storage-card">
      <Progress
        type="circle"
        percent={percent}
        size={80}
        strokeColor={percent > 90 ? 'var(--error)' : 'var(--accent)'}
        format={(p) => `${p}%`}
      />
      <div className="storage-card-info">
        <div className="storage-card-title">
          <CloudOutlined style={{ marginRight: 8 }} />
          存储空间
          {level && <span className="storage-card-level" style={{ marginLeft: 8 }}>{level}</span>}
        </div>
        <div className="storage-card-detail">
          已使用 {usedMB} MB / {totalMB} MB
        </div>
        {onUpgrade && (
          <Button size="small" icon={<CrownOutlined />} onClick={onUpgrade} style={{ marginTop: 8 }}>
            升级空间
          </Button>
        )}
      </div>
    </div>
  )
}

export default StorageCard
