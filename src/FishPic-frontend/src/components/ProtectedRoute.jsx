import { useContext } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { AuthContext } from '../context/AuthContext'

function ProtectedRoute({ children, requireAdmin = false }) {
  const auth = useContext(AuthContext)
  const location = useLocation()

  if (!auth || !auth.isAuthenticated) {
    return <Navigate to="/" replace state={{ from: location }} />
  }

  if (requireAdmin && auth.userInfo?.role !== 'admin') {
    return <Navigate to="/404" replace />
  }

  return children
}

export default ProtectedRoute
