import { useState, useEffect, useCallback } from 'react'
import { App, Modal, Button, Tabs, Checkbox, Tag, Spin, Empty, Alert } from 'antd'
import { SaveOutlined, TeamOutlined, LockOutlined } from '@ant-design/icons'
import { listSpace, savePictureByUrl } from '../../api'

const SPACE_TYPE_MAP = { 0: '私人空间', 1: '团队空间' }
const SPACE_TYPE_COLOR = { 0: 'blue', 1: 'green' }

function SaveToSpaceModal({ open, onClose, imageUrl }) {
  const { message } = App.useApp()
  const [activeTab, setActiveTab] = useState('private')
  const [privateSpace, setPrivateSpace] = useState(null)
  const [teamSpaces, setTeamSpaces] = useState([])
  const [selectedSpaceIds, setSelectedSpaceIds] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const loadSpaces = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setPrivateSpace(list.length > 0 ? list[0] : null)

      const teamResult = await listSpace(1)
      const teamList = Array.isArray(teamResult) ? teamResult : []
      setTeamSpaces(teamList)
    } catch {
      setPrivateSpace(null)
      setTeamSpaces([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (open) {
      setActiveTab('private')
      setSelectedSpaceIds([])
      loadSpaces()
    }
  }, [open, loadSpaces])

  const toggleSpace = useCallback((spaceId) => {
    setSelectedSpaceIds((prev) =>
      prev.includes(spaceId) ? prev.filter((id) => id !== spaceId) : [...prev, spaceId]
    )
  }, [])

  const handleConfirm = async () => {
    if (selectedSpaceIds.length === 0) {
      message.warning('请至少选择一个空间')
      return
    }
    if (!imageUrl) {
      message.error('图片URL为空')
      return
    }

    setSaving(true)
    let successCount = 0
    let failCount = 0

    try {
      const results = await Promise.allSettled(
        selectedSpaceIds.map((spaceId) => savePictureByUrl(imageUrl, spaceId))
      )

      results.forEach((r) => {
        if (r.status === 'fulfilled') successCount++
        else failCount++
      })

      if (successCount > 0) {
        message.success(`已保存到 ${successCount} 个空间`)
      }
      if (failCount > 0) {
        message.error(`${failCount} 个空间保存失败`)
      }

      onClose()
    } catch {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const formatStorage = (bytes) => {
    if (!bytes) return '-'
    if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB'
    if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB'
    return (bytes / 1024).toFixed(0) + ' KB'
  }

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
            <span>已用 {formatStorage(space.size)} / {formatStorage(space.storageSize)}</span>
          </div>
        </div>
      </div>
    )
  }

  const tabItems = [
    {
      key: 'private',
      label: (
        <span><LockOutlined style={{ marginRight: 4 }} />私人空间</span>
      ),
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
      label: (
        <span><TeamOutlined style={{ marginRight: 4 }} />团队空间</span>
      ),
      children: (
        <Spin spinning={loading}>
          <div className="save-space-list">
            {teamSpaces.length === 0 ? (
              <Empty description="暂无团队空间" style={{ padding: '40px 0' }} />
            ) : (
              teamSpaces.map((sp) => renderSpaceItem(sp, false))
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
          onClick={handleConfirm}
        >
          确认保存{selectedSpaceIds.length > 0 ? ` (${selectedSpaceIds.length} 个空间)` : ''}
        </Button>,
      ]}
    >
      <Alert
        message="选择要保存到的空间，支持同时保存到多个空间"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
      />
    </Modal>
  )
}

export default SaveToSpaceModal
