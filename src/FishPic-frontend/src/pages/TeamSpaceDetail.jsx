import { useState, useEffect, useCallback, useContext, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { App as AntApp, Typography, Button, Modal, Form, Input, Select, Pagination, Masonry, Image as AntImage, Spin, Empty, Popconfirm, Progress, Popover, Avatar, Tooltip, Tag } from 'antd'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, ArrowLeftOutlined, TeamOutlined, UserOutlined, EditOutlined, CloudUploadOutlined, ArrowUpOutlined } from '@ant-design/icons'
import { getSpace, updateSpace, spaceListPicture, deletePicture, updatePicture, getSystemTypes, getPictureEditMessage, submitAiTag } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { ThemeContext } from '../context/ThemeContext'
import { AuthContext } from '../context/AuthContext'
import { PAGINATION_LOCALE, PAGE_SIZE, LEVEL_MAP, storageStrokeColor, formatStorage } from '../utils/constants'
import ImageUploadModal from '../components/shared/ImageUploadModal'
import ImageEditorModal from '../components/shared/ImageEditorModal'
import UpgradeModal from '../components/shared/UpgradeModal'
import './TeamSpaceDetail.css'
import './PrivateSpace.css'

const { Title } = Typography

function TeamSpaceDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { message, modal } = AntApp.useApp()
  const [systemTags, setSystemTags] = useState([])
  const { isDarkMode } = useContext(ThemeContext)
  const { userInfo } = useContext(AuthContext)

  const [spaceInfo, setSpaceInfo] = useState(null)
  const [loading, setLoading] = useState(true)

  const [pictures, setPictures] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureTotal, setPictureTotal] = useState(0)
  const [pictureLoading, setPictureLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')

  const [batchMode, setBatchMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const [showEditPicture, setShowEditPicture] = useState(false)
  const [editPictureLoading, setEditPictureLoading] = useState(false)
  const [editPictureForm] = Form.useForm()
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [showImageEditor, setShowImageEditor] = useState(false)

  const [showEdit, setShowEdit] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)
  const [editForm] = Form.useForm()
  const [showUpgrade, setShowUpgrade] = useState(false)

  const fetchSpace = useCallback(async () => {
    setLoading(true)
    try {
      const result = await getSpace(id)
      if (result) {
        const sizeBytes = parseFloat(result.size) || 0
        const storageBytes = parseFloat(result.storageSize) || 0
        const percent = storageBytes > 0 ? Math.min(100, Math.round((sizeBytes / storageBytes) * 100)) : 0
        setSpaceInfo({
          ...result,
          percent,
          usedText: formatStorage(sizeBytes),
          totalText: formatStorage(storageBytes),
        })
      }
    } catch (error) {
      message.error(error.message || '加载空间信息失败')
      navigate('/team-space', { replace: true })
    } finally {
      setLoading(false)
    }
  }, [id, message, navigate])

  const fetchPictures = useCallback(async (spaceId, page, keyword) => {
    setPictureLoading(true)
    setPicturePage(page)
    try {
      const params = { spaceId, current: page, pageSize: PAGE_SIZE }
      if (keyword && keyword.trim()) params.keyword = keyword.trim()
      const result = await spaceListPicture(params)
      const list = Array.isArray(result?.records) ? result.records : []
      const total = typeof result?.total === 'number' ? result.total : list.length
      setPictures(list)
      setPictureTotal(total)
    } catch {
      setPictures([])
    } finally {
      setPictureLoading(false)
    }
  }, [])

  useEffect(() => {
    getSystemTypes().then(result => {
      if (Array.isArray(result)) setSystemTags(result)
    }).catch(() => {})
  }, [])

  useEffect(() => {
    fetchSpace()
  }, [fetchSpace])

  useEffect(() => {
    if (spaceInfo?.id) {
      fetchPictures(spaceInfo.id, 1)
    }
  }, [spaceInfo?.id, fetchPictures])

  const handlePageChange = useCallback((page) => {
    setSelectedIds([])
    setBatchMode(false)
    if (spaceInfo?.id) fetchPictures(spaceInfo.id, page, searchKeyword)
  }, [spaceInfo?.id, fetchPictures, searchKeyword])

  const handleSearch = useCallback(() => {
    if (spaceInfo?.id) fetchPictures(spaceInfo.id, 1, searchKeyword)
  }, [spaceInfo?.id, fetchPictures, searchKeyword])

  const handleSearchReset = useCallback(() => {
    setSearchKeyword('')
    if (spaceInfo?.id) fetchPictures(spaceInfo.id, 1, '')
  }, [spaceInfo?.id, fetchPictures])

  const toggleBatchMode = useCallback(() => {
    setBatchMode((prev) => {
      if (prev) setSelectedIds([])
      return !prev
    })
  }, [])

  const toggleSelect = useCallback((pictureId) => {
    setSelectedIds((prev) =>
      prev.includes(pictureId) ? prev.filter((pid) => pid !== pictureId) : [...prev, pictureId]
    )
  }, [])

  const handleBatchDelete = useCallback(async () => {
    if (selectedIds.length === 0) {
      message.warning('请先选择要删除的图片')
      return
    }
    try {
      const res = await deletePicture(selectedIds)
      message.success(res?.message || '删除成功')
      setSelectedIds([])
      setBatchMode(false)
      if (spaceInfo?.id) fetchPictures(spaceInfo.id, picturePage, searchKeyword)
      fetchSpace()
    } catch (error) {
      message.error(error.message || '批量删除失败')
    }
  }, [selectedIds, spaceInfo?.id, fetchPictures, picturePage, searchKeyword, fetchSpace, message])

  const handleEditPictureOpen = () => {
    if (selectedIds.length === 0) {
      message.warning('请先选择图片')
      return
    }
    if (selectedIds.length > 1) {
      message.warning('一次只能编辑一张图片')
      return
    }
    if (isMobile) {
      const pic = pictures.find(p => p.id === selectedIds[0])
      navigate('/mobile/picture/edit', {
        state: {
          pictureId: selectedIds[0],
          pictureUrl: pic?.url,
          pictureName: pic?.pictureName,
          introduction: pic?.introduction,
        }
      })
      return
    }
    editPictureForm.resetFields()
    setShowEditPicture(true)
    getPictureEditMessage(selectedIds[0]).then(result => {
      if (result) {
        editPictureForm.setFieldsValue({
          pictureName: result.pictureName || '',
          introduction: result.introduction || '',
          tags: Array.isArray(result.tags) ? result.tags : [],
        })
      }
    }).catch(() => {})
  }

  const handleUploadSuccess = useCallback(() => {
    setShowUploadModal(false)
    if (spaceInfo?.id) {
      fetchPictures(spaceInfo.id, 1, searchKeyword)
      fetchSpace()
    }
  }, [spaceInfo?.id, fetchPictures, searchKeyword, fetchSpace])

  const handleEditPictureSubmit = async (values) => {
    setEditPictureLoading(true)
    try {
      await updatePicture({
        id: selectedIds[0],
        pictureName: values.pictureName || undefined,
        introduction: values.introduction || undefined,
        tags: values.tags || undefined,
      })
      message.success('编辑成功')
      setShowEditPicture(false)
      editPictureForm.resetFields()
      setSelectedIds([])
      setBatchMode(false)
      if (spaceInfo?.id) fetchPictures(spaceInfo.id, picturePage, searchKeyword)
      fetchSpace()
    } catch (error) {
      message.error(error.message || '编辑失败')
    } finally {
      setEditPictureLoading(false)
    }
  }

  const handleEditOpen = () => {
    if (spaceInfo) {
      editForm.setFieldsValue({ name: spaceInfo.name, introduction: spaceInfo.introduction || '' })
      setShowEdit(true)
    }
  }

  const handleUpdate = async (values) => {
    setUpdateLoading(true)
    try {
      await updateSpace({ id: spaceInfo.id, name: values.name, introduction: values.introduction || '' })
      message.success('修改成功')
      setShowEdit(false)
      editForm.resetFields()
      fetchSpace()
    } catch (error) {
      message.error(error.message || '修改失败')
    } finally {
      setUpdateLoading(false)
    }
  }

  const masonryItems = useMemo(() => pictures.map((pic) => ({ key: `pic-${pic.id}`, data: pic })), [pictures])
  const levelInfo = LEVEL_MAP[spaceInfo?.level] || LEVEL_MAP[0]
  const members = spaceInfo?.teamMembers || []

  if (loading) {
    return (
      <main className="tsd-container">
        <div className="tsd-loading"><Spin /></div>
      </main>
    )
  }

  if (!spaceInfo) return null

  return (
    <main className="tsd-container">
      <div className="tsd-header">
        <div className="tsd-header-left">
          <div className="tsd-back-btn-wrapper">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/team-space')} className="tsd-back-btn">
              返回空间列表
            </Button>
          </div>
          <div className="tsd-content-area">
            <div className="tsd-title-row">
              <Title level={2} className="tsd-title">{spaceInfo.name}</Title>
              <Tag color={levelInfo.color} variant="filled">{levelInfo.label}</Tag>
            </div>
            <p className="tsd-subtitle">
              {spaceInfo.introduction || '团队协作图片空间'}
            </p>
            {members.length > 0 && (
              <div className="tsd-members-row">
                <TeamOutlined className="tsd-members-icon" />
                <Avatar.Group
                  max={{ count: 10, style: { backgroundColor: isDarkMode ? '#434343' : '#f0f0f0', color: isDarkMode ? 'rgba(255,255,255,0.65)' : '#999' } }}
                  size={28}
                >
                  {members.map((m) => (
                    <Tooltip title={m.nickname || '成员'} key={m.id}>
                      <Avatar size={28} src={m.avatar} icon={<UserOutlined />} />
                    </Tooltip>
                  ))}
                </Avatar.Group>
                <span className="tsd-members-count">{members.length} 位成员</span>
              </div>
            )}
          </div>
        </div>
        <div className="tsd-header-right">
          <Popover
            content={
              <div className={`storage-card ${levelInfo.cardClass}`}>
                <div className="storage-card-title">空间详情</div>
                <div className="storage-card-row">
                  <span className="storage-card-label">空间等级</span>
                  <span className={`storage-card-value ${levelInfo.className}`}>{levelInfo.label}</span>
                </div>
                <div className="storage-card-row">
                  <span className="storage-card-label">占用比例</span>
                  <span className="storage-card-value">{spaceInfo.percent}%</span>
                </div>
                <div className="storage-card-row">
                  <span className="storage-card-label">已占用空间</span>
                  <span className="storage-card-value">{spaceInfo.usedText}</span>
                </div>
                <div className="storage-card-row">
                  <span className="storage-card-label">总空间</span>
                  <span className="storage-card-value">{spaceInfo.totalText}</span>
                </div>
                <div className="storage-card-row">
                  <span className="storage-card-label">图片数量</span>
                  <span className="storage-card-value">{spaceInfo.pictureCount ?? 0}</span>
                </div>
                <div className="storage-card-row">
                  <span className="storage-card-label">创建人</span>
                  <span className="storage-card-value">{spaceInfo.userName || '未知'}</span>
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
                  <span className={`level-text ${levelInfo.className}`}>{levelInfo.label}</span>
                  <span className="level-percent">{spaceInfo.percent}%</span>
                </div>
              )}
            />
          </Popover>
          <Button onClick={handleEditOpen}>修改空间</Button>
          <Button icon={<ArrowUpOutlined />} className="tsd-upgrade-btn" onClick={() => isMobile ? navigate('/mobile/upgrade') : setShowUpgrade(true)}>
            升级空间
          </Button>
        </div>
      </div>

      <div className="tsd-search-bar">
        <Input
          className="tsd-search-input"
          placeholder="搜索图片..."
          prefix={<SearchOutlined />}
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          onPressEnter={handleSearch}
          allowClear
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
        <Button icon={<ReloadOutlined />} onClick={handleSearchReset}>重置</Button>
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

      <div className="tsd-masonry-section">
        {pictureLoading && (
          <div className="tsd-loading"><Spin /></div>
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
                  className={`tsd-masonry-item ${batchMode ? 'batch-mode' : ''}`}
                  onClick={batchMode ? () => toggleSelect(item.data.id) : undefined}
                >
                  <AntImage src={item.data.url} alt={item.data.pictureName || '图片'} preview={!batchMode} className="tsd-masonry-image" />
                  {batchMode && (
                    <div className="tsd-masonry-select">
                      <div className={`tsd-masonry-checkbox ${isSelected ? 'checked' : ''}`}>
                        {isSelected && <CheckOutlined />}
                      </div>
                    </div>
                  )}
                </div>
              )
            }}
          />
        )}
        {batchMode && (
          <div className="tsd-batch-bar">
            <span className="tsd-batch-count">
              已选择 <strong>{selectedIds.length}</strong> 张图片
            </span>
            <div className="tsd-batch-actions">
              <Button icon={<CloseOutlined />} onClick={toggleBatchMode}>取消</Button>
              <Button
                icon={<EditOutlined />}
                onClick={handleEditPictureOpen}
                disabled={selectedIds.length === 0}
              >
                编辑图片信息
              </Button>
              <Popconfirm
                title="确认删除"
                description={`确定要删除选中的 ${selectedIds.length} 张图片吗？`}
                onConfirm={handleBatchDelete}
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true, disabled: selectedIds.length === 0 }}
              >
                <Button type="primary" danger icon={<DeleteOutlined />} disabled={selectedIds.length === 0}>
                  删除选中
                </Button>
              </Popconfirm>
            </div>
          </div>
        )}
        {pictureTotal > PAGE_SIZE && (
          <div className="tsd-pagination">
            <Pagination
              current={picturePage}
              total={pictureTotal}
              pageSize={PAGE_SIZE}
              onChange={handlePageChange}
              showSizeChanger={false}
              showQuickJumper
              locale={PAGINATION_LOCALE}
            />
          </div>
        )}
      </div>

      <Modal
        title="修改空间"
        open={showEdit}
        onCancel={() => { setShowEdit(false); editForm.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowEdit(false); editForm.resetFields() }} style={{ marginRight: 8 }}>取消</Button>
            <Button type="primary" onClick={() => editForm.submit()} loading={updateLoading}>保存</Button>
          </div>
        }
        closable={false}
      >
        <Form form={editForm} layout="vertical" onFinish={handleUpdate} style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="空间名称"
            rules={[{ required: true, message: '请输入空间名称' }, { max: 20, message: '空间名称不超过 20 个字符' }]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item name="introduction" label="空间介绍">
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
                  <Button onClick={async () => {
                    try {
                      await submitAiTag(selectedIds[0])
                      setShowEditPicture(false)
                      modal.info({
                        title: 'AI正在执行',
                        content: 'AI正在后台识别图片信息，完成后将自动填充，请稍后重新打开编辑查看',
                        okText: '知道了',
                      })
                    } catch (e) {
                      message.error(e.message || 'AI识别提交失败')
                    }
                  }}>AI一键填写</Button>
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
        spaceId={Number(id)}
      />

      <ImageEditorModal
        open={showImageEditor}
        imageUrl={pictures.find(p => selectedIds.includes(p.id))?.url}
        spaceId={Number(id)}
        onSuccess={handleUploadSuccess}
        onClose={() => setShowImageEditor(false)}
      />
    </main>
  )
}

export default TeamSpaceDetail
