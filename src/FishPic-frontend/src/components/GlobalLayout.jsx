import { useContext, useState, useMemo, useCallback, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { App as AntApp, Button, Avatar, Dropdown, Drawer, Menu, Form } from 'antd'
import {
  SettingOutlined,
  HomeOutlined,
  SunOutlined,
  MoonOutlined,
  UserOutlined,
  LogoutOutlined,
  LoginOutlined,
  MenuOutlined,
  CloseOutlined,
  TeamOutlined,
  AppstoreOutlined,
  RobotOutlined,
  LockFilled,
  ToolOutlined,
  PictureOutlined,
  ReloadOutlined,
  UpOutlined,
  DashboardOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import { useIsMobile } from '../hooks/useIsMobile'
import { ThemeContext } from '../context/ThemeContext.jsx'
import { useAuthModal } from '../hooks/useAuthModal.js'
import { SettingsModal } from './shared/LoginModal.jsx'
import AuthModals from './shared/AuthModals.jsx'
import MobileBottomNav from './shared/MobileBottomNav.jsx'
import './shared/MobileBottomNav.css'

const ADMIN_MENU_CONFIG = [
  { permission: 'system:user:manage', path: '/admin/users', icon: <UserOutlined />, label: '用户管理' },
  { permission: 'system:log:manage', path: '/admin/pictures', icon: <PictureOutlined />, label: '图片管理' },
  { permission: 'system:log:manage', path: '/admin/dashboard', icon: <DashboardOutlined />, label: '数据概览' },
  { permission: 'system:team:manage', path: '/admin/spaces', icon: <AppstoreOutlined />, label: '空间管理' },
  { permission: 'system:log:manage', path: '/admin/audit-logs', icon: <FileTextOutlined />, label: '审计日志' },
  { permission: 'system:ai:manage', path: '/admin/ai', icon: <RobotOutlined />, label: 'AI 管理' },
  { permission: 'system:config', path: '/admin/system', icon: <ToolOutlined />, label: '系统管理' },
]

function GlobalLayout({ children }) {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { isDarkMode, toggleTheme } = useContext(ThemeContext)
  const auth = useContext(AuthContext)
  const userInfo = auth?.userInfo
  const authLogout = auth?.logout
  const isMobile = useIsMobile()
  const [sidebarVisible, setSidebarVisible] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()
  const authModal = useAuthModal()
  const [showBackToTop, setShowBackToTop] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      const scrollY = window.scrollY || document.documentElement.scrollTop
      setShowBackToTop(scrollY > 100)
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const handleLogout = useCallback(async () => {
    if (authLogout) {
      await authLogout()
    }
    message.success('退出成功')
    navigate('/')
  }, [authLogout, message, navigate])

  const handleLoginButtonClick = useCallback(() => {
    if (isMobile) {
      navigate('/mobile/login')
      return
    }
    if (location.pathname === '/') {
      authModal.openLogin()
    } else {
      navigate('/', { state: { showLogin: true } })
    }
  }, [isMobile, location.pathname, navigate, authModal])

  const handleSidebarMenuClick = useCallback((path) => {
    navigate(path)
    setSidebarVisible(false)
  }, [navigate])

  const adminMenuConfig = useMemo(() => {
    const permissions = userInfo?.permissions || []
    return ADMIN_MENU_CONFIG.filter((item) => permissions.includes(item.permission))
  }, [userInfo?.permissions])

  const sidebarMenuItems = useMemo(() => {
    const items = [
      { key: '/', icon: <HomeOutlined />, label: '首页', onClick: () => handleSidebarMenuClick('/') },
      { key: '/private-space', icon: <LockFilled />, label: '私人空间', onClick: () => handleSidebarMenuClick('/private-space') },
      { key: '/team-space', icon: <TeamOutlined />, label: '团队空间', onClick: () => handleSidebarMenuClick('/team-space') },
    ]

    if (userInfo) {
      items.push({ key: '/ai-tools', icon: <RobotOutlined />, label: 'AI 工具', onClick: () => handleSidebarMenuClick('/ai-tools') })
      items.push(
        { key: '/profile', icon: <UserOutlined />, label: '个人中心', onClick: () => handleSidebarMenuClick('/profile') },
        { key: '/logout', icon: <LogoutOutlined />, label: '退出登录', onClick: () => { handleLogout(); setSidebarVisible(false) } },
      )
    } else {
      items.push({ key: '/login', icon: <LoginOutlined />, label: '登录', onClick: () => { handleLoginButtonClick(); setSidebarVisible(false) } })
    }

    if (adminMenuConfig.length > 0) {
      items.push({ type: 'divider' })
      items.push({
        key: 'admin',
        icon: <SettingOutlined />,
        label: '管理页面',
        children: adminMenuConfig.map((item) => ({
          key: item.path,
          icon: item.icon,
          label: item.label,
          onClick: () => handleSidebarMenuClick(item.path),
        })),
      })
    }

    return items
  }, [userInfo, adminMenuConfig, handleSidebarMenuClick, handleLogout, handleLoginButtonClick])

  const userMenuItems = [
    { key: 'profile', icon: <UserOutlined />, label: '个人中心', onClick: () => navigate('/profile') },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
  ]

  const adminMenuItems = adminMenuConfig.map((item) => ({
    key: item.path,
    icon: item.icon,
    label: item.label,
    onClick: () => navigate(item.path),
  }))

  const navActiveClass = (path) => location.pathname === path ? ' nav-btn-active' : ''

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="header-content">
          <div className="logo-section">
            <Button type="text" size="large" className="mobile-menu-btn" onClick={() => setSidebarVisible(true)} icon={<MenuOutlined />} />
            <h1 className="logo-text" onClick={() => navigate('/')}>FishPics</h1>
            <Button type="text" size="large" icon={<HomeOutlined />} onClick={() => navigate('/')} className={`desktop-only${navActiveClass('/')}`}>首页</Button>
            <Button type="text" size="large" onClick={() => navigate('/private-space')} className={`desktop-only${navActiveClass('/private-space')}`}>私人空间</Button>
            <Button type="text" size="large" onClick={() => navigate('/team-space')} className={`desktop-only${navActiveClass('/team-space')}`}>团队空间</Button>
            {userInfo && (
              <Button type="text" size="large" icon={<RobotOutlined />} onClick={() => navigate('/ai-tools')} className={`desktop-only${navActiveClass('/ai-tools')}`}>AI 工具</Button>
            )}
            {adminMenuConfig.length > 0 && (
              <Dropdown menu={{ items: adminMenuItems }} placement="bottomLeft" className="desktop-only">
                <Button type="text" size="large" className="system-management-btn desktop-only"><SettingOutlined /><span>管理页面</span></Button>
              </Dropdown>
            )}
          </div>
          <div className="header-actions">
            {userInfo ? (
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" className="desktop-only">
                <div className="user-info">
                  <Avatar size={32} src={userInfo.avatar} style={{ backgroundColor: userInfo.avatar ? 'transparent' : 'var(--accent)' }}>
                    {!userInfo.avatar && (userInfo.nickname || userInfo.username)?.charAt(0)?.toUpperCase()}
                  </Avatar>
                  <span className="user-name">{userInfo.nickname || userInfo.username}</span>
                </div>
              </Dropdown>
            ) : (
              <Button type="primary" className="login-btn desktop-only" onClick={handleLoginButtonClick} icon={<LoginOutlined />}>登录</Button>
            )}
            <Button type="text" size="large" className="theme-toggle-btn" onClick={toggleTheme} icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />} />
            <Button type="text" size="large" className="settings-btn" onClick={() => setSettingsOpen(true)} icon={<ToolOutlined />} />
          </div>
        </div>
      </header>

      <Drawer title="导航菜单" placement="left" onClose={() => setSidebarVisible(false)} open={sidebarVisible} className="mobile-sidebar" closeIcon={<CloseOutlined />} size={280}>
        <Menu mode="vertical" selectedKeys={[location.pathname]} className="sidebar-menu" items={sidebarMenuItems} triggerSubMenuAction="click" />
      </Drawer>

      {children}

      <div className="float-actions">
        {showBackToTop && (
          <Button
            type="text"
            shape="circle"
            icon={<UpOutlined />}
            onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
            className="float-action-btn float-back-top"
            aria-label="返回顶部"
          />
        )}
        {showBackToTop && (
          <Button
            type="text"
            shape="circle"
            icon={<ReloadOutlined />}
            onClick={() => window.location.reload()}
            className="float-action-btn float-refresh"
            aria-label="刷新"
          />
        )}
      </div>

      <AuthModals authModal={authModal} loginForm={loginForm} registerForm={registerForm} />
      <SettingsModal open={settingsOpen} onCancel={() => setSettingsOpen(false)} />
      <MobileBottomNav />
    </div>
  )
}

export default GlobalLayout
