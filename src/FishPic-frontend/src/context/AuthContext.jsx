import { createContext, useState, useEffect, useCallback, useRef } from 'react'
import { getUserInfo, getUserInfoAsync, saveUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'
import { resetAuthFailureCounter, markPasswordChange } from '../api'
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

  // 加密模式下 getUserInfo() 同步返回 null，用异步版本补读一次
  useEffect(() => {
    if (!userInfo && isAuthenticated) {
      getUserInfoAsync().then(cached => {
        if (cached) setUserInfo(cached)
      })
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const tokenSnapshotRef = useRef(null)
  const __pendingSaveRef = useRef(null)

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
      // 记录本次验证开始时的 token，用于 catch 中判断是否被新登录覆盖
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
        } else {
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
      } catch (error) {
        const status = error?.response?.status
        const code = error?.code
        if (status === 401 || code === 40005 || code === 40002) {
          return
        }
        // eslint-disable-next-line no-console
        console.warn('[AuthContext] 静默刷新失败:', error?.message || error)
      }
    }, 5 * 60 * 1000)

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

  // 将持久化副作用移出 setUserInfo updater，避免 StrictMode 重复执行
  useEffect(() => {
    if (__pendingSaveRef.current !== null) {
      const pending = __pendingSaveRef.current
      __pendingSaveRef.current = null
      saveUserInfo(pending)
      if (pending.token) {
        saveToken(pending.token)
      }
    }
  })

  const updateUserInfo = useCallback((updater) => {
    setUserInfo(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      if (next) {
        // 存入 ref，由上面的 useEffect 在 commit 后统一持久化
        __pendingSaveRef.current = next
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
