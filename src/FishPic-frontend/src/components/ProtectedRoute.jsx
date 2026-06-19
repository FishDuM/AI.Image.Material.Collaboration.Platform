import { useContext, useEffect, useRef } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin, message } from 'antd'
import { AuthContext } from '../context/AuthContext'
import { useIsMobile } from '../hooks/useIsMobile'

function ProtectedRoute({ children, requireAdmin = false, permission }) {
  const auth = useContext(AuthContext)
  const location = useLocation()
  const isMobile = useIsMobile()
  const hasPrompted = useRef(false)

  useEffect(() => {
    const handleExpired = () => {
      if (!hasPrompted.current) {
        hasPrompted.current = true
        message.info('登录已过期，请重新登录')
      }
    }
    window.addEventListener('auth:expired', handleExpired)
    return () => window.removeEventListener('auth:expired', handleExpired)
  }, [])

  if (auth?.authLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!auth || !auth.isAuthenticated) {
    const redirect = `${location.pathname}${location.search || ''}`
    if (isMobile) {
      return <Navigate to={`/mobile/login?redirect=${encodeURIComponent(redirect)}`} replace />
    }
    return <Navigate to="/" replace state={{ showLogin: true, from: location }} />
  }

  if (requireAdmin) {
    const perms = auth.userInfo?.permissions || []
    if (!perms.includes('system:user:manage')) {
      return <Navigate to="/404" replace />
    }
  }

  if (permission) {
    const perms = auth.userInfo?.permissions || []
    const required = Array.isArray(permission) ? permission : [permission]
    const hasPermission = required.some(p => perms.includes(p))
    if (!hasPermission) {
      return <Navigate to="/404" replace />
    }
  }

  return children
}

export default ProtectedRoute
