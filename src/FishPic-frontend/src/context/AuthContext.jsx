import { createContext, useState, useEffect, useCallback, useRef } from 'react'
import { getUserInfo, saveUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'
import { getUser, logout as logoutApi } from '../api'

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

  // 用于防止旧 verifyLoginState 请求覆盖新登录状态
  const tokenSnapshotRef = useRef(null)

  useEffect(() => {
    const handleAuthExpired = () => {
      setUserInfo(null)
      setIsAuthenticated(false)
      setAuthLoading(false)
    }
    window.addEventListener('auth:expired', handleAuthExpired)
    return () => window.removeEventListener('auth:expired', handleAuthExpired)
  }, [])

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
      // 记录本次验证开始时的 token，用于 catch 中判断是否被新登录覆盖
      tokenSnapshotRef.current = token

      setAuthLoading(true)
      try {
        const latestUserInfo = await getUser({ noDedup: true })
        if (ignore) return
        saveUserInfo(latestUserInfo)
        setUserInfo(latestUserInfo)
        setIsAuthenticated(true)
      } catch (error) {
        if (ignore) return
        // 如果在请求期间用户已用新 token 登录，不清理新状态
        if (getToken() !== tokenSnapshotRef.current) return
        // 仅在明确的认证失败时清理登录态，网络抖动/500 等不清除
        const status = error?.response?.status
        const code = error?.response?.data?.code
        const isAuthError = status === 401 || code === 40005 || code === 40002
        if (isAuthError) {
          clearAuth()
          setUserInfo(null)
          setIsAuthenticated(false)
        }
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
    // 更新 token 快照，使进行中的旧 verifyLoginState 请求不再清理状态
    tokenSnapshotRef.current = getToken()
    setIsAuthenticated(true)
    setAuthLoading(false)
  }, [])

  const logout = useCallback(async () => {
    try { await logoutApi() } catch { /* 忽略，确保本地清理 */ }
    clearAuth()
    setUserInfo(null)
    setIsAuthenticated(false)
    setAuthLoading(false)
  }, [])

  const updateUserInfo = useCallback((updater) => {
    setUserInfo(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      if (next) {
        // 在 state setter 外部执行副作用，避免 StrictMode 重复执行
        Promise.resolve().then(() => {
          saveUserInfo(next)
          if (next.token) {
            saveToken(next.token)
          }
        })
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
