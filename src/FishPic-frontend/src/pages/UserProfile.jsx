import { useContext, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  App as AntApp,
  Avatar,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Skeleton,
  Switch,
  Tooltip,
  Upload,
} from 'antd'
import {
  CalendarOutlined,
  EditOutlined,
  EyeOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  PlusOutlined,
  UserOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import { editUser, getUser, getUserMyself, getUserProfile, uploadAvatar } from '../api'
import { AuthContext } from '../context/AuthContext'
import { useIsMobile } from '../hooks/useIsMobile'
import { getBase64, beforeUpload } from '../utils/upload'
import { copyToClipboard } from '../utils/clipboard'
import './UserProfile.css'

function UserProfile() {
  const { message } = AntApp.useApp()
  const { userInfo, updateUserInfo, isAuthenticated } = useContext(AuthContext)
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const [searchParams] = useSearchParams()
  const profileUserId = searchParams.get('userId')
  const currentUserId = userInfo?.id
  const isOwnProfile = !profileUserId || String(profileUserId) === String(currentUserId)

  const [loading, setLoading] = useState(true)
  const [userData, setUserData] = useState(null)
  const [avatarVisible, setAvatarVisible] = useState(false)
  const [editModalVisible, setEditModalVisible] = useState(false)
  const [editForm] = Form.useForm()
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState(null)
  const [showPasswordSection, setShowPasswordSection] = useState(false)

  const fetchUserInfo = async (signal) => {
    try {
      const data = isOwnProfile ? await getUserMyself() : await getUserProfile(profileUserId)
      if (signal?.aborted) return
      setUserData(data)
      if (isOwnProfile && userInfo) {
        const updatedUserInfo = { ...userInfo, ...data }
        updateUserInfo(updatedUserInfo)
      }
    } catch (error) {
      if (signal?.aborted) return
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '获取个人信息失败')
    }
  }

  const refreshUserInfo = async () => {
    try {
      const data = isOwnProfile ? await getUser() : await getUserProfile(profileUserId)
      setUserData((prev) => ({ ...prev, ...data }))
      if (isOwnProfile && userInfo) {
        const updatedUserInfo = { ...userInfo, ...data }
        updateUserInfo(updatedUserInfo)
      }
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '刷新用户信息失败')
    }
  }

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/')
      return
    }
    const controller = new AbortController()
    setLoading(true)
    fetchUserInfo(controller.signal).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [profileUserId, isAuthenticated, isOwnProfile])

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
      const ok = await copyToClipboard(userData.username)
      if (ok) message.success('已复制账号')
      else message.error('复制失败')
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
          nickname: userData.nickname,
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
        nickname: values.nickname,
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
    setAvatarPreviewUrl(null)
    setShowPasswordSection(false)
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

  const renderProfileHeader = () => {
    if (loading) {
      return <Skeleton avatar={{ size: 100 }} paragraph={{ rows: 4 }} active />
    }
    if (!userData) {
      return <Empty description="暂无用户信息" />
    }
    return (
      <>
        <Avatar
          size={100}
          src={userData.avatar}
          className="profile-avatar-large"
          style={{
            backgroundColor: userData.avatar ? 'transparent' : 'var(--accent)',
            fontSize: 40,
          }}
          onClick={handleAvatarClick}
        >
          {!userData.avatar && (userData.nickname || userData.username)?.charAt(0)?.toUpperCase()}
        </Avatar>

        <div className="profile-nickname-row">
          <h1 className="profile-nickname">
            {userData.nickname || userData.username}
            {userData?.permissions?.includes('system:user:manage') && (
              <span className="admin-badge" title="这个网站伟大的管理员">管理员</span>
            )}
          </h1>
          <span className="profile-username" onClick={handleCopyUsername}>
            @{userData.username}
          </span>
        </div>

        <div className="profile-meta-row">
          <Tooltip title={isOwnProfile ? `你已经加入 ${calculateDaysSinceJoin(userData.createTime)} 天` : `TA已加入 ${calculateDaysSinceJoin(userData.createTime)} 天`}>
            <span>
              <CalendarOutlined /> 加入于 {formatDate(userData.createTime)}
            </span>
          </Tooltip>
        </div>

        {isOwnProfile && (userData.email || userData.phone) && (
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

        {isOwnProfile && (
          <Button
            type="primary"
            icon={<EditOutlined />}
            className="edit-profile-btn"
            onClick={handleEditProfileClick}
          >
            修改信息
          </Button>
        )}

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
          <Form form={editForm} layout="vertical" className="edit-profile-form">
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
                { max: 11, message: '账号长度不能大于 11 个字符' },
              ]}
            >
              <Input prefix={<UserOutlined />} placeholder="请输入账号" />
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
                      { max: 20, message: '密码长度不能大于 20 个字符' },
                    ]}
                  >
                    <Input.Password prefix={<LockOutlined />} placeholder="请输入新密码" />
                  </Form.Item>
                  <Form.Item name="originalPassword"
                    rules={[{ required: true, message: '请输入原始密码' }]}
                  >
                    <Input.Password prefix={<EyeOutlined />} placeholder="请输入原始密码" />
                  </Form.Item>
                </div>
              )}
            </div>

            <Form.Item label="昵称" name="nickname" rules={[{ required: true, message: '请输入昵称' }]}>
              <Input placeholder="请输入昵称" />
            </Form.Item>

            <Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}>
              <Input prefix={<MailOutlined />} placeholder="请输入邮箱" />
            </Form.Item>

            <Form.Item label="手机号" name="phone" rules={[{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' }]}>
              <Input prefix={<PhoneOutlined />} placeholder="请输入手机号" />
            </Form.Item>
          </Form>
        </Modal>
      </>
    )
  }

  return renderProfileHeader()
}

export default UserProfile
