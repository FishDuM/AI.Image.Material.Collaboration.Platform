import { useState, useEffect, useCallback, useContext } from 'react'
import { Form, Input, Button, App } from 'antd'
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getLoginCheckCode, login } from '../api'
import { AuthContext } from '../context/AuthContext'
import MobilePageWrapper from '../components/MobilePageWrapper'
import './MobileLoginRegister.css'

export default function MobileLoginPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const { login: authLogin } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [captchaImage, setCaptchaImage] = useState('')
  const [captchaKey, setCaptchaKey] = useState('')

  const fetchCaptcha = useCallback(async () => {
    try {
      const response = await getLoginCheckCode()
      const data = response?.data ?? response
      const inner = data?.data ?? data
      if (inner?.captchaKey && inner?.base64Image) {
        setCaptchaKey(inner.captchaKey)
        setCaptchaImage(inner.base64Image)
      }
    } catch {
      void 0
    }
  }, [])

  useEffect(() => {
    fetchCaptcha()
  }, [fetchCaptcha])

  const handleRefreshCaptcha = () => {
    fetchCaptcha()
    form.setFieldValue('checkCode', '')
  }

  const handleFinish = async (values) => {
    setLoading(true)
    try {
      if (!captchaKey) {
        message.error('验证码已过期，请刷新验证码')
        form.setFieldValue('checkCode', '')
        setLoading(false)
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
      navigate('/', { replace: true })
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
            rules={[
              { required: true, message: '请输入账号' },
              { min: 6, message: '账号至少 6 个字符' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入账号" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码至少 8 个字符' },
            ]}
          >
              <Input.Password prefix={<LockOutlined/>} placeholder="请输入密码" size="large"
                              autoComplete="current-password"/>
          </Form.Item>
          <div className="mobile-captcha-row">
            <Form.Item
              name="checkCode"
              rules={[{ required: true, message: '请输入验证码' }]}
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
