import { FloatButton, Tooltip } from 'antd'
import { PlusOutlined, SyncOutlined } from '@ant-design/icons'

function FloatingActions({ onCreate, onRefresh, showTopThreshold = 400 }) {
  return (
    <div className="float-actions">
      {onCreate && (
        <Tooltip title="发帖" placement="left">
          <button type="button" className="float-action-btn" onClick={onCreate}>
            <PlusOutlined />
          </button>
        </Tooltip>
      )}
      {onRefresh && (
        <Tooltip title="刷新" placement="left">
          <button type="button" className="float-action-btn" onClick={onRefresh}>
            <SyncOutlined />
          </button>
        </Tooltip>
      )}
      <FloatButton.BackTop
        visibilityHeight={showTopThreshold}
        style={{ position: 'relative', bottom: 0, right: 0, width: 48, height: 48 }}
      />
    </div>
  )
}

export default FloatingActions
