import { createContext, useState, useEffect } from 'react'
import { getUserInfo, saveUserInfo, removeUserInfo } from '../utils/storage'

export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [userInfo, setUserInfo] = useState(() => getUserInfo())
  const [isAuthenticated, setIsAuthenticated] = useState(!!getUserInfo())

  useEffect(() => {
    const user = getUserInfo()
    if (user) {
      setUserInfo(user)
      setIsAuthenticated(true)
    } else {
      setUserInfo(null)
      setIsAuthenticated(false)
    }
  }, [])

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

  return (
    <AuthContext.Provider value={{ userInfo, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
