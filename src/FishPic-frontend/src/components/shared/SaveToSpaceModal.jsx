import { useEffect } from 'react'
import { Alert, App, Button, Checkbox, Empty, Modal, Spin, Tabs, Tag } from 'antd'
import { LockOutlined, SaveOutlined, TeamOutlined } from '@ant-design/icons'
import { formatStorage } from '../../utils/constants'
import { SPACE_TYPE_COLOR, SPACE_TYPE_MAP, useSaveToSpace } from '../../hooks/useSaveToSpace'

function SaveToSpaceModal({ open, onClose, imageUrl }) {
  const { message } = App.useApp()
  const {
    activeTab,
    setActiveTab,
    privateSpace,
    teamSpaces,
    selectedSpaceIds,
    loading,
    saving,
    reset,
    loadSpaces,
    toggleSpace,
    saveSelectedSpaces,
  } = useSaveToSpace({ imageUrl, message, onSaved: onClose })

  useEffect(() => {
    if (open) {
      reset()
      loadSpaces()
    }
  }, [open, loadSpaces, reset])

  const renderSpaceItem = (space, isPrivate) => {
    const isChecked = selectedSpaceIds.includes(space.id)
    return (
      <div
        key={space.id}
        className={`save-space-item${isChecked ? ' save-space-item-checked' : ''}`}
        onClick={() => toggleSpace(space.id)}
      >
        <Checkbox checked={isChecked} className="save-space-checkbox" />
        <div className="save-space-info">
          <div className="save-space-name">
            {isPrivate ? <LockOutlined style={{ marginRight: 6 }} /> : <TeamOutlined style={{ marginRight: 6 }} />}
            {space.name || (isPrivate ? '私人空间' : '团队空间')}
          </div>
          <div className="save-space-meta">
            <Tag color={SPACE_TYPE_COLOR[space.type]} style={{ marginRight: 8 }}>
              {SPACE_TYPE_MAP[space.type]}
            </Tag>
            <span>已用 {formatStorage(space.size, '-')} / {formatStorage(space.storageSize, '-')}</span>
          </div>
        </div>
      </div>
    )
  }

  const tabItems = [
    {
      key: 'private',
      label: <span><LockOutlined style={{ marginRight: 4 }} />私人空间</span>,
      children: (
        <Spin spinning={loading}>
          <div className="save-space-list">
            {!privateSpace ? (
              <Empty description="暂无私人空间" style={{ padding: '40px 0' }} />
            ) : (
              renderSpaceItem(privateSpace, true)
            )}
          </div>
        </Spin>
      ),
    },
    {
      key: 'team',
      label: <span><TeamOutlined style={{ marginRight: 4 }} />团队空间</span>,
      children: (
        <Spin spinning={loading}>
          <div className="save-space-list">
            {teamSpaces.length === 0 ? (
              <Empty description="暂无团队空间" style={{ padding: '40px 0' }} />
            ) : (
              teamSpaces.map(space => renderSpaceItem(space, false))
            )}
          </div>
        </Spin>
      ),
    },
  ]

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="保存到空间"
      className="save-to-space-modal"
      width={520}
      footer={[
        <Button key="cancel" onClick={onClose} disabled={saving}>
          取消
        </Button>,
        <Button
          key="confirm"
          type="primary"
          icon={<SaveOutlined />}
          loading={saving}
          disabled={selectedSpaceIds.length === 0}
          onClick={saveSelectedSpaces}
        >
          确认保存{selectedSpaceIds.length > 0 ? ` (${selectedSpaceIds.length} 个空间)` : ''}
        </Button>,
      ]}
    >
      <Alert
        title="选择要保存到的空间，支持同时保存到多个空间"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
    </Modal>
  )
}

export default SaveToSpaceModal
