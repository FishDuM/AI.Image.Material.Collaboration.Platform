import { useEffect, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp } from 'antd'
import { AuthContext } from '../context/AuthContext'

/**
 * 管理页面权限守卫 hook
 * @param {string} permission - 需要的权限码
 */
export function useAdminGuard(permission) {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo, authLoading } = useContext(AuthContext)

  const hasPermission = userInfo?.permissions?.includes(permission)

  useEffect(() => {
    if (authLoading) return
    if (!userInfo || !hasPermission) {
      message.error('无权访问，正在跳转...')
      setTimeout(() => navigate('/404', { replace: true }), 500)
    }
  }, [userInfo, authLoading, navigate, message, hasPermission])

  return { hasPermission, userInfo, authLoading }
}
