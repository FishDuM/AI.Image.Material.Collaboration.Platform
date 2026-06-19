import { createContext, useState, useEffect, useCallback, useRef } from 'react'
import { getUserInfo, saveUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'
import { TOKEN_REFRESH_INTERVAL } from '../utils/constants'
import { resetAuthFailureCounter } from '../api'
import { getUser, logout as logoutApi } from '../api'

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [userInfo, setUserInfo] = useState(() => {
    const token = getToken()
    return token ? getUserInfo() : null
  })
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!getToken())
  const [authLoading, setAuthLoading] = useState(() => !!getToken())

  const tokenSnapshotRef = useRef(null)
  const pendingSaveRef = useRef(null)

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
      // 分享页不验证登录态，避免过期 JWT 触发 handleAuthExpired 清掉其他页面的登录信息
      if (window.location.pathname.startsWith('/s/')) {
        setAuthLoading(false)
        return
      }
      const token = getToken()
      if (!token) {
        clearAuth()
        setUserInfo(null)
        setIsAuthenticated(false)
        setAuthLoading(false)
        return
      }
      tokenSnapshotRef.current = token

      setAuthLoading(true)
      try {
        const latestUserInfo = await getUser({ noDedup: true })
        if (ignore) return
        saveUserInfo(latestUserInfo)
        setUserInfo(latestUserInfo)
        setIsAuthenticated(true)
        resetAuthFailureCounter()
      } catch (error) {
        if (ignore) return
        if (getToken() !== tokenSnapshotRef.current) return
        const status = error?.response?.status
        const code = error?.code ?? error?.response?.data?.code
        const isAuthError = status === 401 || code === 40005 || code === 40002
        if (isAuthError) {
          clearAuth()
          setUserInfo(null)
          setIsAuthenticated(false)
          window.dispatchEvent(new CustomEvent('auth:expired'))
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

  useEffect(() => {
    if (!isAuthenticated) return

    const interval = setInterval(async () => {
      try {
        const latestUserInfo = await getUser({ noDedup: true })
        saveUserInfo(latestUserInfo)
        setUserInfo(latestUserInfo)
      } catch (error) {
        const status = error?.response?.status
        const code = error?.code
        if (status === 401 || code === 40005 || code === 40002) {
          return
        }
      }
    }, TOKEN_REFRESH_INTERVAL)

    return () => clearInterval(interval)
  }, [isAuthenticated])

  const login = useCallback((data) => {
    if (data.token) {
      saveToken(data.token)
      const { token: _token, ...rest } = data
      const safeUser = {
        id: rest.id,
        username: rest.username,
        nickname: rest.nickname,
        avatar: rest.avatar,
        level: rest.level,
        roleId: rest.roleId,
        permissions: Array.isArray(rest.permissions) ? rest.permissions : [],
      }
      setUserInfo(safeUser)
      saveUserInfo(safeUser)
    } else {
      setUserInfo(data)
      saveUserInfo(data)
    }
    tokenSnapshotRef.current = getToken()
    setIsAuthenticated(true)
    resetAuthFailureCounter()
    setAuthLoading(false)
  }, [])

  const logout = useCallback(async () => {
    try { await logoutApi() } catch { /* 忽略，确保本地清理 */ }
    clearAuth()
    setUserInfo(null)
    setIsAuthenticated(false)
    setAuthLoading(false)
  }, [])

  useEffect(() => {
    if (pendingSaveRef.current !== null) {
      const pending = pendingSaveRef.current
      pendingSaveRef.current = null
      saveUserInfo(pending)
      if (pending.token) {
        saveToken(pending.token)
      }
    }
  }, [userInfo])

  const updateUserInfo = useCallback((updater) => {
    setUserInfo(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      if (next) {
        pendingSaveRef.current = next
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
