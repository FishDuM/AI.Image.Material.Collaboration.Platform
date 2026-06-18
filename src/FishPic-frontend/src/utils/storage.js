const USER_KEY = 'fishpics_user_info'
const LEGACY_TOKEN_KEY = 'fishpics_auth_token'
const TOKEN_KEY = 'fishpics_auth_token'
const LEGACY_ENC_KEY_STORAGE = 'fishpics_enc_key'

const SAFE_USER_FIELDS = ['id', 'username', 'nickname', 'avatar', 'level', 'roleId', 'permissions']

let cachedUserInfo = null
let cachedAt = 0

function pickSafeUser(userInfo) {
  if (!userInfo || typeof userInfo !== 'object') return null
  const safe = {}
  for (const key of SAFE_USER_FIELDS) {
    if (userInfo[key] !== undefined) safe[key] = userInfo[key]
  }
  return safe
}

function readJson(storage, key) {
  const raw = storage.getItem(key)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const saveUserInfo = (userInfo) => {
  try {
    const safe = pickSafeUser(userInfo)
    if (!safe) return
    cachedUserInfo = safe
    cachedAt = Date.now()
    sessionStorage.setItem(USER_KEY, JSON.stringify(safe))
    localStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(LEGACY_ENC_KEY_STORAGE)
  } catch (error) {
    console.error('保存用户信息失败', error)
  }
}

export const getUserInfo = () => {
  try {
    if (cachedUserInfo && Date.now() - cachedAt < 60000) {
      return cachedUserInfo
    }

    let userInfo = readJson(sessionStorage, USER_KEY)
    if (!userInfo) {
      userInfo = readJson(localStorage, USER_KEY)
      if (userInfo) {
        sessionStorage.setItem(USER_KEY, JSON.stringify(userInfo))
        localStorage.removeItem(USER_KEY)
      }
    }

    cachedUserInfo = pickSafeUser(userInfo)
    cachedAt = cachedUserInfo ? Date.now() : 0
    return cachedUserInfo
  } catch (error) {
    console.error('读取用户信息失败', error)
    return null
  }
}

export const removeUserInfo = () => {
  try {
    cachedUserInfo = null
    cachedAt = 0
    sessionStorage.removeItem(USER_KEY)
    localStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(LEGACY_ENC_KEY_STORAGE)
  } catch (error) {
    console.error('清除用户信息失败', error)
  }
}

export const saveToken = (token) => {
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
    localStorage.removeItem(LEGACY_TOKEN_KEY)
  } catch (error) {
    console.error('保存Token失败', error)
  }
}

export const getToken = () => {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch (error) {
    console.error('读取Token失败', error)
    return null
  }
}

export const removeToken = () => {
  try {
    sessionStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(LEGACY_TOKEN_KEY)
  } catch (error) {
    console.error('清除Token失败', error)
  }
}

export const clearAuth = () => {
  removeUserInfo()
  removeToken()
}
