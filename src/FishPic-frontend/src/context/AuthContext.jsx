import { createContext, useState, useEffect } from 'react'
import { getUserInfo, saveUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'
import { getUser } from '../api'

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

      setAuthLoading(true)
      try {
        const latestUserInfo = await getUser({ noDedup: true })
        if (ignore) return
        saveUserInfo(latestUserInfo)
        setUserInfo(latestUserInfo)
        setIsAuthenticated(true)
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

  const login = (data) => {
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
  }

  const logout = () => {
    clearAuth()
    setUserInfo(null)
    setIsAuthenticated(false)
    setAuthLoading(false)
  }

  const updateUserInfo = (updater) => {
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
  }

  return (
    <AuthContext.Provider value={{ userInfo, isAuthenticated, authLoading, login, logout, updateUserInfo }}>
      {children}
    </AuthContext.Provider>
  )
}
