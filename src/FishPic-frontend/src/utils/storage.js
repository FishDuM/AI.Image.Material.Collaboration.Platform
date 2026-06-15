const USER_KEY = 'fishpics_user_info'
const TOKEN_KEY = 'fishpics_auth_token'
const ENC_KEY_STORAGE = 'fishpics_enc_key'

// 只落安全字段,防止敏感 PII 进 localStorage
const SAFE_USER_FIELDS = ['id', 'username', 'nickname', 'avatar', 'level', 'roleId', 'permissions']
function pickSafeUser(userInfo) {
  if (!userInfo || typeof userInfo !== 'object') return null
  const safe = {}
  for (const k of SAFE_USER_FIELDS) {
    if (userInfo[k] !== undefined) safe[k] = userInfo[k]
  }
  return safe
}

// localStorage 加密(obfuscation,挡部分爬虫/插件读取)
async function getOrCreateEncKey() {
  try {
    const existing = sessionStorage.getItem(ENC_KEY_STORAGE)
    if (existing) {
      const raw = Uint8Array.from(atob(existing), c => c.charCodeAt(0))
      return crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt'])
    }
  } catch (e) { /* fall through to create */ }
  try {
    const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
    const raw = new Uint8Array(await crypto.subtle.exportKey('raw', key))
    sessionStorage.setItem(ENC_KEY_STORAGE, btoa(String.fromCharCode(...raw)))
    return key
  } catch (e) {
    return null // 极旧浏览器:fallback 到明文
  }
}

function bytesToB64(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
}
function b64ToBytes(b64) {
  return Uint8Array.from(atob(b64), c => c.charCodeAt(0))
}

async function encryptJson(obj) {
  const key = await getOrCreateEncKey()
  if (!key) return null
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const plaintext = new TextEncoder().encode(JSON.stringify(obj))
  const cipher = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plaintext)
  return JSON.stringify({ iv: bytesToB64(iv), ct: bytesToB64(cipher) })
}

async function decryptJson(payload) {
  if (!payload) return null
  try {
    const parsed = JSON.parse(payload)
    const key = await getOrCreateEncKey()
    if (!key) return null
    const iv = b64ToBytes(parsed.iv)
    const ct = b64ToBytes(parsed.ct)
    const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ct)
    return JSON.parse(new TextDecoder().decode(plaintext))
  } catch (e) {
    return null
  }
}

export const saveUserInfo = (userInfo) => {
  try {
    const safe = pickSafeUser(userInfo)
    if (safe) {
      _cachedUserInfo = safe
      _cachedAt = Date.now()
      try {
        localStorage.setItem(USER_KEY, JSON.stringify(safe))
      } catch (_) {}
      // 异步加密覆盖明文
      encryptJson(safe).then(encrypted => {
        if (encrypted) {
          localStorage.setItem(USER_KEY, encrypted)
        }
      })
    }
  } catch (error) {
    console.error('保存用户信息失败', error)
  }
}

let _cachedUserInfo = null
let _cachedAt = 0

export const getUserInfo = () => {
  try {
    if (_cachedUserInfo && Date.now() - _cachedAt < 60000) {
      return _cachedUserInfo
    }
    const data = localStorage.getItem(USER_KEY)
    if (!data) return null
    // 兼容旧数据(明文 JSON)
    if (data.startsWith('{') && data.includes('"id"')) {
      return JSON.parse(data)
    }
    decryptJson(data).then(dec => {
      if (dec) {
        _cachedUserInfo = dec
        _cachedAt = Date.now()
      }
    })
    return _cachedUserInfo
  } catch (error) {
    console.error('读取用户信息失败', error)
    return null
  }
}

/**
 * 异步获取用户信息(解密)— 用于在 AuthContext 启动时调用
 */
export const getUserInfoAsync = async () => {
  try {
    const data = localStorage.getItem(USER_KEY)
    if (!data) return null
    if (data.startsWith('{') && data.includes('"id"')) {
      return JSON.parse(data) // 旧格式
    }
    return await decryptJson(data)
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
    return sessionStorage.getItem(TOKEN_KEY)
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
  try { sessionStorage.removeItem(ENC_KEY_STORAGE) } catch (e) {}
}
