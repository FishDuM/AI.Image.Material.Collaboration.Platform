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

  if (auth?.authLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!auth || !auth.isAuthenticated) {
    if (!hasPrompted.current) {
      hasPrompted.current = true
      message.info('请先登录')
    }
    return <Navigate to="/" replace state={{ from: location }} />
  }

  // 兼容旧版 requireAdmin（已废弃，建议使用 permission 属性）
  if (requireAdmin) {
    const perms = auth.userInfo?.permissions || []
    if (!perms.includes('system:user:manage')) {
      return <Navigate to="/" replace />
    }
  }

  // 新版：按权限码控制
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
