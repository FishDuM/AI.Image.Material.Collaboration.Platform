import { useState, useEffect, useCallback, useMemo, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Typography, Button, Modal, Form, Input, Select, Masonry, Image as AntImage, Spin, Empty, Popconfirm, Progress, Popover } from 'antd'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, ArrowUpOutlined, EditOutlined, CloudUploadOutlined, DatabaseOutlined, HddOutlined, UploadOutlined, ApartmentOutlined, ShareAltOutlined, StarOutlined } from '@ant-design/icons'
import { updateSpace, listSpace, getSystemTypes, createShare } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { AuthContext } from '../context/AuthContext'
import { PAGE_SIZE, LEVEL_MAP, storageStrokeColor, formatStorage } from '../utils/constants'
import { getThumbnailUrl } from '../utils/image'
import { logError } from '../utils/logger'
import { useSpacePictures } from './PrivateSpace/useSpacePictures'
import ImageUploadModal from '../components/shared/ImageUploadModal'
import ImageEditorModal from '../components/shared/ImageEditorModal'
import UpgradeModal from '../components/shared/UpgradeModal'
import './PrivateSpace.css'

const { Title } = Typography

function PrivateSpace() {
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { message, modal } = AntApp.useApp()
  const [systemTags, setSystemTags] = useState([])
  const { userInfo } = useContext(AuthContext)
  const [spaces, setSpaces] = useState([])
  const [showEdit, setShowEdit] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)
  const [editForm] = Form.useForm()

  const [showUpgrade, setShowUpgrade] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [shareForm] = Form.useForm()
  const [shareLoading, setShareLoading] = useState(false)
  const [shareLink, setShareLink] = useState('')
  const handleOpenShare = () => {
    if (selectedIds.length === 0) {
      message.warning('请选择至少一张图片进行分享')
      return
    }
    // 检查所选图片是否都属于当前用户
    const targetPics = pictures.filter(p => selectedIds.includes(p.id))
    const hasForeignPic = targetPics.some(p => p.userId && userInfo && p.userId !== userInfo.id)
    if (hasForeignPic) {
      message.warning('只能分享自己上传的图片')
      return
    }
    shareForm.resetFields()
    setShareLink('')
    setShowShare(true)
  }

  const handleCreateShare = async (values) => {
    setShareLoading(true)
    try {
      const token = await createShare({
        pictureIds: selectedIds,
        expireDays: values.expireDays || 1,
        allowDownload: values.allowDownload ? 1 : 0,
      })
      const link = `${window.location.origin}/s/${token}`
      setShareLink(link)
      message.success('分享链接已生成')
    } catch (error) {
      message.error(error.message || '创建分享失败')
    } finally {
      setShareLoading(false)
    }
  }

  const handleCopyShareLink = () => {
    navigator.clipboard.writeText(shareLink).then(() => {
      message.success('链接已复制到剪贴板')
    }).catch(() => {
      message.error('复制失败，请手动复制')
    })
  }

  useEffect(() => {
    getSystemTypes().then(result => {
      if (Array.isArray(result)) setSystemTags(result)
    }).catch((error) => { logError('getSystemTypes', error) })
  }, [])

  const fetchSpaces = useCallback(async () => {
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
    } catch (error) {
      logError('fetchSpaces', error)
      setSpaces([])
    }
  }, [])

  const {
    pictures,
    pictureLoading,
    searchKeyword,
    setSearchKeyword,
    hasMore,
    loadingMore,
    batchMode,
    selectedIds,
    showEditPicture,
    setShowEditPicture,
    editPictureLoading,
    editPictureForm,
    showUploadModal,
    setShowUploadModal,
    showImageEditor,
    setShowImageEditor,
    masonryItems,
    handleSearch,
    handleSearchReset,
    toggleBatchMode,
    toggleSelect,
    handleBatchDelete,
    handleEditPictureOpen,
    handleUploadSuccess,
    handleEditPictureSubmit,
    handleAiTag,
  } = useSpacePictures({
    spaces,
    pageSize: PAGE_SIZE,
    refreshSpaces: fetchSpaces,
    message,
    modal,
    navigate,
    isMobile,
    userInfo,
    systemTags,
  })

  useEffect(() => {
    const init = async () => { await fetchSpaces() }
    init()
  }, [fetchSpaces])

  const spaceInfo = useMemo(() => {
    if (!spaces.length) return null
    const s = spaces[0]
    const sizeBytes = Number(s.size) || 0
    const storageBytes = Number(s.storageSize) || 0
    const percent = storageBytes > 0 ? Math.min(100, Math.round((sizeBytes / storageBytes) * 100)) : 0
    return { ...s, percent, usedText: formatStorage(sizeBytes), totalText: formatStorage(storageBytes) }
  }, [spaces])

  const handleEditOpen = () => {
    if (spaces.length > 0) {
      editForm.setFieldsValue({ name: spaces[0].name, introduction: spaces[0].introduction })
      setShowEdit(true)
    }
  }

  const handleUpdate = async (values) => {
    setUpdateLoading(true)
    try {
      await updateSpace({
        id: spaces[0].id,
        name: values.name,
        introduction: values.introduction || '',
      })
      message.success('修改成功')
      setShowEdit(false)
      editForm.resetFields()
      fetchSpaces()
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '修改失败')
    } finally {
      setUpdateLoading(false)
    }
  }

  return (
    <main className="private-space-container">
      <div className="private-space-header">
        <div className="private-space-header-left">
          <Title level={2} className="private-space-title">
            私人空间{spaces.length > 0 && ` - ${spaces[0].name}`}
          </Title>
          <p className="header-subtitle">
            {spaces.length > 0 && spaces[0].introduction ? spaces[0].introduction : '你的专属私密存储空间'}
          </p>
        </div>
        {spaceInfo && (
          <div className="private-space-header-right">
            <Popover
              content={
                <div className={`storage-card ${LEVEL_MAP[spaceInfo.level]?.cardClass || ''}`}>
                  <div className="storage-card-header">
                    <DatabaseOutlined className="storage-card-header-icon" />
                    <span className="storage-card-header-text">空间详情</span>
                  </div>
                  <div className="storage-card-grid">
                    <div className="storage-card-item">
                      <ApartmentOutlined className="storage-card-item-icon" />
                      <div className="storage-card-item-content">
                        <span className="storage-card-item-label">等级</span>
                        <span className={`storage-card-item-value ${LEVEL_MAP[spaceInfo.level]?.className || ''}`}>{LEVEL_MAP[spaceInfo.level]?.label || '-'}</span>
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
              }
              trigger="hover"
              placement="bottom"
            >
              <Progress
                type="circle"
                percent={spaceInfo.percent}
                strokeColor={storageStrokeColor}
                size={72}
                className="storage-progress"
                format={() => (
                  <div className="level-center">
                    {LEVEL_MAP[spaceInfo.level] && (
                      <span className={`level-text ${LEVEL_MAP[spaceInfo.level].className}`}>{LEVEL_MAP[spaceInfo.level].label}</span>
                    )}
                    <span className="level-percent">{spaceInfo.percent}%</span>
                  </div>
                )}
              />
            </Popover>
            <Button onClick={handleEditOpen}>
              修改空间
            </Button>
            <Button icon={<ArrowUpOutlined />} className="private-space-upgrade-btn" onClick={() => isMobile ? navigate('/mobile/upgrade') : setShowUpgrade(true)}>
              升级空间
            </Button>
          </div>
        )}
      </div>

      {spaces.length === 0 && (
        <Empty description="暂无私人空间" style={{ marginTop: 80 }} />
      )}

      {spaces.length > 0 && (
        <div className="private-space-search-bar">
          <Input
            className="private-space-search-input"
            placeholder="搜索图片..."
            prefix={<SearchOutlined />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            搜索
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleSearchReset}>
            重置
          </Button>
          <Button
            icon={<DeleteOutlined />}
            onClick={toggleBatchMode}
            type={batchMode ? 'primary' : 'default'}
            danger={batchMode}
          >
            {batchMode ? '退出选择' : '选择图片'}
          </Button>
          <Button
            icon={<CloudUploadOutlined />}
            onClick={() => setShowUploadModal(true)}
            disabled={batchMode}
          >
            上传图片
          </Button>
        </div>
      )}

      {spaces.length > 0 && (
        <div className="private-space-masonry-section">
          {pictureLoading && (
            <div className="private-space-loading">
              <Spin />
            </div>
          )}
          {!pictureLoading && masonryItems.length === 0 && (
            <Empty description="暂无图片" style={{ marginTop: 60 }} />
          )}
          {!pictureLoading && masonryItems.length > 0 && (
            <Masonry
              columns={{ xs: 2, sm: 3, md: 4, lg: 5 }}
              gutter={[12, 12]}
              fresh
              items={masonryItems}
              itemRender={(item) => {
                const isSelected = selectedIds.includes(item.data.id)
                return (
                  <div
                    className={`private-space-masonry-item ${batchMode ? 'batch-mode' : ''}`}
                    onClick={batchMode ? () => toggleSelect(item.data.id) : undefined}
                  >
                    <AntImage
                      src={getThumbnailUrl(item.data.url, 400)}
                      alt={item.data.pictureName || '图片'}
                      preview={!batchMode ? { src: item.data.url } : false}
                      className="private-space-masonry-image"
                    />
                    {batchMode && (
                      <div className="private-space-masonry-select">
                        <div className={`private-space-masonry-checkbox ${isSelected ? 'checked' : ''}`}>
                          {isSelected && <CheckOutlined />}
                        </div>
                      </div>
                    )}
                  </div>
                )
              }}
            />
          )}
          {loadingMore && (
            <div className="load-more-indicator">
              <Spin size="small" />
              <span>加载中...</span>
            </div>
          )}
          {!hasMore && masonryItems.length > 0 && (
            <div className="load-more-indicator">没有更多了</div>
          )}
          {batchMode && (
            <div className="private-space-batch-bar">
              <span className="private-space-batch-count">
                已选择 <strong>{selectedIds.length}</strong> 张图片
              </span>
              <div className="private-space-batch-actions">
                <Button
                  icon={<CloseOutlined />}
                  onClick={toggleBatchMode}
                >
                  取消
                </Button>
                <Button
                  icon={<EditOutlined />}
                  onClick={handleEditPictureOpen}
                  disabled={selectedIds.length === 0}
                >
                  编辑图片信息
                </Button>
                <Button
                  icon={<ShareAltOutlined />}
                  onClick={handleOpenShare}
                  disabled={selectedIds.length === 0}
                >
                  分享
                </Button>
                <Button
                  icon={<StarOutlined />}
                  onClick={async () => {
                    try {
                      await updatePicture({ id: selectedIds[0], isSelected: 1 })
                      message.success('已提交精选申请')
                      setSelectedIds([])
                      setBatchMode(false)
                      doFetchPictures(spaces[0].id, 1, searchKeyword)
                    } catch (err) {
                      message.error(err.message || '申请失败')
                    }
                  }}
                  disabled={selectedIds.length !== 1}
                  style={{ color: '#d4a017', borderColor: '#d4a017' }}
                >
                  申请精选
                </Button>
                <Popconfirm
                  title="确认删除"
                  description={`确定要删除选中的 ${selectedIds.length} 张图片吗？`}
                  onConfirm={handleBatchDelete}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true, disabled: selectedIds.length === 0 }}
                >
                  <Button
                    type="primary"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={selectedIds.length === 0}
                  >
                    删除选中
                  </Button>
                </Popconfirm>
              </div>
            </div>
          )}
        </div>
      )}

      <Modal
        title="修改空间"
        open={showEdit}
        onCancel={() => { setShowEdit(false); editForm.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowEdit(false); editForm.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => editForm.submit()} loading={updateLoading}>
              保存
            </Button>
          </div>
        }
        closable={false}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="空间名称"
            rules={[
              { required: true, message: '请输入空间名称' },
              { max: 20, message: '空间名称不超过 20 个字符' },
            ]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="introduction"
            label="空间介绍"
          >
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>

      {showUpgrade && (
        <UpgradeModal
          open={showUpgrade}
          onClose={() => setShowUpgrade(false)}
          onConfirm={() => {
            modal.info({
              title: '升级会员',
              content: '请联系管理员开通 VIP/SVIP 会员',
              okText: '知道了',
            })
            setShowUpgrade(false)
          }}
        />
      )}

      <Modal
        className="edit-picture-modal"
        title={null}
        open={showEditPicture}
        onCancel={() => { setShowEditPicture(false); editPictureForm.resetFields() }}
        width="80vw"
        style={{ maxHeight: '75vh' }}
        footer={null}
        closable={false}
      >
        <div className="edit-picture-layout">
          <div className="edit-picture-left">
            {(() => {
              const first = pictures.find(p => selectedIds.includes(p.id))
              return first ? <img src={first.url} alt="编辑中的图片" className="edit-picture-img" /> : null
            })()}
          </div>
          <div className="edit-picture-right">
            <div className="edit-picture-right-header">
              <span className="edit-picture-title">编辑图片信息</span>
            </div>
            <Form form={editPictureForm} layout="vertical" onFinish={handleEditPictureSubmit} className="edit-picture-form">
              <Form.Item name="pictureName" label="图片名称">
                <Input placeholder="留空则不修改" maxLength={50} allowClear />
              </Form.Item>
              <Form.Item name="introduction" label="图片介绍">
                <Input.TextArea placeholder="留空则不修改" maxLength={500} rows={3} allowClear />
              </Form.Item>
              <Form.Item name="tags" label="标签">
                <Select mode="multiple" placeholder="请选择标签" allowClear options={systemTags.map(t => ({ label: t, value: t }))} />
              </Form.Item>
            </Form>
            <div className="edit-picture-right-footer">
              <div>
                {(userInfo?.level === 1 || userInfo?.level === 2) && (
                  <Button onClick={handleAiTag}>AI一键填写</Button>
                )}
                <Button icon={<EditOutlined />} onClick={() => setShowImageEditor(true)}>编辑图片</Button>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                <Button onClick={() => { setShowEditPicture(false); editPictureForm.resetFields() }}>
                  取消
                </Button>
                <Button type="primary" onClick={() => editPictureForm.submit()} loading={editPictureLoading}>
                  保存
                </Button>
              </div>
            </div>
          </div>
        </div>
      </Modal>

      <ImageUploadModal
        open={showUploadModal}
        onClose={() => setShowUploadModal(false)}
        onSuccess={handleUploadSuccess}
        spaceId={spaces[0]?.id}
      />

      <ImageEditorModal
        open={showImageEditor}
        imageUrl={pictures.find(p => selectedIds.includes(p.id))?.url}
        spaceId={spaces[0]?.id}
        pictureId={selectedIds[0]}
        onSuccess={handleUploadSuccess}
        onClose={() => setShowImageEditor(false)}
      />

      <Modal
        title="分享图片"
        open={showShare}
        onCancel={() => { setShowShare(false); shareForm.resetFields(); setShareLink('') }}
        footer={null}
        width={420}
      >
        {!shareLink ? (
          <Form form={shareForm} layout="vertical" onFinish={handleCreateShare} initialValues={{ expireDays: 1, allowDownload: true }} style={{ marginTop: 16 }}>
            <Form.Item name="expireDays" label="有效期">
              <Select options={[
                { value: 1, label: '1 天' },
                { value: 3, label: '3 天' },
                { value: 7, label: '7 天' },
              ]} />
            </Form.Item>
            <Form.Item name="allowDownload" label="权限">
              <Select options={[
                { value: true, label: '允许下载' },
                { value: false, label: '仅预览' },
              ]} />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
              <Button onClick={() => { setShowShare(false); shareForm.resetFields() }} style={{ marginRight: 8 }}>取消</Button>
              <Button type="primary" htmlType="submit" loading={shareLoading}>生成链接</Button>
            </Form.Item>
          </Form>
        ) : (
          <div style={{ marginTop: 16 }}>
            <Input.TextArea value={shareLink} readOnly autoSize style={{ marginBottom: 12 }} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button onClick={() => { setShowShare(false); setShareLink('') }}>关闭</Button>
              <Button type="primary" onClick={handleCopyShareLink}>复制链接</Button>
            </div>
          </div>
        )}
      </Modal>
    </main>
  )
}

export default PrivateSpace