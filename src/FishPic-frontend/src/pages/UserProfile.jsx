import { useContext, useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Avatar, Tabs, Empty, Skeleton, Modal, Button, Form, Input } from 'antd'
import { 
  UserOutlined, 
  MailOutlined, 
  PhoneOutlined, 
  CalendarOutlined,
  FileTextOutlined,
  StarOutlined,
  HeartOutlined,
  EditOutlined,
  LockOutlined
} from '@ant-design/icons'
import { getUserMyself, editUser } from '../api'
import { getToken } from '../utils/storage'
import { AuthContext } from '../context/AuthContext.jsx'
import { ThemeContext } from '../main.jsx'
import './UserProfile.css'

function UserProfile() {
  const { message } = AntApp.useApp()
  const { isDarkMode } = useContext(ThemeContext)
  const { userInfo, login: authLogin } = useContext(AuthContext)
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [userData, setUserData] = useState(null)
  const [activeTab, setActiveTab] = useState('notes')
  const [avatarVisible, setAvatarVisible] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editForm] = Form.useForm()
  const hasFetchedRef = useRef(false)

  useEffect(() => {
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    
    const fetchUserInfo = async () => {
      if (!getToken()) {
        message.warning('请先登录')
        navigate('/')
        return
      }
      
      setLoading(true)
      try {
        const data = await getUserMyself()
        setUserData(data)
        
        if (userInfo) {
          const updatedUserInfo = { ...userInfo, ...data }
          authLogin(updatedUserInfo)
        }
      } catch (error) {
        message.error(error.message || '获取个人信息失败')
        console.error('获取个人信息失败:', error)
      } finally {
        setLoading(false)
      }
    }

    fetchUserInfo()
  }, [navigate, message])

  const formatDate = (date) => {
    if (!date) return '未知'
    const d = new Date(date)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  const calculateDaysSinceJoin = (date) => {
    if (!date) return 0
    const joinDate = new Date(date)
    const today = new Date()
    const diffTime = Math.abs(today - joinDate)
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24))
    return diffDays
  }

  const handleCopyUsername = () => {
    if (userData?.username) {
      navigator.clipboard.writeText(userData.username).then(() => {
        message.success('已复制账号')
      }).catch(() => {
        message.error('复制失败')
      })
    }
  }

  const handleAvatarClick = () => {
    if (userData?.avatar) {
      setAvatarVisible(true)
    }
  }

  const handleEditProfileClick = () => {
    if (userData) {
      editForm.setFieldsValue({
        id: userData.id,
        username: userData.username,
        password: '',
        avatar: userData.avatar,
        email: userData.email,
        phone: userData.phone,
        nickname: userData.nickname
      })
      setEditModalVisible(true)
    }
  }

  const handleEditModalOk = async () => {
    try {
      const values = await editForm.validateFields()
      const submitData = {
        id: values.id,
        username: values.username,
        avatar: values.avatar && values.avatar.trim() !== '' ? values.avatar : null,
        email: values.email || null,
        phone: values.phone || null,
        nickname: values.nickname
      }
      if (values.password && values.password.trim() !== '') {
        submitData.password = values.password
      }
      await editUser(submitData)
      message.success('修改成功')
      setEditModalVisible(false)
      
      const updatedUserData = { ...userData, ...submitData }
      setUserData(updatedUserData)
      
      if (userInfo) {
        const updatedUserInfo = { ...userInfo, ...submitData }
        authLogin(updatedUserInfo)
      }
      
      editForm.resetFields()
    } catch (error) {
      if (error !== 'cancelled') {
        message.error(error.message || '修改失败')
      }
    }
  }

  const handleEditModalCancel = () => {
    setEditModalVisible(false)
    editForm.resetFields()
  }

  const renderProfileHeader = () => {
    if (loading) {
      return (
        <div className="profile-header">
          <div className="profile-header-content">
            <Skeleton avatar={{ size: 100 }} paragraph={{ rows: 4 }} active />
          </div>
        </div>
      )
    }

    if (!userData) {
      return (
        <div className="profile-header">
          <div className="profile-header-content">
            <Empty description="暂无用户信息" />
          </div>
        </div>
      )
    }

    const postCount = userData.postList?.length || 0
    const collectCount = userData.postCollectList?.length || 0
    const likeCount = userData.postLikeList?.length || 0

    return (
      <>
        <div className="profile-header">
          <div className="profile-header-content">
            <div className="profile-avatar-section">
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
            </div>
            
            <div className="profile-info-section">
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
                  <span className="stat-value">{postCount}</span>
                  <span className="stat-label">笔记</span>
                </div>
                <div className="stat-item">
                  <span className="stat-value">{collectCount}</span>
                  <span className="stat-label">收藏</span>
                </div>
                <div className="stat-item">
                  <span className="stat-value">{likeCount}</span>
                  <span className="stat-label">点赞</span>
                </div>
              </div>
              
              <div className="profile-meta-row">
                <span title={`你已经加入 ${calculateDaysSinceJoin(userData.createTime)} 天`}>
                  <CalendarOutlined /> 加入于 {formatDate(userData.createTime)}
                </span>
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
            </div>

            <div className="profile-actions-section">
              <Button 
                type="primary" 
                icon={<EditOutlined />}
                className="edit-profile-btn"
                onClick={handleEditProfileClick}
              >
                修改信息
              </Button>
            </div>
          </div>
        </div>

        <Modal
          className="avatar-modal"
          open={avatarVisible}
          onCancel={() => setAvatarVisible(false)}
          footer={null}
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

            <Form.Item
              label="密码"
              name="password"
              tooltip="留空表示不修改密码"
              rules={[
                { min: 8, message: '密码长度不能小于 8 个字符' },
                { max: 20, message: '密码长度不能大于 20 个字符' }
              ]}
            >
              <Input.Password 
                prefix={<LockOutlined />} 
                placeholder="请输入新密码，留空表示不修改"
              />
            </Form.Item>

            <Form.Item
              label="头像 URL"
              name="avatar"
            >
              <Input 
                placeholder="请输入头像 URL"
              />
            </Form.Item>

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

  const renderPostList = (posts, emptyText) => {
    if (loading) {
      return <Skeleton active paragraph={{ rows: 6 }} />
    }

    if (!posts || posts.length === 0) {
      return (
        <div className="empty-state">
          <Empty 
            description={emptyText} 
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </div>
      )
    }

    return (
      <div className="posts-grid">
        {posts.map((post) => (
          <div 
            key={post.id} 
            className="post-card-grid"
            onClick={() => navigate(`/post/${post.id}`)}
          >
            <div className="post-cover">
              <FileTextOutlined />
            </div>
            <div className="post-info">
              <h3 className="post-title-grid">{post.title || '无标题'}</h3>
              <div className="post-meta-grid">
                <span>{formatDate(post.createTime)}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    )
  }

  const tabItems = [
    {
      key: 'notes',
      label: (
        <span>
          <FileTextOutlined />
          图文
        </span>
      ),
      children: renderPostList(userData?.postList, '你还没有发布任何内容哦'),
    },
    {
      key: 'favorites',
      label: (
        <span>
          <StarOutlined />
          收藏
        </span>
      ),
      children: renderPostList(userData?.postCollectList, '暂无收藏内容'),
    },
    {
      key: 'likes',
      label: (
        <span>
          <HeartOutlined />
          点赞
        </span>
      ),
      children: renderPostList(userData?.postLikeList, '暂无点赞内容'),
    },
  ]

  return (
    <div className="user-profile-page">
      {renderProfileHeader()}
      
      <div className="profile-tabs-section">
        <div className="profile-tabs-container">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={tabItems}
            className="profile-tabs"
          />
        </div>
      </div>
    </div>
  )
}

export default UserProfile
