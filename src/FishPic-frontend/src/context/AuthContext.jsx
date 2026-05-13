import { createContext, useState, useEffect } from 'react'
import { getUserInfo, saveUserInfo, removeUserInfo } from '../utils/storage'
import { getUser } from '../api'

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [userInfo, setUserInfo] = useState(() => getUserInfo())
  const [isAuthenticated, setIsAuthenticated] = useState(!!getUserInfo())

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const user = getUserInfo()
    if (user) {
      getUser()
        .then((freshUser) => {
          if (freshUser) {
            saveUserInfo(freshUser)
            setUserInfo(freshUser)
            setIsAuthenticated(true)
          } else {
            removeUserInfo()
            setUserInfo(null)
            setIsAuthenticated(false)
          }
        })
        .catch(() => {
          removeUserInfo()
          setUserInfo(null)
          setIsAuthenticated(false)
        })
    } else {
      setUserInfo(null)
      setIsAuthenticated(false)
    }
  }, [])
  /* eslint-enable react-hooks/set-state-in-effect */

  const login = (data) => {
    saveUserInfo(data)
    setUserInfo(data)
    setIsAuthenticated(true)
  }

  const logout = () => {
    removeUserInfo()
    setUserInfo(null)
    setIsAuthenticated(false)
  }

  const updateUserInfo = (updater) => {
    setUserInfo(prev => {
      const next = typeof updater === 'function' ? updater(prev) : updater
      if (next) saveUserInfo(next)
      return next
    })
  }

  return (
    <AuthContext.Provider value={{ userInfo, isAuthenticated, login, logout, updateUserInfo }}>
      {children}
    </AuthContext.Provider>
  )
}
