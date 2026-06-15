import { useContext, useEffect, useRef } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin, message } from 'antd'
import { AuthContext } from '../context/AuthContext'

/**
 * 路由守卫组件
 * 支持 permission 属性：要求用户拥有指定权限码才可访问
 */
function ProtectedRoute({ children, requireAdmin = false, permission }) {
  const auth = useContext(AuthContext)
  const location = useLocation()
  const hasPrompted = useRef(false)

  useEffect(() => {
    // 监听认证过期事件（非主动退出），显示提示
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
    return <Navigate to="/" replace state={{ from: location }} />
  }

  if (requireAdmin) {
    const perms = auth.userInfo?.permissions || []
    if (!perms.includes('system:user:manage')) {
      return <Navigate to="/" replace />
    }
  }

  // 按权限码控制
  if (permission) {
    const perms = auth.userInfo?.permissions || []
    const required = Array.isArray(permission) ? permission : [permission]
    const hasPermission = required.some(p => perms.includes(p))
    if (!hasPermission) {
      return <Navigate to="/" replace />
    }
  }

  return children
}

export default ProtectedRoute
