import { useContext, useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Avatar, Tabs, Empty, Skeleton, Modal, Image, Button, Form, Input } from 'antd'
import { 
  UserOutlined, 
  MailOutlined, 
  PhoneOutlined, 
  CalendarOutlined,
  FileTextOutlined,
  StarOutlined,
  HeartOutlined,
  HomeOutlined,
  EditOutlined,
  LockOutlined
} from '@ant-design/icons'
import { getUserMyself, editUser } from '../api'
import { getToken } from '../utils/storage'
import { ThemeContext } from '../main.jsx'
import './UserProfile.css'

function UserProfile() {
  const { message } = AntApp.useApp()
  const { isDarkMode } = useContext(ThemeContext)
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
    
    const fetchUserInfo = async () => {
      if (hasFetchedRef.current) return
      
      if (!getToken()) {
        message.warning('请先登录')
        navigate('/')
        return
      }
      
      hasFetchedRef.current = true
      setLoading(true)
      try {
        const data = await getUserMyself()
        setUserData(data)
      } catch (error) {
        message.error(error.message || '获取个人信息失败')
        console.error('获取个人信息失败:', error)
      } finally {
        setLoading(false)
      }
    }

    fetchUserInfo()
  }, [])

  const formatDate = (date) => {
    if (!date) return '未知'
    const d = new Date(date)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
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
        avatar: values.avatar,
        email: values.email,
        phone: values.phone,
        nickname: values.nickname
      }
      if (values.password && values.password.trim() !== '') {
        submitData.password = values.password
      }
      await editUser(submitData)
      message.success('修改成功')
      setEditModalVisible(false)
      setUserData(prev => ({ ...prev, ...values }))
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

    const followingCount = userData.followingCount || 0
    const followersCount = userData.followersCount || 0
    const likedCount = userData.likedCount || 0

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
                <h1 className="profile-nickname">{userData.nickname || userData.username}</h1>
                <span className="profile-username">@{userData.username}</span>
              </div>
              
              <p className="profile-bio">
                {userData.bio || '这个人很懒，什么都没写~'}
              </p>
              
              <div className="profile-stats-row">
                <div className="stat-item">
                  <span className="stat-value">{followingCount}</span>
                  <span className="stat-label">关注</span>
                </div>
                <div className="stat-item">
                  <span className="stat-value">{followersCount}</span>
                  <span className="stat-label">粉丝</span>
                </div>
                <div className="stat-item">
                  <span className="stat-value">{likedCount}</span>
                  <span className="stat-label">获赞与收藏</span>
                </div>
              </div>
              
              <div className="profile-meta-row">
                <span>
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
            <Form.Item
              name="id"
              hidden
            >
              <Input />
            </Form.Item>

            <Form.Item
              label="用户名"
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input 
                prefix={<UserOutlined />} 
                placeholder="请输入用户名"
              />
            </Form.Item>

            <Form.Item
              label="密码"
              name="password"
              tooltip="留空表示不修改密码"
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

  const renderNotes = () => {
    if (loading) {
      return <Skeleton active paragraph={{ rows: 6 }} />
    }

    if (!userData?.postList || userData.postList.length === 0) {
      return (
        <div className="empty-state">
          <Empty 
            description="你还没有发布任何内容哦" 
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </div>
      )
    }

    return (
      <div className="posts-grid">
        {userData.postList.map((post, index) => (
          <div 
            key={post.id || index} 
            className="post-card-grid"
            onClick={() => navigate(`/post/${post.id}`)}
          >
            <div className="post-cover">
              {post.cover ? (
                <img src={post.cover} alt={post.title} />
              ) : (
                <FileTextOutlined />
              )}
            </div>
            <div className="post-info">
              <h3 className="post-title-grid">{post.title}</h3>
              <div className="post-meta-grid">
                <span>{formatDate(post.createTime)}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    )
  }

  const renderFavorites = () => {
    return (
      <div className="empty-state">
        <Empty 
          description="暂无收藏内容" 
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      </div>
    )
  }

  const renderLikes = () => {
    return (
      <div className="empty-state">
        <Empty 
          description="暂无点赞内容" 
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      </div>
    )
  }

  const tabItems = [
    {
      key: 'notes',
      label: (
        <span>
          <FileTextOutlined />
          笔记
        </span>
      ),
      children: renderNotes(),
    },
    {
      key: 'favorites',
      label: (
        <span>
          <StarOutlined />
          收藏
        </span>
      ),
      children: renderFavorites(),
    },
    {
      key: 'likes',
      label: (
        <span>
          <HeartOutlined />
          点赞
        </span>
      ),
      children: renderLikes(),
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
