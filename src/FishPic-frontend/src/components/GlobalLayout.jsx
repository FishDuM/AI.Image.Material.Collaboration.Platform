import { useContext, useState, useMemo } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { App as AntApp, Button, Avatar, Dropdown, Modal, Form, Input, Card, Drawer, Menu } from 'antd'
import { 
  SettingOutlined, 
  HomeOutlined, 
  SunOutlined, 
  MoonOutlined, 
  UserOutlined,
  LogoutOutlined,
  LoginOutlined,
  LockOutlined,
  ScanOutlined,
  MenuOutlined,
  CloseOutlined,
  TeamOutlined,
  AppstoreOutlined,
  RobotOutlined,
  MessageOutlined,
  LockFilled,
  ToolOutlined,
  GithubOutlined,
  QqOutlined,
  GlobalOutlined,
  PlaySquareOutlined,
  BellOutlined
} from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import { getLoginCheckCode, login, getRegisterCheckCode, register } from '../api'
import { ThemeContext } from '../main.jsx'

function GlobalLayout({ children }) {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { isDarkMode, toggleTheme } = useContext(ThemeContext)
  const { userInfo, isAuthenticated, login: authLogin, logout: authLogout } = useContext(AuthContext)
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false)
  const [loginForm] = Form.useForm()
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginCheckCodeUrl, setLoginCheckCodeUrl] = useState('')
  const [loginKey, setLoginKey] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [sidebarVisible, setSidebarVisible] = useState(false)
  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false)
  const [isRegisterModalOpen, setIsRegisterModalOpen] = useState(false)
  const [registerForm] = Form.useForm()
  const [registerLoading, setRegisterLoading] = useState(false)
  const [registerCheckCodeUrl, setRegisterCheckCodeUrl] = useState('')
  const [registerKey, setRegisterKey] = useState('')

  const handleLogout = () => {
    authLogout()
    message.success('已退出登录')
    navigate('/')
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
          message.error('获取验证码失败')
        }
      } else {
        message.error('获取验证码失败')
      }
    } catch (error) {
      message.error('获取验证码失败')
    }
  }

  const showLoginModal = () => {
    setIsLoginModalOpen(true)
    fetchLoginCheckCode()
  }

  const handleLoginCancel = () => {
    setIsLoginModalOpen(false)
    loginForm.resetFields()
    setLoginCheckCodeUrl('')
    setLoginKey('')
    setAgreed(false)
  }

  const showSettingsModal = () => {
    setIsSettingsModalOpen(true)
  }

  const handleSettingsCancel = () => {
    setIsSettingsModalOpen(false)
  }

  const handleRefreshLoginCode = () => {
    fetchLoginCheckCode()
    loginForm.setFieldValue('checkCode', '')
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
          message.error('获取验证码失败')
        }
      } else {
        message.error('获取验证码失败')
      }
    } catch (error) {
      message.error('获取验证码失败')
    }
  }

  const showRegisterModal = () => {
    setIsLoginModalOpen(false)
    setIsRegisterModalOpen(true)
    fetchRegisterCheckCode()
  }

  const showLoginFromRegister = () => {
    setIsRegisterModalOpen(false)
    setIsLoginModalOpen(true)
    fetchLoginCheckCode()
  }

  const handleRegisterCancel = () => {
    setIsRegisterModalOpen(false)
    registerForm.resetFields()
    setRegisterCheckCodeUrl('')
    setRegisterKey('')
  }

  const handleRefreshRegisterCode = () => {
    fetchRegisterCheckCode()
    registerForm.setFieldValue('checkCode', '')
  }

  const handleRegisterFinish = async (values) => {
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
        username: values.username,
        password: values.password,
        checkPassword: values.checkPassword,
        checkCode: values.checkCode,
        captchaKey: registerKey
      }
      await register(registerData)
      message.success('注册成功，请登录')
      handleRegisterCancel()
      showLoginModal()
    } catch (error) {
      message.error(error.message || '注册失败，请重试')
      fetchRegisterCheckCode()
      registerForm.setFieldValue('checkCode', '')
    } finally {
      setRegisterLoading(false)
    }
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
      authLogin(result)
      message.success('登录成功')
      handleLoginCancel()
    } catch (error) {
      message.error(error.message || '登录失败，请重试')
      fetchLoginCheckCode()
      loginForm.setFieldValue('checkCode', '')
    } finally {
      setLoginLoading(false)
    }
  }

  const handleLoginButtonClick = () => {
    if (location.pathname === '/') {
      showLoginModal()
    } else {
      navigate('/')
      setTimeout(() => {
        showLoginModal()
      }, 100)
    }
  }

  const showSidebar = () => {
    setSidebarVisible(true)
  }

  const closeSidebar = () => {
    setSidebarVisible(false)
  }

  const handleSidebarMenuClick = (path) => {
    navigate(path)
    setSidebarVisible(false)
  }

  const sidebarMenuItems = useMemo(() => {
    const items = [
      {
        key: '/',
        icon: <HomeOutlined />,
        label: '首页',
        onClick: () => handleSidebarMenuClick('/'),
      },
      {
        key: '/community',
        icon: <MessageOutlined />,
        label: '社区广场',
        onClick: () => handleSidebarMenuClick('/community'),
      },
      {
        key: '/private-space',
        icon: <LockFilled />,
        label: '私人空间',
        onClick: () => handleSidebarMenuClick('/private-space'),
      },
      {
        key: '/team-space',
        icon: <TeamOutlined />,
        label: '团队空间',
        onClick: () => handleSidebarMenuClick('/team-space'),
      },
    ]

    if (userInfo) {
      items.push(
        {
          key: '/profile',
          icon: <UserOutlined />,
          label: '个人中心',
          onClick: () => handleSidebarMenuClick('/profile'),
        },
        {
          key: '/logout',
          icon: <LogoutOutlined />,
          label: '退出登录',
          onClick: () => {
            handleLogout()
            closeSidebar()
          },
        }
      )
    } else {
      items.push({
        key: '/login',
        icon: <LoginOutlined />,
        label: '登录',
        onClick: () => {
          handleLoginButtonClick()
          closeSidebar()
        },
      })
    }

    if (userInfo?.role === 'admin') {
      items.push({
        type: 'divider',
      })
      items.push({
        key: 'admin',
        icon: <SettingOutlined />,
        label: '系统管理',
        children: [
          {
            key: '/admin/users',
            icon: <UserOutlined />,
            label: '用户管理',
            onClick: () => handleSidebarMenuClick('/admin/users'),
          },
          {
            key: '/admin/spaces',
            icon: <AppstoreOutlined />,
            label: '空间管理',
            onClick: () => handleSidebarMenuClick('/admin/spaces'),
          },
          {
            key: '/admin/teams',
            icon: <TeamOutlined />,
            label: '团队管理',
            onClick: () => handleSidebarMenuClick('/admin/teams'),
          },
          {
            key: '/admin/ai',
            icon: <RobotOutlined />,
            label: 'AI 管理',
            onClick: () => handleSidebarMenuClick('/admin/ai'),
          },
        ],
      })
    }

    return items
  }, [userInfo, location.pathname])

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ]

  const systemManagementMenuItems = [
    {
      key: 'user-management',
      icon: <UserOutlined />,
      label: '用户管理',
      onClick: () => navigate('/admin/users'),
    },
      {
          key: 'space-management',
          icon: <AppstoreOutlined />,
          label: '空间管理',
          onClick: () => navigate('/admin/spaces'),
      },
      {
          key: 'team-management',
          icon: <TeamOutlined />,
          label: '团队管理',
          onClick: () => navigate('/admin/teams'),
      },
      {
          key: 'ai-management',
          icon: <RobotOutlined />,
          label: 'AI 管理',
          onClick: () => navigate('/admin/ai'),
      },
  ]

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="header-content">
          <div className="logo-section">
            <Button
              type="text"
              size="large"
              className="mobile-menu-btn"
              onClick={showSidebar}
              icon={<MenuOutlined />}
            />
            <h1 className="logo-text" onClick={() => navigate('/')}>FishPics</h1>
            <Button
              type="text"
              size="large"
              icon={<HomeOutlined />}
              onClick={() => navigate('/')}
              className={`desktop-only${location.pathname === '/' ? ' nav-btn-active' : ''}`}
            >
              首页
            </Button>
            <Button
              type="text"
              size="large"
              onClick={() => navigate('/community')}
              className={`desktop-only${location.pathname === '/community' ? ' nav-btn-active' : ''}`}
            >
              社区广场
            </Button>
            <Button
              type="text"
              size="large"
              onClick={() => navigate('/private-space')}
              className={`desktop-only${location.pathname === '/private-space' ? ' nav-btn-active' : ''}`}
            >
              私人空间
            </Button>
            <Button
              type="text"
              size="large"
              onClick={() => navigate('/team-space')}
              className={`desktop-only${location.pathname === '/team-space' ? ' nav-btn-active' : ''}`}
            >
              团队空间
            </Button>
            {userInfo?.role === 'admin' && (
              <Dropdown menu={{ items: systemManagementMenuItems }} placement="bottomLeft" className="desktop-only">
                <Button
                  type="text"
                  size="large"
                  className="system-management-btn desktop-only"
                >
                  <SettingOutlined />
                  <span>系统管理</span>
                </Button>
              </Dropdown>
            )}
          </div>
          <div className="header-actions">
            {userInfo ? (
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" className="desktop-only">
                <div className="user-info">
                  <Avatar 
                    size={32} 
                    src={userInfo.avatar}
                    style={{ 
                      backgroundColor: userInfo.avatar ? 'transparent' : 'var(--accent)',
                    }}
                  >
                    {!userInfo.avatar && (userInfo.nickname || userInfo.username)?.charAt(0)?.toUpperCase()}
                  </Avatar>
                  <span className="user-name">{userInfo.nickname || userInfo.username}</span>
                </div>
              </Dropdown>
            ) : (
              <Button
                type="primary"
                className="login-btn desktop-only"
                onClick={handleLoginButtonClick}
                icon={<LoginOutlined />}
              >
                登录
              </Button>
            )}
            {userInfo && (
              <Button
                type="text"
                size="large"
                className={`notification-btn${location.pathname === '/notifications' ? ' notification-btn-active' : ''}`}
                icon={<BellOutlined />}
                onClick={() => navigate('/notifications')}
              />
            )}
            <Button
              type="text"
              size="large"
              className="theme-toggle-btn"
              onClick={toggleTheme}
              icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />}
            />
            <Button
              type="text"
              size="large"
              className="settings-btn"
              onClick={showSettingsModal}
              icon={<ToolOutlined />}
            />
          </div>
        </div>
      </header>

      <Drawer
        title="导航菜单"
        placement="left"
        onClose={closeSidebar}
        open={sidebarVisible}
        className="mobile-sidebar"
        closeIcon={<CloseOutlined />}
        size={280}
      >
        <Menu
          mode="vertical"
          selectedKeys={[location.pathname]}
          className="sidebar-menu"
          items={sidebarMenuItems}
          triggerSubMenuAction="click"
        />
      </Drawer>

      {children}

      <Modal
        open={isLoginModalOpen}
        onCancel={handleLoginCancel}
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
                    { required: true, message: '请输入账号' },
                    { min: 6, message: '账号至少 6 个字符' },
                  ]}
                >
                  <Input
                    prefix={<UserOutlined className="input-icon" />}
                    placeholder="请输入账号"
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
                <div className="form-footer">
                  <span>还没有账号？</span>
                  <Button type="link" onClick={showRegisterModal} className="switch-form-btn">
                    立即注册
                  </Button>
                </div>
              </Form>
            </div>
          </div>
        </div>
      </Modal>

      <Modal
        open={isRegisterModalOpen}
        onCancel={handleRegisterCancel}
        footer={null}
        centered
        className="xhs-modal"
        destroyOnHidden
        width={800}
      >
        <div className="xhs-modal-content">
          <div className="xhs-left-panel">
            <div className="scan-hint">加入我们，开始创作</div>
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
            <div className="form-container">
              <h2 className="form-title">账号注册</h2>
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
                    { required: true, message: '请输入账号' },
                    { min: 6, message: '账号至少 6 个字符' },
                  ]}
                >
                  <Input
                    prefix={<UserOutlined className="input-icon" />}
                    placeholder="请输入账号"
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
                    { required: true, message: '请再次输入密码' },
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
                    placeholder="请再次输入密码"
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
                      {registerCheckCodeUrl && (
                        <img src={registerCheckCodeUrl} alt="验证码" className="check-code-img-btn" />
                      )}
                    </Button>
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
                <div className="form-footer">
                  <span>已有账号？</span>
                  <Button type="link" onClick={showLoginFromRegister} className="switch-form-btn">
                    立即登录
                  </Button>
                </div>
              </Form>
            </div>
          </div>
        </div>
      </Modal>

      <Modal
        open={isSettingsModalOpen}
        onCancel={handleSettingsCancel}
        footer={null}
        centered
        className="settings-modal"
        destroyOnHidden
        width={400}
      >
        <div className="settings-modal-content">
          <p className="dev-message">功能正在开发中，敬请期待 ～</p>
          <p className="dev-by">— By Fish</p>
          <div className="social-links">
            <a
              href="https://github.com/FishDuM"
              target="_blank"
              rel="noopener noreferrer"
              className="social-link-item"
              title="GitHub"
            >
              <GithubOutlined />
            </a>
            <a
              href="https://space.bilibili.com/386312184"
              target="_blank"
              rel="noopener noreferrer"
              className="social-link-item"
              title="Bilibili"
            >
              <PlaySquareOutlined />
            </a>
            <a
              href="https://qm.qq.com/q/bH26HucOhW"
              target="_blank"
              rel="noopener noreferrer"
              className="social-link-item"
              title="QQ"
            >
              <QqOutlined />
            </a>
            <a
              href="https://fishdum.github.io/"
              target="_blank"
              rel="noopener noreferrer"
              className="social-link-item"
              title="我的个人网站"
            >
              <GlobalOutlined />
            </a>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export default GlobalLayout
