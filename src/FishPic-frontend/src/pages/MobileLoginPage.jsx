import { useState, useEffect, useContext } from 'react'
import { Form, Input, Button, App } from 'antd'
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getLoginCheckCode, login } from '../api'
import { AuthContext } from '../context/AuthContext'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { useCaptcha } from '../hooks/useCaptcha'
import { captchaRules, passwordRules, usernameRules } from '../utils/formRules'
import './MobileLoginRegister.css'

export default function MobileLoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const { login: authLogin } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const { captchaImage, captchaKey, refreshCaptcha } = useCaptcha(getLoginCheckCode)

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

      const loginData = {
        ...values,
        captchaKey,
      }
      const result = await login(loginData)
      message.success('登录成功')
      authLogin(result)
      form.resetFields()
      // 登录成功后,如果 url 带 ?redirect=...,跳回原页面
      const redirect = searchParams.get('redirect')
      const safeRedirect = redirect && /^\/(?!\/)/.test(redirect) ? redirect : '/'
      navigate(safeRedirect, { replace: true })
    } catch (err) {
      message.error(err.message || '登录失败，请重试')
      form.setFieldValue('checkCode', '')
    } finally {
      setLoading(false)
    }
  }

  return (
    <MobilePageWrapper title="用户登录">
      <div className="mobile-auth-container">
        <div className="mobile-auth-header">
          <div className="mobile-auth-logo">
            <img src="/logo_white.png" alt="FishPics" className="mobile-auth-logo-img" />
          </div>
          <h1 className="mobile-auth-title">欢迎来到 FishPics</h1>
          <p className="mobile-auth-subtitle">登录以开始你的创作之旅</p>
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
                              autoComplete="current-password"/>
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
              登录
            </Button>
          </Form.Item>
        </Form>
        <div className="mobile-auth-link">
          还没有账号？<span onClick={() => navigate('/mobile/register')}>立即注册</span>
        </div>
      </div>
    </MobilePageWrapper>
  )
}
