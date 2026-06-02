import { useContext, useEffect, useRef } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin, message } from 'antd'
import { AuthContext } from '../context/AuthContext'

function ProtectedRoute({ children, requireAdmin = false }) {
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

  if (requireAdmin && auth.userInfo?.role !== 'admin') {
    return <Navigate to="/" replace />
  }

  return children
}

export default ProtectedRoute
