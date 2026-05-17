import { createContext, useState, useEffect } from 'react'
import { getUserInfo, saveUserInfo, removeUserInfo, saveToken, getToken, clearAuth } from '../utils/storage'
import { getUser } from '../api'

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

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const token = getToken()
    const user = getUserInfo()
    if (token && user) {
      getUser()
        .then((freshUser) => {
          if (freshUser) {
            saveUserInfo(freshUser)
            setUserInfo(freshUser)
            setIsAuthenticated(true)
          } else {
            clearAuth()
            setUserInfo(null)
            setIsAuthenticated(false)
          }
        })
        .catch(() => {
          clearAuth()
          setUserInfo(null)
          setIsAuthenticated(false)
        })
    } else {
      if (!token) {
        removeUserInfo()
      }
      setUserInfo(null)
      setIsAuthenticated(false)
    }
  }, [])
  /* eslint-enable react-hooks/set-state-in-effect */

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
