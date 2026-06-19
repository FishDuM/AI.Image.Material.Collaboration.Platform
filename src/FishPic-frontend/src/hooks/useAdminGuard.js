import { useEffect, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp } from 'antd'
import { AuthContext } from '../context/AuthContext'

export function useAdminGuard(permission) {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo, authLoading } = useContext(AuthContext)

  const hasPermission = userInfo?.permissions?.includes(permission)

  useEffect(() => {
    if (authLoading) return
    if (!userInfo || !hasPermission) {
      message.error('无权访问，正在跳转...')
      setTimeout(() => navigate('/', { replace: true }), 500)
    }
  }, [userInfo, authLoading, navigate, message, hasPermission])

  return { hasPermission, userInfo, authLoading }
}
