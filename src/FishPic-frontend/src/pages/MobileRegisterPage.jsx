import { useState, useEffect } from 'react'
import { Form, Input, Button, App } from 'antd'
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getRegisterCheckCode, register } from '../api'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { useCaptcha } from '../hooks/useCaptcha'
import { captchaRules, confirmPasswordRules, passwordRules, usernameRules } from '../utils/formRules'
import './MobileLoginRegister.css'

export default function MobileRegisterPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const { captchaImage, captchaKey, refreshCaptcha } = useCaptcha(getRegisterCheckCode)

  useEffect(() => {
    refreshCaptcha()
  }, [refreshCaptcha])

  const handleRefreshCaptcha = () => {
    refreshCaptcha()
    form.setFieldValue('checkCode', '')
  }

  const handleFinish = async (values) => {
    setLoading(true)
    try {
      if (!captchaKey) {
        message.error('验证码已过期，请刷新验证码')
        form.setFieldValue('checkCode', '')
        return
      }

      const registerData = {
        username: values.username,
        password: values.password,
        checkPassword: values.checkPassword,
        checkCode: values.checkCode,
        captchaKey,
      }
      await register(registerData)
      message.success('注册成功，请登录')
      form.resetFields()
      navigate('/mobile/login', { replace: true })
    } catch (err) {
      message.error(err.message || '注册失败，请重试')
      form.setFieldValue('checkCode', '')
    } finally {
      setLoading(false)
    }
  }

  return (
    <MobilePageWrapper title="用户注册">
      <div className="mobile-auth-container">
        <div className="mobile-auth-header">
          <div className="mobile-auth-logo">
            <img src="/logo_white.png" alt="FishPics" className="mobile-auth-logo-img" />
          </div>
          <h1 className="mobile-auth-title">欢迎来到 FishPics</h1>
          <p className="mobile-auth-subtitle">创建账号，开启你的图片协作之旅</p>
        </div>
        <Form form={form} layout="vertical" onFinish={handleFinish} autoComplete="off" className="mobile-auth-form">
          <Form.Item
            name="username"
            rules={usernameRules}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入账号" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={passwordRules}
          >
              <Input.Password prefix={<LockOutlined/>} placeholder="请输入密码" size="large"
                              autoComplete="new-password"/>
          </Form.Item>
          <Form.Item
            name="checkPassword"
            dependencies={['password']}
            rules={confirmPasswordRules}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请再次输入密码" size="large" />
          </Form.Item>
          <div className="mobile-captcha-row">
            <Form.Item
              name="checkCode"
              rules={captchaRules}
              style={{ flex: 1, marginBottom: 0 }}
            >
              <Input prefix={<SafetyCertificateOutlined />} placeholder="请输入验证码" size="large" maxLength={5} />
            </Form.Item>
            <div
              className="mobile-captcha-image"
              onClick={handleRefreshCaptcha}
              title="点击刷新验证码"
            >
              {captchaImage ? (
                <img src={captchaImage} alt="验证码" />
              ) : (
                <span style={{ color: '#999', fontSize: 12 }}>加载中...</span>
              )}
            </div>
          </div>
          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large" loading={loading}>
              注册
            </Button>
          </Form.Item>
        </Form>
        <div className="mobile-auth-link">
          已有账号？<span onClick={() => navigate('/mobile/login')}>立即登录</span>
        </div>
      </div>
    </MobilePageWrapper>
  )
}
