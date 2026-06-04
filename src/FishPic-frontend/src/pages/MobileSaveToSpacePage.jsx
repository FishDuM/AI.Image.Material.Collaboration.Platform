import { useState, useEffect, useCallback, useContext } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { App as AntApp, Button, Tabs, Checkbox, Tag, Spin, Empty, Alert } from 'antd'
import { SaveOutlined, TeamOutlined, LockOutlined } from '@ant-design/icons'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { listSpace, savePictureByUrl } from '../api'
import { AuthContext } from '../context/AuthContext'
import './MobileSaveToSpacePage.css'

const SPACE_TYPE_MAP = { 0: '私人空间', 1: '团队空间' }
const SPACE_TYPE_COLOR = { 0: 'blue', 1: 'green' }

function MobileSaveToSpacePage() {
  const { message } = AntApp.useApp()
  const location = useLocation()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)

  const { imageUrl } = location.state || {}
  const [activeTab, setActiveTab] = useState('private')
  const [privateSpace, setPrivateSpace] = useState(null)
  const [teamSpaces, setTeamSpaces] = useState([])
  const [selectedSpaceIds, setSelectedSpaceIds] = useState([])
  const [loading, setLoading] = useState(true)
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
    if (!userInfo) {
      navigate('/mobile/login', { replace: true })
      return
    }
    if (!imageUrl) {
      message.error('图片信息丢失')
      navigate(-1)
      return
    }
    loadSpaces()
  }, [userInfo, imageUrl, navigate, message, loadSpaces])

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

      navigate(-1)
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
        className={`mobile-save-space-item${isChecked ? ' mobile-save-space-item-checked' : ''}`}
        onClick={() => toggleSpace(space.id)}
      >
        <Checkbox checked={isChecked} className="mobile-save-space-checkbox" />
        <div className="mobile-save-space-info">
          <div className="mobile-save-space-name">
            {isPrivate ? <LockOutlined style={{ marginRight: 6 }} /> : <TeamOutlined style={{ marginRight: 6 }} />}
            {space.name || (isPrivate ? '私人空间' : '团队空间')}
          </div>
          <div className="mobile-save-space-meta">
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
      label: '私人空间',
      children: (
        <Spin spinning={loading}>
          <div className="mobile-save-space-list">
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
      label: '团队空间',
      children: (
        <Spin spinning={loading}>
          <div className="mobile-save-space-list">
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
    <MobilePageWrapper title="保存到空间">
      <div className="mobile-save-to-space-page">
        <Alert
          message="选择要保存到的空间，支持同时保存到多个空间"
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
        />

        {imageUrl && (
          <div className="mobile-save-preview">
            <img src={imageUrl} alt="预览" className="mobile-save-preview-img" />
          </div>
        )}

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
        />

        <div className="mobile-save-actions">
          <Button
            type="primary"
            icon={<SaveOutlined />}
            block
            size="large"
            loading={saving}
            disabled={selectedSpaceIds.length === 0}
            onClick={handleConfirm}
          >
            确认保存{selectedSpaceIds.length > 0 ? ` (${selectedSpaceIds.length} 个空间)` : ''}
          </Button>
        </div>
      </div>
    </MobilePageWrapper>
  )
}

export default MobileSaveToSpacePage
