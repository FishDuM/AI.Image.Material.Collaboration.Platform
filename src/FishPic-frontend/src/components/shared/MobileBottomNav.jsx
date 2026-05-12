import { useNavigate, useLocation } from 'react-router-dom'
import { HomeOutlined, AppstoreOutlined, LockOutlined, UserOutlined, TeamOutlined } from '@ant-design/icons'
import { useContext } from 'react'
import { AuthContext } from '../../context/AuthContext.jsx'

const NAV_ITEMS = [
  { key: '/', icon: <HomeOutlined />, label: '首页' },
  { key: '/community', icon: <AppstoreOutlined />, label: '社区' },
  { key: '/private-space', icon: <LockOutlined />, label: '私人' },
  { key: '/team-space', icon: <TeamOutlined />, label: '团队' },
  { key: '/profile', icon: <UserOutlined />, label: '我的' },
]

function MobileBottomNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const auth = useContext(AuthContext)
  const userInfo = auth?.userInfo

  const mobileRoutes = ['/mobile/login', '/mobile/register', '/mobile/post/detail', '/mobile/picture/edit', '/mobile/profile/edit']
  if (mobileRoutes.some(r => location.pathname.startsWith(r))) return null
  if (!userInfo && location.pathname !== '/') return null

  const isActive = (key) => {
    if (key === '/') return location.pathname === '/'
    return location.pathname.startsWith(key)
  }

  const handleClick = (item) => {
    if (!userInfo && item.key !== '/') {
      navigate('/mobile/login')
      return
    }
    navigate(item.key)
  }

  return (
    <nav className="mobile-bottom-nav">
      {NAV_ITEMS.map((item) => (
        <button
          key={item.key}
          className={`mobile-bottom-nav-item${isActive(item.key) ? ' active' : ''}${item.isAction ? ' action' : ''}`}
          onClick={() => handleClick(item)}
        >
          <span className="mobile-bottom-nav-icon">{item.icon}</span>
          <span className="mobile-bottom-nav-label">{item.label}</span>
        </button>
      ))}
    </nav>
  )
}

export default MobileBottomNav
