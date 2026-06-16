import { useState, useEffect, useCallback, useContext, useMemo, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { App as AntApp, Typography, Button, Modal, Form, Input, Select, Pagination, Masonry, Image as AntImage, Spin, Empty, Popconfirm, Progress, Popover, Avatar, Tooltip, Tag, List, Alert } from 'antd'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, ArrowLeftOutlined, TeamOutlined, UserOutlined, EditOutlined, CloudUploadOutlined, ArrowUpOutlined, UserAddOutlined, SettingOutlined, ShareAltOutlined, SwapOutlined, StarOutlined } from '@ant-design/icons'
import { getSpace, updateSpace, spaceListPicture, deletePicture, updatePicture, getPictureEditMessage, submitAiTag, searchUsers, getTeamMembers, teamInvite, teamRemove, teamChangeRole, createShare } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { useSystemTypes } from '../hooks/useRequestUtils'
import { ThemeContext } from '../context/ThemeContext'
import { AuthContext } from '../context/AuthContext'
import { PAGINATION_LOCALE, PAGE_SIZE, LEVEL_MAP, DEFAULT_LEVEL, storageStrokeColor, formatStorage } from '../utils/constants'
import { getThumbnailUrl } from '../utils/image'
import ImageUploadModal from '../components/shared/ImageUploadModal'
import ImageEditorModal from '../components/shared/ImageEditorModal'
import PictureEditModal from '../components/shared/PictureEditModal'
import CollaborativeCanvas from '../components/shared/CollaborativeCanvas'
import UpgradeModal from '../components/shared/UpgradeModal'
import './TeamSpaceDetail.css'
import './PrivateSpace.css'

const { Title } = Typography

const TEAM_ROLES = [
  { value: 1, label: '所有者' },
  { value: 2, label: '成员' },
]
const ROLE_MAP = { 1: '所有者', 2: '成员' }

function TeamSpaceDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { message, modal } = AntApp.useApp()
  const [systemTags, setSystemTags] = useState([])
  const { fetchSystemTypes } = useSystemTypes()
  const { isDarkMode } = useContext(ThemeContext)
  const { userInfo } = useContext(AuthContext)

  const [spaceInfo, setSpaceInfo] = useState(null)
  const [loading, setLoading] = useState(true)

  const [pictures, setPictures] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureTotal, setPictureTotal] = useState(0)
  const [pictureLoading, setPictureLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')
  const activeSearchRef = useRef('')

  const [batchMode, setBatchMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const [showEditPicture, setShowEditPicture] = useState(false)
  const [editPictureLoading, setEditPictureLoading] = useState(false)
  const [editPictureForm] = Form.useForm()
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [showImageEditor, setShowImageEditor] = useState(false)
  const [showCollabCanvas, setShowCollabCanvas] = useState(false)

  const [showEdit, setShowEdit] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)
  const [editForm] = Form.useForm()
  const [showUpgrade, setShowUpgrade] = useState(false)
  const [showShare, setShowShare] = useState(false)
  const [shareForm] = Form.useForm()
  const [shareLoading, setShareLoading] = useState(false)
  const [shareLink, setShareLink] = useState('')

  const [showTeamManage, setShowTeamManage] = useState(false)
  const [teamMembers, setTeamMembers] = useState([])
  const [teamLoading, setTeamLoading] = useState(false)
  const [inviteModalOpen, setInviteModalOpen] = useState(false)
  const [searchResults, setSearchResults] = useState([])
  const [searching, setSearching] = useState(false)
  const [inviteForm] = Form.useForm()
  const [inviting, setInviting] = useState(false)
  const searchTimerRef = useRef(null)

  // 切换 space 时旧请求完成后 setState 可能把 A space 数据塞到 B space
  const currentSpaceIdRef = useRef(null)
  useEffect(() => {
    currentSpaceIdRef.current = id
  }, [id])

  const fetchSpace = useCallback(async () => {
    setLoading(true)
    try {
      const result = await getSpace(id)
      if (currentSpaceIdRef.current !== id) return
      if (result) {
        const sizeBytes = Number(result.size) || 0
        const storageBytes = Number(result.storageSize) || 0
        const percent = storageBytes > 0 ? Math.min(100, Math.round((sizeBytes / storageBytes) * 100)) : 0
        setSpaceInfo({
          ...result,
          percent,
          usedText: formatStorage(sizeBytes),
          totalText: formatStorage(storageBytes),
        })
      }
    } catch (error) {
      if (currentSpaceIdRef.current !== id) return
      message.error(error.message || '加载空间信息失败')
      navigate('/team-space', { replace: true })
    } finally {
      if (currentSpaceIdRef.current === id) setLoading(false)
    }
  }, [id, message, navigate])

  const fetchPictures = useCallback(async (spaceId, page, keyword) => {
    setPictureLoading(true)
    setPicturePage(page)
    try {
      const params = { spaceId, current: page, pageSize: PAGE_SIZE }
      if (keyword && keyword.trim()) params.keyword = keyword.trim()
      const result = await spaceListPicture(params)
      if (currentSpaceIdRef.current !== id) return
      const list = Array.isArray(result?.records) ? result.records : []
      const total = typeof result?.total === 'number' ? result.total : list.length
      setPictures(list)
      setPictureTotal(total)
    } catch {
      if (currentSpaceIdRef.current !== id) return
      setPictures([])
    } finally {
      if (currentSpaceIdRef.current === id) setPictureLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchSystemTypes().then(result => {
      if (Array.isArray(result)) setSystemTags(result)
    }).catch(() => {})
  }, [])

  // 搜索防抖定时器清理
  useEffect(() => {
    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
    }
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
    if (spaceInfo?.id) fetchPictures(spaceInfo.id, page, activeSearchRef.current)
  }, [spaceInfo?.id, fetchPictures])

  const handleSearch = useCallback(() => {
    activeSearchRef.current = searchKeyword
    if (spaceInfo?.id) fetchPictures(spaceInfo.id, 1, searchKeyword)
  }, [spaceInfo?.id, fetchPictures, searchKeyword])

  const handleSearchReset = useCallback(() => {
    setSearchKeyword('')
    activeSearchRef.current = ''
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
      if (spaceInfo?.id) fetchPictures(spaceInfo.id, 1, searchKeyword)
      fetchSpace()
    } catch (error) {
      message.error(error.message || '批量删除失败')
    }
  }, [selectedIds, spaceInfo?.id, fetchPictures, searchKeyword, fetchSpace, message])

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
    }).catch((error) => {
      message.error(error.message || '加载图片信息失败')
    })
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

  const handleOpenShare = () => {
    if (selectedIds.length === 0) {
      message.warning('请选择至少一张图片进行分享')
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

  const fetchTeamMembers = useCallback(async () => {
    if (!spaceInfo?.id) return
    setTeamLoading(true)
    try {
      const result = await getTeamMembers(spaceInfo.id)
      setTeamMembers(Array.isArray(result) ? result : [])
    } catch (e) {
      message.error(e.message || '获取成员列表失败')
    } finally {
      setTeamLoading(false)
    }
  }, [spaceInfo?.id, message])

  const handleOpenTeamManage = () => {
    setShowTeamManage(true)
    fetchTeamMembers()
  }

  const handleSearchUser = useCallback((keyword) => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
    if (!keyword || keyword.trim().length < 1) {
      setSearchResults([])
      return
    }
    searchTimerRef.current = setTimeout(async () => {
      setSearching(true)
      try {
        const result = await searchUsers(keyword.trim())
        setSearchResults(Array.isArray(result) ? result : [])
      } catch {
        setSearchResults([])
      } finally {
        setSearching(false)
      }
    }, 300)
  }, [])

  const handleInvite = async (values) => {
    setInviting(true)
    try {
      await teamInvite({ spaceId: spaceInfo.id, userId: values.userId, roleId: values.roleId })
      message.success('邀请成功')
      setInviteModalOpen(false)
      inviteForm.resetFields()
      setSearchResults([])
      fetchTeamMembers()
      fetchSpace()
    } catch (e) {
      message.error(e.message || '邀请失败')
    } finally {
      setInviting(false)
    }
  }

  const handleRemove = async (userId) => {
    try {
      await teamRemove({ spaceId: spaceInfo.id, userId })
      message.success('移除成功')
      fetchTeamMembers()
      fetchSpace()
    } catch (e) {
      message.error(e.message || '移除失败')
    }
  }

  const handleChangeRole = async (userId, roleId) => {
    try {
      await teamChangeRole({ spaceId: spaceInfo.id, userId, roleId })
      message.success('角色变更成功')
      fetchTeamMembers()
    } catch (e) {
      message.error(e.message || '角色变更失败')
    }
  }

  const isCreator = spaceInfo?.userId === userInfo?.id
  const hasMemberManagePerm = isCreator || userInfo?.permissions?.includes('system:team:manage')

  const masonryItems = useMemo(() => pictures.map((pic) => ({ key: `pic-${pic.id}`, data: pic })), [pictures])
  const levelInfo = LEVEL_MAP[spaceInfo?.level] || DEFAULT_LEVEL
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
            {spaceInfo.status === 0 && (
              <Alert
                message="该空间已被管理员禁用"
                description="禁用期间无法上传新图片或进行其他操作"
                type="warning"
                showIcon
                banner
                style={{ marginBottom: 16 }}
              />
            )}
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
          {hasMemberManagePerm && (
            <Button icon={<SettingOutlined />} onClick={handleOpenTeamManage}>管理成员</Button>
          )}
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
                  <AntImage src={getThumbnailUrl(item.data.url, 400)} alt={item.data.pictureName || '图片'} preview={!batchMode ? { src: item.data.url } : false} className="tsd-masonry-image" />
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
                    message.success('精选申请已提交')
                    setSelectedIds([])
                    setBatchMode(false)
                    fetchPictures(spaceInfo.id, 1, searchKeyword)
                  } catch (err) {
                    message.error(err.message || '申请失败')
                  }
                }}
                disabled={selectedIds.length !== 1}
                style={{ color: '#d4a017', borderColor: '#d4a017' }}
              >
                申请精选
              </Button>
              <Button
                icon={<SwapOutlined />}
                onClick={() => setShowCollabCanvas(true)}
                disabled={selectedIds.length !== 1}
                type="primary"
              >
                协同编辑
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

      <PictureEditModal
        open={showEditPicture}
        form={editPictureForm}
        picture={pictures.find(p => selectedIds.includes(p.id))}
        tags={systemTags}
        loading={editPictureLoading}
        canUseAi={userInfo?.level === 1 || userInfo?.level === 2}
        onSubmit={handleEditPictureSubmit}
        onAiTag={async () => {
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
        }}
        onEditImage={() => setShowImageEditor(true)}
        onCancel={() => { setShowEditPicture(false); editPictureForm.resetFields() }}
      />

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
        pictureId={selectedIds[0]}
        onSuccess={handleUploadSuccess}
        onClose={() => setShowImageEditor(false)}
      />

      {showCollabCanvas && selectedIds.length === 1 && (() => {
        const selectedPic = pictures.find(p => selectedIds.includes(p.id))
        return (
          <CollaborativeCanvas
            open={showCollabCanvas}
            imageUrl={selectedPic?.url}
            pictureId={selectedIds[0]}
            spaceId={Number(id)}
            updatedAt={selectedPic?.updateTime}
            onSuccess={handleUploadSuccess}
            onClose={() => setShowCollabCanvas(false)}
          />
        )
      })()}

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

      <Modal
        title="团队成员管理"
        open={showTeamManage}
        onCancel={() => setShowTeamManage(false)}
        footer={null}
        width={520}
      >
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="primary" icon={<UserAddOutlined />} onClick={() => setInviteModalOpen(true)}>
            邀请成员
          </Button>
        </div>
        <List
          loading={teamLoading}
          dataSource={teamMembers}
          locale={{ emptyText: '暂无成员' }}
          renderItem={(item) => (
            <List.Item
              actions={
                hasMemberManagePerm && item.id !== spaceInfo?.userId
                  ? [
                      <Select
                        key="role"
                        size="small"
                        value={item.roleId}
                        onChange={(val) => handleChangeRole(item.id, val)}
                        options={TEAM_ROLES}
                        style={{ width: 120 }}
                      />,
                      <Popconfirm
                        key="remove"
                        title="确认移除"
                        description={`确定要移除 ${item.nickname} 吗？`}
                        onConfirm={() => handleRemove(item.id)}
                        okText="移除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                      >
                        <Button size="small" danger>移除</Button>
                      </Popconfirm>,
                    ]
                  : item.id === spaceInfo?.userId
                    ? [<Tag key="tag" color="blue">创建者</Tag>]
                    : []
              }
            >
              <List.Item.Meta
                avatar={<Avatar src={item.avatar} icon={<UserOutlined />} />}
                title={item.nickname}
                description={ROLE_MAP[item.roleId] || '未知角色'}
              />
            </List.Item>
          )}
        />

        <Modal
          title="邀请成员"
          open={inviteModalOpen}
          onCancel={() => { setInviteModalOpen(false); inviteForm.resetFields(); setSearchResults([]) }}
          footer={null}
          width={420}
          destroyOnClose
        >
          <Form form={inviteForm} layout="vertical" onFinish={handleInvite} style={{ marginTop: 16 }}>
            <Form.Item name="userId" label="选择用户" rules={[{ required: true, message: '请选择要邀请的用户' }]}>
              <Select
                showSearch
                filterOption={false}
                onSearch={handleSearchUser}
                loading={searching}
                placeholder="输入用户名或昵称搜索"
                notFoundContent={searching ? '搜索中...' : '无匹配结果'}
                options={searchResults.map(u => ({
                  value: u.id,
                  label: (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Avatar size={20} src={u.avatar} icon={<UserOutlined />} />
                      <span>{u.nickname}</span>
                    </div>
                  ),
                }))}
              />
            </Form.Item>
            <Form.Item name="roleId" label="分配角色" rules={[{ required: true, message: '请选择角色' }]}>
              <Select placeholder="请选择角色" options={TEAM_ROLES} />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
              <Button onClick={() => { setInviteModalOpen(false); inviteForm.resetFields(); setSearchResults([]) }} style={{ marginRight: 8 }}>取消</Button>
              <Button type="primary" htmlType="submit" loading={inviting}>邀请</Button>
            </Form.Item>
          </Form>
        </Modal>
      </Modal>
    </main>
  )
}

export default TeamSpaceDetail
