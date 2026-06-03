import { createContext, useState, useEffect, useCallback } from 'react'
import { getUserInfo, saveUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'
import { getUser, logout as logoutApi } from '../api'
import { createConnection, destroyConnection } from '../hooks/useWebSocket'

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [userInfo, setUserInfo] = useState(() => {
    const token = getToken()
    return token ? getUserInfo() : null
  })
  const [isAuthenticated, setIsAuthenticated] = useState(() => {
    const token = getToken()
    return !!(token && getUserInfo())
  })
  const [authLoading, setAuthLoading] = useState(() => !!getToken())
  useCallback(() => {
    const token = getToken()
    if (token) {
      createConnection()
    }
  }, []);
  const cleanupWs = useCallback(() => {
    destroyConnection()
  }, [])

  useEffect(() => {
    const handleAuthExpired = () => {
      cleanupWs()
      setUserInfo(null)
      setIsAuthenticated(false)
      setAuthLoading(false)
    }
    window.addEventListener('auth:expired', handleAuthExpired)
    return () => window.removeEventListener('auth:expired', handleAuthExpired)
  }, [cleanupWs])

  useEffect(() => {
    let ignore = false

    const verifyLoginState = async () => {
      const token = getToken()
      if (!token) {
        clearAuth()
        setUserInfo(null)
        setIsAuthenticated(false)
        setAuthLoading(false)
        return
      }

      setAuthLoading(true)
      try {
        const latestUserInfo = await getUser({ noDedup: true })
        if (ignore) return
        saveUserInfo(latestUserInfo)
        setUserInfo(latestUserInfo)
        setIsAuthenticated(true)
        // Token 有效，建立 WebSocket 连接
        createConnection()
      } catch {
        if (ignore) return
        clearAuth()
        setUserInfo(null)
        setIsAuthenticated(false)
      } finally {
        if (!ignore) {
          setAuthLoading(false)
        }
      }
    }

    verifyLoginState()

    return () => {
      ignore = true
    }
  }, [])

  // 定期刷新权限（每5分钟）
  useEffect(() => {
    if (!isAuthenticated) return

    const interval = setInterval(async () => {
      try {
        const latestUserInfo = await getUser({ noDedup: true })
        saveUserInfo(latestUserInfo)
        setUserInfo(latestUserInfo)
      } catch {
        // 静默失败，不影响用户体验
      }
    }, 5 * 60 * 1000) // 5分钟

    return () => clearInterval(interval)
  }, [isAuthenticated])

  const login = useCallback((data) => {
    if (data.token) {
      saveToken(data.token)
      const { token: _token, ...userData } = data
      saveUserInfo(userData)
      setUserInfo(userData)
    } else {
      saveUserInfo(data)
      setUserInfo(data)
    }
    setIsAuthenticated(true)
    createConnection()
  }, [])

  const logout = useCallback(async () => {
    try { await logoutApi() } catch { /* 忽略，确保本地清理 */ }
    destroyConnection()
    clearAuth()
    setUserInfo(null)
    setIsAuthenticated(false)
    setAuthLoading(false)
  }, [])

  const updateUserInfo = useCallback((updater) => {
    setUserInfo(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      if (next) {
        saveUserInfo(next)
        if (next.token) {
          saveToken(next.token)
        }
      }
      return next
    })
  }, [])

  return (
    <AuthContext.Provider value={{ userInfo, isAuthenticated, authLoading, login, logout, updateUserInfo }}>
      {children}
    </AuthContext.Provider>
  )
}
