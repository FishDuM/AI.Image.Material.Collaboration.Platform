import {useCallback, useContext, useEffect, useRef, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {
  App as AntApp,
  Avatar,
  Button,
  Empty,
  Form,
  Input,
  Masonry,
  Modal,
  Skeleton,
  Spin,
  Switch,
  Tooltip,
  Upload
} from 'antd'
import {
  CalendarOutlined,
  EditOutlined,
  EyeOutlined,
  FileTextOutlined,
  HeartOutlined,
  LoadingOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  PlusOutlined,
  StarOutlined,
  UserOutlined
} from '@ant-design/icons'
import {editUser, getMyCollects, getMyLikes, getMyPosts, getPost, getUser, getUserMyself, uploadAvatar} from '../api'
import {AuthContext} from '../context/AuthContext'
import { useIsMobile } from '../hooks/useIsMobile'
import { useFetchWithCleanup } from '../hooks/useRequestUtils'
import { isAllowedImageFile, getMaxUploadSize, formatMaxUploadSize } from '../utils/uploadConstraints'
import PostDetailModal from '../components/PostDetailModal'
import CreateEditPostModal from '../components/CreateEditPostModal'
import PostCard from '../components/shared/PostCard'
import './UserProfile.css'

function UserProfile() {
  const { message } = AntApp.useApp()
  const { userInfo, login: authLogin, isAuthenticated } = useContext(AuthContext)
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const [loading, setLoading] = useState(true)
  const [userData, setUserData] = useState(null)
  const [activeTab, setActiveTab] = useState('notes')
  const [avatarVisible, setAvatarVisible] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editForm] = Form.useForm()
  const [, setUploadedAvatarUrl] = useState(null)
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState(null)
  const [showPasswordSection, setShowPasswordSection] = useState(false)
  const hasFetchedRef = useRef(false)

  const [tabState, setTabState] = useState({
    notes: { items: [], hasMore: true, loaded: false, loading: false },
    favorites: { items: [], hasMore: true, loaded: false, loading: false },
    likes: { items: [], hasMore: true, loaded: false, loading: false },
  })
  const [counts, setCounts] = useState({ notes: 0, favorites: 0, likes: 0 })
  const pageRefs = useRef({ notes: 1, favorites: 1, likes: 1 })
  const loadingMoreRef = useRef(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [postDetailModalOpen, setPostDetailModalOpen] = useState(false)
  const [postDetail, setPostDetail] = useState(null)
  const [postDetailLoading, setPostDetailLoading] = useState(false)
  const [detailImageIndex, setDetailImageIndex] = useState(0)
  const [createEditModalOpen, setCreateEditModalOpen] = useState(false)
  const [editingPostDetail, setEditingPostDetail] = useState(null)

  const { createSignal } = useFetchWithCleanup()

  const apiMap = { notes: getMyPosts, favorites: getMyCollects, likes: getMyLikes }

  const fetchTabData = useCallback(async (tabKey, page = 1, append = false, signal) => {
    const fetchFn = apiMap[tabKey]
    if (!fetchFn) return

    if (append) {
      loadingMoreRef.current = true
      setLoadingMore(true)
    } else {
      setTabState(prev => ({
        ...prev,
        [tabKey]: { ...prev[tabKey], loading: true },
      }))
    }

    try {
      const result = await fetchFn({ current: page, pageSize: 20 }, signal ? { signal } : {})
      const records = result.records || []
      const totalPages = result.pages || 0
      const hasMore = page < totalPages

      if (append) {
        setTabState(prev => {
          const existing = prev[tabKey]
          const existingIds = new Set(existing.items.map(p => p.id))
          const newRecords = records.filter(p => !existingIds.has(p.id))
          return {
            ...prev,
            [tabKey]: {
              ...existing,
              items: [...existing.items, ...newRecords],
              hasMore,
              loading: false,
            },
          }
        })
      } else {
        setTabState(prev => ({
          ...prev,
          [tabKey]: { items: records, hasMore, loaded: true, loading: false },
        }))
      }

      setCounts(prev => ({ ...prev, [tabKey]: result.total || 0 }))
      pageRefs.current[tabKey] = page
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '获取数据失败')
      if (!append) {
        setTabState(prev => ({
          ...prev,
          [tabKey]: { ...prev[tabKey], loading: false },
        }))
      }
    } finally {
      if (append) {
        loadingMoreRef.current = false
        setLoadingMore(false)
      }
    }
  }, [message])

  const fetchUserInfo = async () => {
    try {
      const signal = createSignal()
      const data = await getUserMyself({ signal })
      setUserData(data)
      
      if (userInfo) {
        const updatedUserInfo = { ...userInfo, ...data }
        authLogin(updatedUserInfo)
      }
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '获取个人信息失败')
    }
  }

  const refreshUserInfo = async () => {
    try {
      const signal = createSignal()
      const data = await getUser({ signal })
      setUserData((prev) => ({ ...prev, ...data }))
      
      if (userInfo) {
        const updatedUserInfo = { ...userInfo, ...data }
        authLogin(updatedUserInfo)
      }
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '刷新用户信息失败')
    }
  }

  useEffect(() => {
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    
    if (!isAuthenticated) {
      message.warning('请先登录')
      navigate('/')
      return
    }
    
    setLoading(true)
    fetchUserInfo().finally(() => {
      setLoading(false)
    })
  }, [navigate, message])

  useEffect(() => {
    if (!tabState[activeTab].loaded && !tabState[activeTab].loading) {
      const signal = createSignal()
      fetchTabData(activeTab, 1, false, signal)
    }
  }, [activeTab])

  useEffect(() => {
    const handleScroll = () => {
      const currentTab = activeTab
      if (loadingMoreRef.current || !tabState[currentTab]?.hasMore) return

      const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
      const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
      const clientHeight = document.documentElement.clientHeight || window.innerHeight

      if (scrollTop + clientHeight >= scrollHeight - 200) {
        const signal = createSignal()
        fetchTabData(currentTab, pageRefs.current[currentTab] + 1, true, signal)
      }
    }

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [activeTab, tabState, fetchTabData, createSignal])

  useEffect(() => {
    if (!editModalVisible) return
    const modifiedElements = []
    const timer = setTimeout(() => {
      const header = document.querySelector('.edit-profile-modal-header')
      if (!header) return
      let el = header
      while (el && el !== document.body) {
        const style = window.getComputedStyle(el)
        if (style.display === 'flex' || style.display === 'inline-flex') {
          el.style.alignItems = 'flex-start'
          el.style.justifyContent = 'flex-start'
          modifiedElements.push(el)
        }
        if (el.classList && el.classList.contains('ant-modal')) {
          el.style.top = '0'
          modifiedElements.push(el)
        }
        el = el.parentElement
      }
    }, 100)
    return () => {
      clearTimeout(timer)
      modifiedElements.forEach(el => {
        el.style.alignItems = ''
        el.style.justifyContent = ''
        el.style.top = ''
      })
    }
  }, [editModalVisible])

  const handlePostClick = async (post) => {
    if (isMobile) {
      navigate(`/mobile/post/detail/${post.id}`)
      return
    }
    setPostDetailLoading(true)
    setPostDetailModalOpen(true)
    setDetailImageIndex(0)
    try {
      const signal = createSignal()
      const result = await getPost(post.id, { signal })
      setPostDetail(result)
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      message.error(err.message || '加载帖子详情失败')
      setPostDetailModalOpen(false)
    } finally {
      setPostDetailLoading(false)
    }
  }

  const formatDate = (date) => {
    if (!date) return '未知'
    const d = new Date(date)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  const calculateDaysSinceJoin = (date) => {
    if (!date) return 0
    const joinDate = new Date(date)
    const today = new Date()
    joinDate.setHours(0, 0, 0, 0)
    today.setHours(0, 0, 0, 0)
    const diffTime = Math.abs(today - joinDate)
    return Math.round(diffTime / (1000 * 60 * 60 * 24))
  }

  const handleCopyUsername = async () => {
    if (userData?.username) {
      try {
        await navigator.clipboard.writeText(userData.username)
        message.success('已复制账号')
      } catch {
        try {
          const textarea = document.createElement('textarea')
          textarea.value = userData.username
          textarea.style.position = 'fixed'
          textarea.style.opacity = '0'
          document.body.appendChild(textarea)
          textarea.select()
          document.execCommand('copy')
          document.body.removeChild(textarea)
          message.success('已复制账号')
        } catch {
          message.error('复制失败')
        }
      }
    }
  }

  const handleAvatarClick = () => {
    if (userData?.avatar) {
      setAvatarVisible(true)
    }
  }

  const handleEditProfileClick = () => {
    if (isMobile) {
      navigate('/mobile/profile/edit')
      return
    }
    if (userData) {
      setEditModalVisible(true)
      requestAnimationFrame(() => {
        editForm.setFieldsValue({
          id: userData.id,
          username: userData.username,
          password: '',
          email: userData.email,
          phone: userData.phone,
          nickname: userData.nickname
        })
      })
    }
  }

  const handleEditModalOk = async () => {
    try {
      const values = await editForm.validateFields()
      const submitData = {
        id: values.id,
        username: values.username,
        email: values.email || null,
        phone: values.phone || null,
        nickname: values.nickname
      }
      if (showPasswordSection && values.password && values.password.trim() !== '') {
        submitData.password = values.password
        submitData.originalPassword = values.originalPassword
      }
      await editUser(submitData)
      editForm.resetFields()
      message.success('修改成功')
      setEditModalVisible(false)
      
      await refreshUserInfo()
    } catch (error) {
      if (error !== 'cancelled') {
        message.error(error.message || '修改失败')
      }
    }
  }

  const handleEditModalCancel = () => {
    editForm.resetFields()
    setEditModalVisible(false)
    setUploadedAvatarUrl(null)
    setAvatarPreviewUrl(null)
    setShowPasswordSection(false)
  }

  const getBase64 = (file) => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.addEventListener('load', () => resolve(reader.result))
      reader.addEventListener('error', reject)
      reader.readAsDataURL(file)
    })
  }

  const beforeUpload = (file) => {
    const maxSize = getMaxUploadSize()
    const maxSizeText = formatMaxUploadSize()
    const isAllowedImage = isAllowedImageFile(file)
    if (!isAllowedImage) {
      message.error('只能上传图片文件（JPEG、PNG、JPG、GIF、WebP、HEIC）！')
    }
    const isLtSize = file.size <= maxSize
    if (!isLtSize) {
      message.error(`图片大小不能超过${maxSizeText}！`)
    }
    return isAllowedImage && isLtSize
  }

  const handleAvatarChange = async (info) => {
    if (info.file.status === 'uploading') {
      setUploadingAvatar(true)
      return
    }
    if (info.file.status === 'done') {
      await getBase64(info.file.originFileObj).then((url) => {
        setUploadingAvatar(false)
        setAvatarPreviewUrl(url)
      })
      const avatarUrl = info.file.response
      setUploadedAvatarUrl(avatarUrl)
      
      await refreshUserInfo()
      
      message.success('头像上传成功')
    }
    if (info.file.status === 'error') {
      setUploadingAvatar(false)
      message.error('头像上传失败')
    }
  }

  const uploadButton = (
    <button style={{ border: 0, background: 'none' }} type="button">
      {uploadingAvatar ? <LoadingOutlined /> : <PlusOutlined />}
      <div style={{ marginTop: 8 }}>上传</div>
    </button>
  )

  const handleAvatarUpload = async (options) => {
    const { file, onSuccess, onError } = options
    
    setUploadingAvatar(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('id', userData.id)
      
      const result = await uploadAvatar(formData)
      
      if (onSuccess) {
        onSuccess(result)
      }
    } catch (error) {
      if (onError) {
        onError(error)
      }
    }
  }

  const renderTabContent = (tabKey, emptyText) => {
    const tab = tabState[tabKey]

    if (tab.loading) {
      return <Skeleton active paragraph={{ rows: 6 }} />
    }

    if (!tab.items || tab.items.length === 0) {
      return (
        <div className="empty-state">
          <Empty 
            description={emptyText} 
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </div>
      )
    }

    const masonryItems = tab.items.map((post, index) => ({
      key: `post-${post.id}-${index}`,
      data: post,
    }))

    return (
      <div className="profile-masonry-section">
        <Masonry
          columns={isMobile ? 2 : 3}
          gutter={[12, 12]}
          items={masonryItems}
          itemRender={(item) => <PostCard post={item.data} onClick={handlePostClick} variant="profile" />}
        />
        {tab.hasMore ? (
          loadingMore ? (
            <div className="loading-more"><Spin /> 加载中...</div>
          ) : null
        ) : (
          tab.items.length > 0 && <div className="no-more-posts">没有更多了</div>
        )}
      </div>
    )
  }

  const renderProfileHeader = () => {
    if (loading) {
      return (
        <Skeleton avatar={{ size: 100 }} paragraph={{ rows: 4 }} active />
      )
    }

    if (!userData) {
      return (
        <Empty description="暂无用户信息" />
      )
    }

    return (
      <>
        <Avatar 
          size={100} 
          src={userData.avatar}
          className="profile-avatar-large"
          style={{ 
            backgroundColor: userData.avatar ? 'transparent' : 'var(--accent)',
            fontSize: 40 
          }}
          onClick={handleAvatarClick}
        >
          {!userData.avatar && (userData.nickname || userData.username)?.charAt(0)?.toUpperCase()}
        </Avatar>
        
        <div className="profile-nickname-row">
          <h1 className="profile-nickname">
            {userData.nickname || userData.username}
            {userData.role === 'admin' && (
              <span className="admin-badge" title="这个网站伟大的管理员">管理员</span>
            )}
          </h1>
          <span 
            className="profile-username"
            onClick={handleCopyUsername}
          >
            @{userData.username}
          </span>
        </div>
        
        <div className="profile-stats-row">
          <div className="stat-item">
            <span className="stat-value">{counts.notes}</span>
            <span className="stat-label">笔记</span>
          </div>
          <div className="stat-item">
            <span className="stat-value">{counts.favorites}</span>
            <span className="stat-label">收藏</span>
          </div>
          <div className="stat-item">
            <span className="stat-value">{counts.likes}</span>
            <span className="stat-label">点赞</span>
          </div>
        </div>
        
        <div className="profile-meta-row">
          <Tooltip title={`你已经加入 ${calculateDaysSinceJoin(userData.createTime)} 天`}>
            <span>
              <CalendarOutlined /> 加入于 {formatDate(userData.createTime)}
            </span>
          </Tooltip>
        </div>
        
        {(userData.email || userData.phone) && (
          <div className="profile-info-cards">
            {userData.email && (
              <div className="info-card-simple">
                <MailOutlined className="info-icon" />
                <span>{userData.email}</span>
              </div>
            )}
            {userData.phone && (
              <div className="info-card-simple">
                <PhoneOutlined className="info-icon" />
                <span>{userData.phone}</span>
              </div>
            )}
          </div>
        )}

        <Button 
          type="primary" 
          icon={<EditOutlined />}
          className="edit-profile-btn"
          onClick={handleEditProfileClick}
        >
          修改信息
        </Button>

        <div className="profile-tabs-container">
          {tabItems.map(tab => (
            <div
              key={tab.key}
              className={`profile-tab-item ${activeTab === tab.key ? 'profile-tab-active' : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              <span>{tab.icon} {tab.label}</span>
            </div>
          ))}
        </div>
        {renderTabContent(activeTab, tabItems.find(t => t.key === activeTab)?.emptyText || '')}

        <Modal
          className="avatar-modal"
          open={avatarVisible}
          onCancel={() => setAvatarVisible(false)}
          footer={null}
          closable={false}
          width={600}
        >
          {userData?.avatar && <img src={userData.avatar} alt="avatar" />}
        </Modal>

        <Modal
          className="edit-profile-modal"
          open={editModalVisible}
          onOk={handleEditModalOk}
          onCancel={handleEditModalCancel}
          width={600}
          okText="保存"
          cancelText="取消"
        >
          <div className="edit-profile-modal-header">
            <h2>修改个人信息</h2>
          </div>
          <Form
            form={editForm}
            layout="vertical"
            className="edit-profile-form"
          >
            <Form.Item name="id" hidden>
              <Input />
            </Form.Item>

            <Form.Item label="修改头像">
              <Upload
                name="avatar"
                listType="picture-circle"
                className="avatar-uploader"
                showUploadList={false}
                accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
                customRequest={handleAvatarUpload}
                beforeUpload={beforeUpload}
                onChange={handleAvatarChange}
              >
                {(avatarPreviewUrl || userData?.avatar) ? (
                  <img src={avatarPreviewUrl || userData.avatar} alt="avatar" style={{ width: '100%' }} />
                ) : (
                  uploadButton
                )}
              </Upload>
            </Form.Item>

            <Form.Item
              label="账号"
              name="username"
              rules={[
                { required: true, message: '请输入账号' },
                { min: 6, message: '账号长度不能小于 6 个字符' },
                { max: 11, message: '账号长度不能大于 11 个字符' }
              ]}
            >
              <Input 
                prefix={<UserOutlined />} 
                placeholder="请输入账号"
              />
            </Form.Item>

            <div className="password-section">
              <div className="password-section-header">
                <span className="password-section-label">
                  <LockOutlined className="password-section-icon" />
                  修改密码
                </span>
                <Switch
                  size="small"
                  checked={showPasswordSection}
                  onChange={(checked) => {
                    setShowPasswordSection(checked)
                    if (!checked) {
                      editForm.setFieldsValue({ password: '', originalPassword: '' })
                    }
                  }}
                />
              </div>
              {showPasswordSection && (
                <div className="password-section-fields">
                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入新密码' },
                      { min: 8, message: '密码长度不能小于 8 个字符' },
                      { max: 20, message: '密码长度不能大于 20 个字符' }
                    ]}
                  >
                    <Input.Password 
                      prefix={<LockOutlined />} 
                      placeholder="请输入新密码"
                    />
                  </Form.Item>
                  <Form.Item
                    name="originalPassword"
                  >
                    <Input.Password 
                      prefix={<EyeOutlined />} 
                      placeholder="请输入原始密码"
                    />
                  </Form.Item>
                </div>
              )}
            </div>

            <Form.Item
              label="昵称"
              name="nickname"
              rules={[{ required: true, message: '请输入昵称' }]}
            >
              <Input 
                placeholder="请输入昵称"
              />
            </Form.Item>

            <Form.Item
              label="邮箱"
              name="email"
              rules={[
                { type: 'email', message: '请输入有效的邮箱地址' }
              ]}
            >
              <Input 
                prefix={<MailOutlined />} 
                placeholder="请输入邮箱"
              />
            </Form.Item>

            <Form.Item
              label="手机号"
              name="phone"
              rules={[
                { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' }
              ]}
            >
              <Input 
                prefix={<PhoneOutlined />} 
                placeholder="请输入手机号"
              />
            </Form.Item>
          </Form>
        </Modal>
      </>
    )
  }

  const tabItems = [
    { key: 'notes', label: '图文', icon: <FileTextOutlined />, emptyText: '你还没有发布任何内容哦' },
    { key: 'favorites', label: '收藏', icon: <StarOutlined />, emptyText: '暂无收藏内容' },
    { key: 'likes', label: '点赞', icon: <HeartOutlined />, emptyText: '暂无点赞内容' },
  ]

  return (
    <>
      {renderProfileHeader()}

      <PostDetailModal
        open={postDetailModalOpen}
        onClose={() => setPostDetailModalOpen(false)}
        loading={postDetailLoading}
        postDetail={postDetail}
        detailImageIndex={detailImageIndex}
        onImageIndexChange={setDetailImageIndex}
        currentUsername={userData?.username}
        onEdit={() => {
          setPostDetailModalOpen(false)
          setEditingPostDetail(postDetail)
          setCreateEditModalOpen(true)
        }}
      />

      <CreateEditPostModal
        open={createEditModalOpen}
        onClose={() => {
          setCreateEditModalOpen(false)
          setEditingPostDetail(null)
        }}
        editPostDetail={editingPostDetail}
        onSuccess={() => {
          const signal = createSignal()
          fetchTabData(activeTab, 1, false, signal)
        }}
      />
    </>
  )
}

export default UserProfile