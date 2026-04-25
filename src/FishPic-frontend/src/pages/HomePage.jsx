import { useState, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Button, Modal, Form, Input, message as antdMessage, Card, Checkbox } from 'antd'
import { UserOutlined, LockOutlined, LoginOutlined, LogoutOutlined, QrcodeOutlined, ScanOutlined } from '@ant-design/icons'
import { getLoginCheckCode, login, getRegisterCheckCode, register } from '../api'
import { saveUserInfo, getUserInfo } from '../utils/storage'
import { ThemeContext } from '../main.jsx'
import '../App.css'

const LOGIN_USER_PREFIX = 'LOGIN_CHECK_CODE-'

function HomePage() {
  const { message } = AntApp.useApp()
  const { isDarkMode, toggleTheme } = useContext(ThemeContext)
  const navigate = useNavigate()
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false)
  const [isRegisterMode, setIsRegisterMode] = useState(false)
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()
  const [loginLoading, setLoginLoading] = useState(false)
  const [registerLoading, setRegisterLoading] = useState(false)
  const [loginCheckCodeUrl, setLoginCheckCodeUrl] = useState('')
  const [loginKey, setLoginKey] = useState('')
  const [registerCheckCodeUrl, setRegisterCheckCodeUrl] = useState('')
  const [registerKey, setRegisterKey] = useState('')
  const [userInfo, setUserInfo] = useState(() => getUserInfo())
  const [agreed, setAgreed] = useState(false)



  const showLoginModal = () => {
    setIsLoginModalOpen(true)
    setIsRegisterMode(false)
    fetchLoginCheckCode()
  }

  const handleCancel = () => {
    setIsLoginModalOpen(false)
    loginForm.resetFields()
    registerForm.resetFields()
    setLoginCheckCodeUrl('')
    setLoginKey('')
    setRegisterCheckCodeUrl('')
    setRegisterKey('')
    setAgreed(false)
  }

  const fetchLoginCheckCode = async () => {
    try {
      const response = await getLoginCheckCode()
      
      const data = response?.data || response
      
      if (data && data.code === 1 && data.data) {
        const { captchaKey, base64Image } = data.data
        
        if (captchaKey && base64Image && base64Image.length > 0) {
          setLoginKey(captchaKey)
          const imageSrc = base64Image.startsWith('data:') 
            ? base64Image 
            : `data:image/png;base64,${base64Image}`
          setLoginCheckCodeUrl(imageSrc)
        } else {
          console.warn('验证码数据不完整', { captchaKey, base64Image })
          message.error('获取验证码失败')
        }
      } else {
        console.warn('未获取到验证码', data)
        message.error('获取验证码失败')
      }
    } catch (error) {
      console.error('获取验证码失败', error)
      message.error('获取验证码失败')
    }
  }

  const fetchRegisterCheckCode = async () => {
    try {
      const response = await getRegisterCheckCode()
      
      const data = response?.data || response
      
      if (data && data.code === 1 && data.data) {
        const { captchaKey, base64Image } = data.data
        
        if (captchaKey && base64Image && base64Image.length > 0) {
          setRegisterKey(captchaKey)
          const imageSrc = base64Image.startsWith('data:') 
            ? base64Image 
            : `data:image/png;base64,${base64Image}`
          setRegisterCheckCodeUrl(imageSrc)
        } else {
          console.warn('验证码数据不完整', { captchaKey, base64Image })
          message.error('获取验证码失败')
        }
      } else {
        console.warn('未获取到验证码', data)
        message.error('获取验证码失败')
      }
    } catch (error) {
      console.error('获取验证码失败', error)
      message.error('获取验证码失败')
    }
  }

  const handleRefreshLoginCode = () => {
    fetchLoginCheckCode()
    loginForm.setFieldValue('checkCode', '')
  }

  const handleRefreshRegisterCode = () => {
    fetchRegisterCheckCode()
    registerForm.setFieldValue('checkCode', '')
  }

  const switchToRegister = () => {
    setIsRegisterMode(true)
    fetchRegisterCheckCode()
  }

  const switchToLogin = () => {
    setIsRegisterMode(false)
    fetchLoginCheckCode()
  }

  const handleLoginFinish = async (values) => {
    setLoginLoading(true)
    try {
      if (!loginKey) {
        message.error('验证码已过期，请刷新验证码')
        fetchLoginCheckCode()
        loginForm.setFieldValue('checkCode', '')
        setLoginLoading(false)
        return
      }
      
      const loginData = {
        ...values,
        captchaKey: loginKey
      }
      const result = await login(loginData)
      saveUserInfo(result)
      setUserInfo(result)
      message.success('登录成功')
      setIsLoginModalOpen(false)
      loginForm.resetFields()
      setLoginCheckCodeUrl('')
      setLoginKey('')
    } catch (error) {
      message.error(error.message || '登录失败，请重试')
      fetchLoginCheckCode()
      loginForm.setFieldValue('checkCode', '')
    } finally {
      setLoginLoading(false)
    }
  }

  const handleRegisterFinish = async (values) => {
    if (!agreed) {
      message.warning('请先阅读并同意用户协议')
      setRegisterLoading(false)
      return
    }
    
    setRegisterLoading(true)
    try {
      if (!registerKey) {
        message.error('验证码已过期，请刷新验证码')
        fetchRegisterCheckCode()
        registerForm.setFieldValue('checkCode', '')
        setRegisterLoading(false)
        return
      }
      
      const registerData = {
        ...values,
        captchaKey: registerKey
      }
      await register(registerData)
      message.success('注册成功，请登录')
      setIsRegisterMode(false)
      registerForm.resetFields()
      setRegisterCheckCodeUrl('')
      setRegisterKey('')
      fetchLoginCheckCode()
    } catch (error) {
      message.error(error.message || '注册失败，请重试')
      fetchRegisterCheckCode()
      registerForm.setFieldValue('checkCode', '')
    } finally {
      setRegisterLoading(false)
    }
  }

  return (
    <>
      <div className="hero-section">
        <div className="hero-content">
          <h2 className="hero-title">
            发现与分享
          </h2>
          <p className="hero-subtitle">
            简洁高效的图片管理平台
          </p>
          <div className="hero-divider" />
        </div>
      </div>

      <Modal
        open={isLoginModalOpen}
        onCancel={handleCancel}
        footer={null}
        centered
        className="xhs-modal"
        destroyOnHidden
        width={800}
      >
        <div className="xhs-modal-content">
          <div className="xhs-left-panel">
            <div className="scan-hint">登录后推荐更懂你的笔记</div>
            <div className="qr-card">
              <Card className="qr-code-card" variant="borderless">
                <div className="qr-code-wrapper">
                  <div className="qr-code-bg">
                    <img 
                      src="/qrcode.jpg" 
                      alt="二维码" 
                      className="qr-placeholder"
                      onError={(e) => {
                        e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQwIiBoZWlnaHQ9IjI0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjQwIiBoZWlnaHQ9IjI0MCIgZmlsbD0iI2ZmZiIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iYXJpYWwiIGZvbnQtc2l6ZT0iMTQiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj7mlrnlnLbfkYZcL3RleHQ+PC9zdmc+';
                      }}
                    />
                  </div>
                </div>
                <div className="scan-status">
                  <ScanOutlined className="scan-icon" />
                  <span>暂是实现该功能，敬请期待</span>
                </div>
              </Card>
            </div>
            <div className="scan-tips">
              <span>可用</span>
              <span className="app-name">FishPics</span>
              <span>或</span>
              <span className="app-name-wechat">微信</span>
              <span>扫码</span>
            </div>
          </div>
          
          <div className="xhs-right-panel">
            {!isRegisterMode ? (
              <div className="form-container">
                <h2 className="form-title">账号登录</h2>
                <Form
                  form={loginForm}
                  name="login"
                  onFinish={handleLoginFinish}
                  autoComplete="off"
                  size="large"
                  requiredMark={false}
                  layout="vertical"
                >
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入用户名' },
                      { min: 6, message: '用户名至少 6 个字符' },
                    ]}
                  >
                    <Input
                      prefix={<UserOutlined className="input-icon" />}
                      placeholder="请输入用户名"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 8, message: '密码至少 8 个字符' },
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="input-icon" />}
                      placeholder="请输入密码"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="checkCode"
                    rules={[{ required: true, message: '请输入验证码' }]}
                  >
                    <div className="check-code-row xhs">
                      <Input
                        prefix={<LockOutlined className="input-icon" />}
                        placeholder="请输入验证码"
                        className="xhs-input check-code-input"
                        maxLength={5}
                      />
                      <Button 
                        className="get-code-btn" 
                        onClick={handleRefreshLoginCode}
                        type="link"
                      >
                        {loginCheckCodeUrl && (
                          <img src={loginCheckCodeUrl} alt="验证码" className="check-code-img-btn" />
                        )}
                      </Button>
                    </div>
                  </Form.Item>

                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={loginLoading}
                      block
                      className="xhs-submit-btn"
                    >
                      登录
                    </Button>
                  </Form.Item>

                  <div className="switch-form">
                    <span>没有账号？</span>
                    <Button type="link" className="switch-link" onClick={switchToRegister}>
                      立即注册
                    </Button>
                  </div>
                </Form>
              </div>
            ) : (
              <div className="form-container">
                <h2 className="form-title">注册账号</h2>
                <Form
                  form={registerForm}
                  name="register"
                  onFinish={handleRegisterFinish}
                  autoComplete="off"
                  size="large"
                  requiredMark={false}
                  layout="vertical"
                >
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入用户名' },
                      { min: 6, message: '用户名至少 6 个字符' },
                    ]}
                  >
                    <Input
                      prefix={<UserOutlined className="input-icon" />}
                      placeholder="请输入用户名"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 8, message: '密码至少 8 个字符' },
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="input-icon" />}
                      placeholder="请输入密码"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="checkPassword"
                    dependencies={['password']}
                    rules={[
                      { required: true, message: '请确认密码' },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (!value || getFieldValue('password') === value) {
                            return Promise.resolve()
                          }
                          return Promise.reject(new Error('两次输入的密码不一致'))
                        },
                      }),
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="input-icon" />}
                      placeholder="请确认密码"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="checkCode"
                    rules={[{ required: true, message: '请输入验证码' }]}
                  >
                    <div className="check-code-row xhs">
                      <Input
                        prefix={<LockOutlined className="input-icon" />}
                        placeholder="请输入验证码"
                        className="xhs-input check-code-input"
                        maxLength={5}
                      />
                      <Button 
                        className="get-code-btn" 
                        onClick={handleRefreshRegisterCode}
                        type="link"
                      >
                        {registerCheckCodeUrl ? (
                          <img src={registerCheckCodeUrl} alt="验证码" className="check-code-img-btn" />
                        ) : null}
                      </Button>
                    </div>
                  </Form.Item>

                  <Form.Item>
                    <div className="agreement-row">
                      <Checkbox checked={agreed} onChange={(e) => setAgreed(e.target.checked)}>
                        <span className="agreement-text">
                          我已阅读并同意《用户协议》《隐私政策》
                        </span>
                      </Checkbox>
                    </div>
                  </Form.Item>

                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={registerLoading}
                      block
                      className="xhs-submit-btn"
                    >
                      注册
                    </Button>
                  </Form.Item>

                  <div className="switch-form">
                    <span>已有账号？</span>
                    <Button type="link" className="switch-link" onClick={switchToLogin}>
                      立即登录
                    </Button>
                  </div>
                </Form>
              </div>
            )}
          </div>
        </div>
      </Modal>
    </>
  )
}

export default HomePage
