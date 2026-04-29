const STORAGE_KEY = 'fishpics_user_info'

export const saveUserInfo = (userInfo) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(userInfo))
  } catch (error) {
    console.error('保存用户信息失败', error)
  }
}

export const getUserInfo = () => {
  try {
    const data = localStorage.getItem(STORAGE_KEY)
    return data ? JSON.parse(data) : null
  } catch (error) {
    console.error('读取用户信息失败', error)
    return null
  }
}

export const removeUserInfo = () => {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    console.error('清除用户信息失败', error)
  }
}

export const request = async (url, options = {}) => {
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  }
  
  const response = await fetch(url, {
    ...options,
    headers,
  })
  
  const result = await response.json()
  
  if (result.code !== 1) {
    throw new Error(result.message || '请求失败')
  }
  
  return result
}
