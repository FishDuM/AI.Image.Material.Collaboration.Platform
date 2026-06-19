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
  UserOutlined,
} from '@ant-design/icons'
import { editUser, getUser, getUserMyself, getUserProfile, markPasswordChange } from '../api'
import { AuthContext } from '../context/AuthContext'
import { useIsMobile } from '../hooks/useIsMobile'
import { useAvatarUpload } from '../hooks/useAvatarUpload'
import { createBeforeUpload } from '../utils/upload'
import { copyToClipboard } from '../utils/clipboard'
import { isCanceledError } from '../utils/error'
import { formatTime } from '../utils/constants'
import { emailRules, newPasswordRules, nicknameRules, originalPasswordRules, phoneRules, usernameRules } from '../utils/formRules'
import './UserProfile.css'

function UserProfile() {
  const { message } = AntApp.useApp()
  const beforeUpload = createBeforeUpload(message)
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
  const [editModalLoading, setEditModalLoading] = useState(false)
  const [editForm] = Form.useForm()
  const [showPasswordSection, setShowPasswordSection] = useState(false)

  const refreshUserInfo = async () => {
    try {
      const data = isOwnProfile ? await getUser() : await getUserProfile(profileUserId)
      setUserData((prev) => ({ ...prev, ...data }))
      if (isOwnProfile && userInfo) {
        const updatedUserInfo = { ...userInfo, ...data }
        updateUserInfo(updatedUserInfo)
      }
    } catch (error) {
      if (isCanceledError(error)) return
      message.error(error.message || '刷新用户信息失败')
    }
  }

  const { previewUrl: avatarPreviewUrl, handleChange: handleAvatarChange, handleUpload: handleAvatarUpload, reset: resetAvatar, uploadButton } = useAvatarUpload({ userId: userData?.id, onSuccess: refreshUserInfo })

  const fetchUserInfo = async (signal) => {
    try {
      const data = isOwnProfile
        ? await getUserMyself({ signal })
        : await getUserProfile(profileUserId, { signal })
      if (signal?.aborted) return
      setUserData(data)
      if (isOwnProfile && userInfo) {
        const updatedUserInfo = { ...userInfo, ...data }
        updateUserInfo(updatedUserInfo)
      }
    } catch (error) {
      if (signal?.aborted) return
      if (isCanceledError(error)) return
      message.error(error.message || '获取个人信息失败')
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
  }, [profileUserId, isAuthenticated, isOwnProfile, navigate])

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
    setEditModalLoading(true)
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
      if (submitData.password) {
        markPasswordChange()
      }
      editForm.resetFields()
      message.success('修改成功')
      setEditModalVisible(false)
      await refreshUserInfo()
    } catch (error) {
      if (error !== 'cancelled') {
        message.error(error.message || '修改失败')
      }
    } finally {
      setEditModalLoading(false)
    }
  }

  const handleEditModalCancel = () => {
    editForm.resetFields()
    setEditModalVisible(false)
    resetAvatar()
    setShowPasswordSection(false)
  }

  const renderProfileHeader = () => {
    if (loading) {
      return <Skeleton avatar={{ size: 100 }} paragraph={{ rows: 4 }} active />
    }
    if (!userData) {
      return <Empty description="暂无用户信息" />
    }
    const joinDays = userData.createTime
      ? Math.round(Math.abs(new Date().setHours(0,0,0,0) - new Date(userData.createTime).setHours(0,0,0,0)) / (1000 * 60 * 60 * 24))
      : 0
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
          <Tooltip title={isOwnProfile ? `你已经加入 ${joinDays} 天` : `TA已加入 ${joinDays} 天`}>
            <span>
              <CalendarOutlined /> 加入于 {formatTime(userData.createTime) || '未知'}
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
          confirmLoading={editModalLoading}
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
              rules={usernameRules}
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
                    rules={newPasswordRules}
                  >
                    <Input.Password prefix={<LockOutlined />} placeholder="请输入新密码" />
                  </Form.Item>
                  <Form.Item name="originalPassword"
                    rules={originalPasswordRules}
                  >
                    <Input.Password prefix={<EyeOutlined />} placeholder="请输入原始密码" />
                  </Form.Item>
                </div>
              )}
            </div>

            <Form.Item label="昵称" name="nickname" rules={nicknameRules}>
              <Input placeholder="请输入昵称" />
            </Form.Item>

            <Form.Item label="邮箱" name="email" rules={emailRules}>
              <Input prefix={<MailOutlined />} placeholder="请输入邮箱" />
            </Form.Item>

            <Form.Item label="手机号" name="phone" rules={phoneRules}>
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
