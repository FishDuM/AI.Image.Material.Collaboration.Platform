import { useState, useEffect, useCallback, useMemo, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Typography, Button, Modal, Form, Input, Masonry, Image as AntImage, Spin, Empty, Popconfirm } from 'antd'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, ArrowUpOutlined, EditOutlined, CloudUploadOutlined, ShareAltOutlined, StarOutlined } from '@ant-design/icons'
import { updateSpace, listSpace, createShare, updatePicture } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { useSystemTypes } from '../hooks/useRequestUtils'
import { AuthContext } from '../context/AuthContext'
import { PAGE_SIZE, formatStorage } from '../utils/constants'
import { getThumbnailUrl } from '../utils/image'
import { spaceNameRules } from '../utils/formRules'
import { isCanceledError } from '../utils/error'
import { usePictureFetch, useBatchSelection, usePictureEditUpload } from '../hooks/useSpacePictures'
import ImageUploadModal from '../components/shared/ImageUploadModal'
import ImageEditorModal from '../components/shared/ImageEditorModal'
import PictureEditModal from '../components/shared/PictureEditModal'
import UpgradeModal from '../components/shared/UpgradeModal'
import SharePictureModal from '../components/shared/SharePictureModal'
import StorageUsagePopover from '../components/shared/StorageUsagePopover'
import './PrivateSpace.css'

const { Title } = Typography

function PrivateSpace() {
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { message, modal } = AntApp.useApp()
  const [systemTags, setSystemTags] = useState([])
  const { userInfo } = useContext(AuthContext)
  const { fetchSystemTypes } = useSystemTypes()
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
    fetchSystemTypes().then(result => {
      if (Array.isArray(result)) setSystemTags(result)
    }).catch((error) => { console.error('[fetchSystemTypes]', error) })
  }, [])

  const fetchSpaces = useCallback(async () => {
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
    } catch (error) {
      console.error('[fetchSpaces]', error)
      setSpaces([])
    }
  }, [])

  const {
    pictures, pictureLoading, searchKeyword, setSearchKeyword,
    hasMore, loadingMore, handleSearch, handleSearchReset, refreshPictures,
  } = usePictureFetch({ spaces, pageSize: PAGE_SIZE, refreshSpaces: fetchSpaces, message })

  const {
    batchMode, selectedIds, setSelectedIds, setBatchMode,
    toggleBatchMode, toggleSelect, handleBatchDelete,
  } = useBatchSelection({ pictures, spaces, searchKeyword, refreshPictures, refreshSpaces: fetchSpaces, message })

  const {
    showEditPicture, setShowEditPicture, editPictureLoading, editPictureForm,
    showUploadModal, setShowUploadModal, showImageEditor, setShowImageEditor,
    handleEditPictureOpen, handleUploadSuccess, handleEditPictureSubmit, handleAiTag,
  } = usePictureEditUpload({ selectedIds, pictures, spaces, searchKeyword, refreshPictures, refreshSpaces: fetchSpaces, message, modal, navigate, isMobile, Form })

  const masonryItems = useMemo(() => pictures.map(pic => ({ key: `pic-${pic.id}`, data: pic })), [pictures])

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
      if (isCanceledError(error)) return
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
            <StorageUsagePopover spaceInfo={spaceInfo} />
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
                      refreshPictures(spaces[0].id, 1, searchKeyword)
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
            rules={spaceNameRules}
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

      <PictureEditModal
        open={showEditPicture}
        form={editPictureForm}
        picture={pictures.find(p => selectedIds.includes(p.id))}
        tags={systemTags}
        loading={editPictureLoading}
        canUseAi={userInfo?.level === 1 || userInfo?.level === 2}
        onSubmit={handleEditPictureSubmit}
        onAiTag={handleAiTag}
        onEditImage={() => setShowImageEditor(true)}
        onCancel={() => { setShowEditPicture(false); editPictureForm.resetFields() }}
      />

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

      <SharePictureModal
        open={showShare}
        form={shareForm}
        loading={shareLoading}
        shareLink={shareLink}
        onCreate={handleCreateShare}
        onCopy={handleCopyShareLink}
        onClose={() => { setShowShare(false); shareForm.resetFields(); setShareLink('') }}
      />
    </main>
  )
}

export default PrivateSpace
