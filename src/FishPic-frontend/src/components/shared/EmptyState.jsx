import { Empty } from 'antd'

function EmptyState({ icon, title = '暂无数据', description = '', style }) {
  return (
    <div className="empty-state" style={style}>
      {icon && <div className="empty-state-icon">{icon}</div>}
      <div className="empty-state-title">{title}</div>
      {description && <div className="empty-state-desc">{description}</div>}
      {!icon && <Empty description={false} />}
    </div>
  )
}

export default EmptyState
