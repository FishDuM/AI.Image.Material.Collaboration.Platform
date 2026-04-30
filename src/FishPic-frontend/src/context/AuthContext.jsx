import { createContext, useState, useEffect } from 'react'
import { getUserInfo, saveUserInfo, removeUserInfo } from '../utils/storage'

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [userInfo, setUserInfo] = useState(() => getUserInfo())
  const [isAuthenticated, setIsAuthenticated] = useState(!!getUserInfo())

  /* eslint-disable react-hooks/set-state-in-effect */
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

  return (
    <AuthContext.Provider value={{ userInfo, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
