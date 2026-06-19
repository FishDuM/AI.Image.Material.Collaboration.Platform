import { useState, useEffect, useCallback, useMemo, useContext, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Typography, Button, Form, Input, Masonry, Image as AntImage, Spin, Empty } from 'antd'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, ArrowUpOutlined, EditOutlined, CloudUploadOutlined, ShareAltOutlined, StarOutlined } from '@ant-design/icons'
import { updateSpace, listSpace, updatePicture } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { useSystemTypes } from '../hooks/useSystemTypes'
import { useMasonryItems } from '../hooks/useMasonryItems'
import { useShare } from '../hooks/useShare'
import { AuthContext } from '../context/AuthContext'
import { PAGE_SIZE, formatStorage, computeSpaceStorage, isVipUser, showUpgradeHint } from '../utils/constants'
import { getThumbnailUrl } from '../utils/image'
import { isCanceledError } from '../utils/error'
import { usePictureFetch, useBatchSelection, usePictureEditUpload } from '../hooks/useSpacePictures'
import ImageUploadModal from '../components/shared/ImageUploadModal'
import ImageEditorModal from '../components/shared/ImageEditorModal'
import PictureEditModal from '../components/shared/PictureEditModal'
import UpgradeModal from '../components/shared/UpgradeModal'
import SharePictureModal from '../components/shared/SharePictureModal'
import StorageUsagePopover from '../components/shared/StorageUsagePopover'
import EditSpaceModal from '../components/shared/EditSpaceModal'
import BatchActionBar from '../components/shared/BatchActionBar'
import './PrivateSpace.css'

const { Title } = Typography

function PrivateSpace() {
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { message, modal } = AntApp.useApp()
  const systemTags = useSystemTypes()
  const { userInfo } = useContext(AuthContext)
  const [spaces, setSpaces] = useState([])
  const [showEdit, setShowEdit] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)

  const [showUpgrade, setShowUpgrade] = useState(false)

  const fetchSpaces = useCallback(async () => {
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
    } catch {
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

  const picturesRef = useRef(pictures)
  useEffect(() => { picturesRef.current = pictures })
  const userInfoRef = useRef(userInfo)
  useEffect(() => { userInfoRef.current = userInfo })

  const {
    showShare, shareForm, shareLoading, shareLink,
    handleOpenShare, handleCreateShare, handleCopyShareLink, handleCloseShare,
  } = useShare({
    selectedIds,
    message,
    onValidate: () => {
      const targetPics = picturesRef.current.filter(p => selectedIds.includes(p.id))
      const hasForeignPic = targetPics.some(p => p.userId && userInfoRef.current && p.userId !== userInfoRef.current.id)
      if (hasForeignPic) {
        message.warning('只能分享自己上传的图片')
        return false
      }
    },
  })

  const {
    showEditPicture, setShowEditPicture, editPictureLoading, editPictureForm,
    showUploadModal, setShowUploadModal, showImageEditor, setShowImageEditor,
    handleEditPictureOpen, handleUploadSuccess, handleEditPictureSubmit, handleAiTag,
  } = usePictureEditUpload({ selectedIds, pictures, spaces, searchKeyword, refreshPictures, refreshSpaces: fetchSpaces, message, modal, navigate, isMobile, Form })

  const masonryItems = useMasonryItems(pictures)

  useEffect(() => {
    fetchSpaces()
  }, [fetchSpaces])

  const spaceInfo = useMemo(() => {
    if (!spaces.length) return null
    const s = spaces[0]
    const { sizeBytes, storageBytes, percent } = computeSpaceStorage(s)
    return { ...s, percent, usedText: formatStorage(sizeBytes), totalText: formatStorage(storageBytes) }
  }, [spaces])

  const handleEditOpen = () => {
    if (spaces.length > 0) {
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
            <BatchActionBar
              className="private-space-batch-bar"
              countClassName="private-space-batch-count"
              actionsClassName="private-space-batch-actions"
              selectedCount={selectedIds.length}
              cancelIcon={<CloseOutlined />}
              onCancel={toggleBatchMode}
              actions={[
                { icon: <EditOutlined />, label: '编辑图片信息', onClick: handleEditPictureOpen, disabled: selectedIds.length === 0 },
                { icon: <ShareAltOutlined />, label: '分享', onClick: handleOpenShare, disabled: selectedIds.length === 0 },
                {
                  icon: <StarOutlined />,
                  label: '申请精选',
                  onClick: async () => {
                    try {
                      await updatePicture({ id: selectedIds[0], isSelected: 1 })
                      message.success('已提交精选申请')
                      setSelectedIds([])
                      setBatchMode(false)
                      refreshPictures(spaces[0].id, 1, searchKeyword)
                    } catch (err) {
                      message.error(err.message || '申请失败')
                    }
                  },
                  disabled: selectedIds.length !== 1,
                  style: { color: '#d4a017', borderColor: '#d4a017' },
                },
              ]}
              deleteAction={{ onClick: handleBatchDelete, disabled: selectedIds.length === 0 }}
            />
          )}
        </div>
      )}

      <EditSpaceModal
        open={showEdit}
        loading={updateLoading}
        initialValues={spaces.length > 0 ? { name: spaces[0].name, introduction: spaces[0].introduction } : undefined}
        onSubmit={handleUpdate}
        onCancel={() => setShowEdit(false)}
      />

      {showUpgrade && (
        <UpgradeModal
          open={showUpgrade}
          onClose={() => setShowUpgrade(false)}
          onConfirm={() => {
            showUpgradeHint(modal)
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
        canUseAi={isVipUser(userInfo?.level)}
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
        onClose={handleCloseShare}
      />
    </main>
  )
}

export default PrivateSpace
