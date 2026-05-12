import { Button } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'

function BulkActionBar({ selectedCount, onSelectAll, onDeselectAll, onDelete, extraActions }) {
  return (
    <div className="bulk-action-bar">
      <span className="bulk-action-bar-info">
        已选择 <span className="bulk-action-bar-count">{selectedCount}</span> 项
      </span>
      <Button size="small" onClick={onSelectAll}>全选</Button>
      <Button size="small" onClick={onDeselectAll}>取消全选</Button>
      {extraActions}
      <Button size="small" danger icon={<DeleteOutlined />} onClick={onDelete}>
        删除
      </Button>
    </div>
  )
}

export default BulkActionBar
