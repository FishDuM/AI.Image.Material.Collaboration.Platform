import { useContext, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Alert, App as AntApp, Button, Checkbox, Empty, Spin, Tabs, Tag } from 'antd'
import { LockOutlined, SaveOutlined, TeamOutlined } from '@ant-design/icons'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { AuthContext } from '../context/AuthContext'
import { formatStorage, SPACE_TYPE_COLOR, SPACE_TYPE_MAP } from '../utils/constants'
import { useSaveToSpace } from '../hooks/useSaveToSpace'
import './MobileSaveToSpacePage.css'

function MobileSaveToSpacePage() {
  const { message } = AntApp.useApp()
  const location = useLocation()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const { imageUrl } = location.state || {}

  const {
    activeTab,
    setActiveTab,
    privateSpace,
    teamSpaces,
    selectedSpaceIds,
    loading,
    saving,
    loadSpaces,
    toggleSpace,
    saveSelectedSpaces,
  } = useSaveToSpace({ imageUrl, message, onSaved: () => navigate(-1) })

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
            <span>已用 {formatStorage(space.size, '-')} / {formatStorage(space.storageSize, '-')}</span>
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
              teamSpaces.map(space => renderSpaceItem(space, false))
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

        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />

        <div className="mobile-save-actions">
          <Button
            type="primary"
            icon={<SaveOutlined />}
            block
            size="large"
            loading={saving}
            disabled={selectedSpaceIds.length === 0}
            onClick={saveSelectedSpaces}
          >
            确认保存{selectedSpaceIds.length > 0 ? ` (${selectedSpaceIds.length} 个空间)` : ''}
          </Button>
        </div>
      </div>
    </MobilePageWrapper>
  )
}

export default MobileSaveToSpacePage
