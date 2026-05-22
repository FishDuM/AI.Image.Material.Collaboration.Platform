import { createContext, useState, useEffect } from 'react'
import { getUserInfo, saveUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'

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

  useEffect(() => {
    const handleAuthExpired = () => {
      setUserInfo(null)
      setIsAuthenticated(false)
    }
    window.addEventListener('auth:expired', handleAuthExpired)
    return () => window.removeEventListener('auth:expired', handleAuthExpired)
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
    <AuthContext.Provider value={{ userInfo, isAuthenticated, login, logout, updateUserInfo }}>
      {children}
    </AuthContext.Provider>
  )
}