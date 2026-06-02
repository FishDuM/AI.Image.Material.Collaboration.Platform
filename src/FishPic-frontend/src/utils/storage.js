const USER_KEY = 'fishpics_user_info'
const TOKEN_KEY = 'fishpics_auth_token'

export const saveUserInfo = (userInfo) => {
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo))
  } catch (error) {
    console.error('保存用户信息失败', error)
  }
}

export const getUserInfo = () => {
  try {
    const data = localStorage.getItem(USER_KEY)
    return data ? JSON.parse(data) : null
  } catch (error) {
    console.error('读取用户信息失败', error)
    return null
  }
}

export const removeUserInfo = () => {
  try {
    localStorage.removeItem(USER_KEY)
  } catch (error) {
    console.error('清除用户信息失败', error)
  }
}

export const saveToken = (token) => {
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
    localStorage.removeItem(TOKEN_KEY)
  } catch (error) {
    console.error('保存Token失败', error)
  }
}

export const getToken = () => {
  try {
    return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY)
  } catch (error) {
    console.error('读取Token失败', error)
    return null
  }
}

export const removeToken = () => {
  try {
    sessionStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TOKEN_KEY)
  } catch (error) {
    console.error('清除Token失败', error)
  }
}

export const clearAuth = () => {
  removeUserInfo()
  removeToken()
}
