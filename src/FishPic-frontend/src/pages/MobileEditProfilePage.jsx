import { useState, useEffect, useCallback, useContext } from 'react'
import { Form, Input, Button, Avatar, App } from 'antd'
import { UserOutlined, MailOutlined, PhoneOutlined, CameraOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getUserMyself, editUser, uploadAvatar } from '../api'
import { AuthContext } from '../context/AuthContext'
import MobilePageWrapper from '../components/MobilePageWrapper'
import './MobileLoginRegister.css'

export default function MobileEditProfilePage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const { updateUserInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [userData, setUserData] = useState(null)
  const [avatarLoading, setAvatarLoading] = useState(false)

  const fetchUserData = useCallback(async () => {
    try {
      const res = await getUserMyself()
      const data = res?.data?.data || res?.data || res
      setUserData(data)
      form.setFieldsValue({
        username: data.username,
        nickname: data.nickname,
        email: data.email,
        phone: data.phone,
      })
    } catch {
      message.error('获取用户信息失败')
    }
  }, [form, message])

  useEffect(() => {
    fetchUserData()
  }, [fetchUserData])

  const handleAvatarChange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      message.error('图片大小不能超过5MB')
      return
    }
    setAvatarLoading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('id', userData.id)
      const res = await uploadAvatar(formData)
      const newAvatar = res?.data?.data?.avatar || res?.data?.avatar
      if (newAvatar) {
        setUserData(prev => ({ ...prev, avatar: newAvatar }))
        updateUserInfo(prev => ({ ...prev, avatar: newAvatar }))
        message.success('头像更新成功')
      }
    } catch {
      message.error('头像上传失败')
    } finally {
      setAvatarLoading(false)
    }
  }

  const handleFinish = async (values) => {
    setLoading(true)
    try {
      await editUser({
        id: userData.id,
        nickname: values.nickname,
        email: values.email,
        phone: values.phone,
      })
      updateUserInfo(prev => ({
        ...prev,
        nickname: values.nickname,
        email: values.email,
        phone: values.phone,
      }))
      message.success('保存成功')
      navigate(-1)
    } catch {
      message.error('保存失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <MobilePageWrapper title="编辑资料" showBack>
      <div className="mobile-edit-profile">
        <div className="mobile-edit-avatar-section">
          <div className="mobile-edit-avatar-wrapper">
            <Avatar
              size={80}
              src={userData?.avatar}
              icon={!userData?.avatar && <UserOutlined />}
            />
            <label className="mobile-edit-avatar-btn">
              <CameraOutlined />
              <input
                type="file"
                accept="image/*"
                onChange={handleAvatarChange}
                disabled={avatarLoading}
                style={{ display: 'none' }}
              />
            </label>
          </div>
          <span className="mobile-edit-avatar-hint">点击更换头像</span>
        </div>

        <Form
          form={form}
          layout="vertical"
          onFinish={handleFinish}
          className="mobile-edit-form"
          requiredMark={false}
        >
          <Form.Item label="用户名" name="username">
            <Input
              prefix={<UserOutlined />}
              disabled
              size="large"
            />
          </Form.Item>

          <Form.Item
            label="昵称"
            name="nickname"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="请输入昵称"
              size="large"
              maxLength={20}
            />
          </Form.Item>

          <Form.Item
            label="邮箱"
            name="email"
            rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="请输入邮箱"
              size="large"
            />
          </Form.Item>

          <Form.Item label="手机号" name="phone">
            <Input
              prefix={<PhoneOutlined />}
              placeholder="请输入手机号"
              size="large"
            />
          </Form.Item>

          <Form.Item className="mobile-edit-submit">
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
              size="large"
            >
              保存
            </Button>
          </Form.Item>
        </Form>
      </div>
    </MobilePageWrapper>
  )
}
