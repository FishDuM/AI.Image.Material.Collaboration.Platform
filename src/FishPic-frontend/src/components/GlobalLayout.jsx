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
  MessageOutlined,
  LockFilled,
  ToolOutlined,
  PictureOutlined,
  CommentOutlined,
  BellOutlined,
  ReloadOutlined,
  UpOutlined,
  PlusOutlined,
  DashboardOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import { logout } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { ThemeContext } from '../context/ThemeContext.jsx'
import { useAuthModal } from '../hooks/useAuthModal.js'
import { SettingsModal } from './shared/LoginModal.jsx'
import AuthModals from './shared/AuthModals.jsx'
import MobileBottomNav from './shared/MobileBottomNav.jsx'
import ErrorBoundary from './ErrorBoundary.jsx'
import './shared/MobileBottomNav.css'

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
    try { await logout() } catch { /* ignore */ }
    finally {
      authLogout()
      message.success('退出成功')
      navigate('/')
    }
  }, [authLogout, message, navigate])

  const handleScrollToTop = useCallback(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [])

  const handleRefresh = useCallback(() => {
    window.location.reload()
  }, [])

  const handleCreatePost = useCallback(() => {
    navigate('/community', { state: { openCreatePost: true } })
  }, [navigate])

  const handleLoginButtonClick = useCallback(() => {
    if (isMobile) {
      navigate('/mobile/login')
      return
    }
    if (location.pathname === '/') {
      authModal.openLogin()
    } else {
      navigate('/')
      setTimeout(() => authModal.openLogin(), 100)
    }
  }, [isMobile, location.pathname, navigate, authModal])

  const handleSidebarMenuClick = useCallback((path) => {
    navigate(path)
    setSidebarVisible(false)
  }, [navigate])

  const sidebarMenuItems = useMemo(() => {
    const items = [
      { key: '/', icon: <HomeOutlined />, label: '首页', onClick: () => handleSidebarMenuClick('/') },
      { key: '/community', icon: <MessageOutlined />, label: '社区广场', onClick: () => handleSidebarMenuClick('/community') },
      { key: '/private-space', icon: <LockFilled />, label: '私人空间', onClick: () => handleSidebarMenuClick('/private-space') },
      { key: '/team-space', icon: <TeamOutlined />, label: '团队空间', onClick: () => handleSidebarMenuClick('/team-space') },
    ]

    if (userInfo) {
      items.push({ key: '/ai-tools', icon: <RobotOutlined />, label: 'AI 工具', onClick: () => handleSidebarMenuClick('/ai-tools') })
      items.push(
        { key: '/profile', icon: <UserOutlined />, label: '个人中心', onClick: () => handleSidebarMenuClick('/profile') },
        { key: '/notifications', icon: <BellOutlined />, label: '通知', onClick: () => handleSidebarMenuClick('/notifications') },
        { key: '/logout', icon: <LogoutOutlined />, label: '退出登录', onClick: () => { handleLogout(); setSidebarVisible(false) } },
      )
    } else {
      items.push({ key: '/login', icon: <LoginOutlined />, label: '登录', onClick: () => { handleLoginButtonClick(); setSidebarVisible(false) } })
    }

    const hasAdminAccess = userInfo?.permissions?.includes('user:manage') || userInfo?.permissions?.includes('user:list') || false;
    if (hasAdminAccess) {
      items.push({ type: 'divider' })
      const adminChildren = [];

      if (userInfo?.permissions?.includes('user:list') || userInfo?.permissions?.includes('user:manage')) {
        adminChildren.push({ key: '/admin/users', icon: <UserOutlined />, label: '用户管理', onClick: () => handleSidebarMenuClick('/admin/users') });
      }
      if (userInfo?.permissions?.includes('picture:list') || userInfo?.permissions?.includes('picture:review')) {
        adminChildren.push({ key: '/admin/pictures', icon: <PictureOutlined />, label: '图片管理', onClick: () => handleSidebarMenuClick('/admin/pictures') });
      }
      if (userInfo?.permissions?.includes('comment:list') || userInfo?.permissions?.includes('comment:review')) {
        adminChildren.push({ key: '/admin/comments', icon: <CommentOutlined />, label: '评论审核', onClick: () => handleSidebarMenuClick('/admin/comments') });
      }
      if (userInfo?.permissions?.includes('post:list') || userInfo?.permissions?.includes('post:review')) {
        adminChildren.push({ key: '/admin/posts', icon: <MessageOutlined />, label: '帖子审核', onClick: () => handleSidebarMenuClick('/admin/posts') });
      }
      if (userInfo?.permissions?.includes('user:manage')) {
        adminChildren.push({ key: '/admin/dashboard', icon: <DashboardOutlined />, label: '数据概览', onClick: () => handleSidebarMenuClick('/admin/dashboard') });
      }
      if (userInfo?.permissions?.includes('space:list') || userInfo?.permissions?.includes('space:manage')) {
        adminChildren.push({ key: '/admin/spaces', icon: <AppstoreOutlined />, label: '空间管理', onClick: () => handleSidebarMenuClick('/admin/spaces') });
      }
      if (userInfo?.permissions?.includes('user:manage')) {
        adminChildren.push({ key: '/admin/audit-logs', icon: <FileTextOutlined />, label: '审计日志', onClick: () => handleSidebarMenuClick('/admin/audit-logs') });
      }
      if (userInfo?.permissions?.includes('ai:tasks') || userInfo?.permissions?.includes('ai:stats')) {
        adminChildren.push({ key: '/admin/ai', icon: <RobotOutlined />, label: 'AI 管理', onClick: () => handleSidebarMenuClick('/admin/ai') });
      }
      if (userInfo?.permissions?.includes('user:manage')) {
        adminChildren.push({ key: '/admin/system', icon: <ToolOutlined />, label: '系统管理', onClick: () => handleSidebarMenuClick('/admin/system') });
      }

      if (adminChildren.length > 0) {
        items.push({
          key: 'admin',
          icon: <SettingOutlined />,
          label: '管理页面',
          children: adminChildren,
        });
      }
    }

    return items
  }, [userInfo, handleSidebarMenuClick, handleLogout, handleLoginButtonClick])

  const userMenuItems = useMemo(() => [
    { key: 'profile', icon: <UserOutlined />, label: '个人中心', onClick: () => navigate('/profile') },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
  ], [navigate, handleLogout])

  const adminMenuItems = useMemo(() => {
    const items = [];

    if (userInfo?.permissions?.includes('user:list') || userInfo?.permissions?.includes('user:manage')) {
      items.push({ key: 'user-management', icon: <UserOutlined />, label: '用户管理', onClick: () => navigate('/admin/users') });
    }
    if (userInfo?.permissions?.includes('picture:list') || userInfo?.permissions?.includes('picture:review')) {
      items.push({ key: 'picture-management', icon: <PictureOutlined />, label: '图片管理', onClick: () => navigate('/admin/pictures') });
    }
    if (userInfo?.permissions?.includes('comment:list') || userInfo?.permissions?.includes('comment:review')) {
      items.push({ key: 'comment-management', icon: <CommentOutlined />, label: '评论审核', onClick: () => navigate('/admin/comments') });
    }
    if (userInfo?.permissions?.includes('post:list') || userInfo?.permissions?.includes('post:review')) {
      items.push({ key: 'post-management', icon: <MessageOutlined />, label: '帖子审核', onClick: () => navigate('/admin/posts') });
    }
    if (userInfo?.permissions?.includes('user:manage')) {
      items.push({ key: 'dashboard', icon: <DashboardOutlined />, label: '数据概览', onClick: () => navigate('/admin/dashboard') });
    }
    if (userInfo?.permissions?.includes('space:list') || userInfo?.permissions?.includes('space:manage')) {
      items.push({ key: 'space-management', icon: <AppstoreOutlined />, label: '空间管理', onClick: () => navigate('/admin/spaces') });
    }
    if (userInfo?.permissions?.includes('user:manage')) {
      items.push({ key: 'audit-log', icon: <FileTextOutlined />, label: '审计日志', onClick: () => navigate('/admin/audit-logs') });
    }
    if (userInfo?.permissions?.includes('ai:tasks') || userInfo?.permissions?.includes('ai:stats')) {
      items.push({ key: 'ai-management', icon: <RobotOutlined />, label: 'AI 管理', onClick: () => navigate('/admin/ai') });
    }
    if (userInfo?.permissions?.includes('user:manage')) {
      items.push({ key: 'system-management', icon: <ToolOutlined />, label: '系统管理', onClick: () => navigate('/admin/system') });
    }

    return items;
  }, [navigate, userInfo?.permissions])

  const navActiveClass = (path) => location.pathname === path ? ' nav-btn-active' : ''

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="header-content">
          <div className="logo-section">
            <Button type="text" size="large" className="mobile-menu-btn" onClick={() => setSidebarVisible(true)} icon={<MenuOutlined />} />
            <h1 className="logo-text" onClick={() => navigate('/')}>FishPics</h1>
            <Button type="text" size="large" icon={<HomeOutlined />} onClick={() => navigate('/')} className={`desktop-only${navActiveClass('/')}`}>首页</Button>
            <Button type="text" size="large" onClick={() => navigate('/community')} className={`desktop-only${navActiveClass('/community')}`}>社区广场</Button>
            <Button type="text" size="large" onClick={() => navigate('/private-space')} className={`desktop-only${navActiveClass('/private-space')}`}>私人空间</Button>
            <Button type="text" size="large" onClick={() => navigate('/team-space')} className={`desktop-only${navActiveClass('/team-space')}`}>团队空间</Button>
            {userInfo && (
              <Button type="text" size="large" icon={<RobotOutlined />} onClick={() => navigate('/ai-tools')} className={`desktop-only${navActiveClass('/ai-tools')}`}>AI 工具</Button>
            )}
            {(userInfo?.permissions?.includes('user:manage') || userInfo?.permissions?.includes('user:list') || userInfo?.permissions?.includes('picture:list') || userInfo?.permissions?.includes('comment:list') || userInfo?.permissions?.includes('post:list') || userInfo?.permissions?.includes('space:list')) && (
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
            {userInfo && (
              <Button type="text" size="large" className={`notification-btn${location.pathname === '/notifications' ? ' notification-btn-active' : ''}`} icon={<BellOutlined />} onClick={() => navigate('/notifications')} />
            )}
            <Button type="text" size="large" className="theme-toggle-btn" onClick={toggleTheme} icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />} />
            <Button type="text" size="large" className="settings-btn" onClick={() => setSettingsOpen(true)} icon={<ToolOutlined />} />
          </div>
        </div>
      </header>

      <Drawer title="导航菜单" placement="left" onClose={() => setSidebarVisible(false)} open={sidebarVisible} className="mobile-sidebar" closeIcon={<CloseOutlined />} size={280}>
        <Menu mode="vertical" selectedKeys={[location.pathname]} className="sidebar-menu" items={sidebarMenuItems} triggerSubMenuAction="click" />
      </Drawer>

      <ErrorBoundary onReset={() => navigate('/')}>{children}</ErrorBoundary>

      <div className="float-actions">
        {showBackToTop && (
          <Button
            type="text"
            shape="circle"
            icon={<UpOutlined />}
            onClick={handleScrollToTop}
            className="float-action-btn float-back-top"
            aria-label="返回顶部"
          />
        )}
        {showBackToTop && location.pathname === '/community' && (
          <Button
            type="text"
            shape="circle"
            icon={<PlusOutlined />}
            onClick={handleCreatePost}
            className="float-action-btn float-create-post"
            aria-label="发帖"
          />
        )}
        {showBackToTop && (
          <Button
            type="text"
            shape="circle"
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
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
