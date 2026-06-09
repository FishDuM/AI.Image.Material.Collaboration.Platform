import axios from 'axios'
import { getToken, saveToken, clearAuth } from '../utils/storage'
import { TIMEOUT_DEFAULT, TIMEOUT_AVATAR, TIMEOUT_AI, TIMEOUT_PICTURE } from '../utils/constants'

const api = axios.create({
  baseURL: '/api',
  timeout: TIMEOUT_DEFAULT,
  headers: {
    'Content-Type': 'application/json',
  },
})

const pendingRequests = new Map()

let requestCounter = 0

function getRequestKey(config) {
  try {
    const { method, url, params, data } = config
    return [method, url, JSON.stringify(params || {}), JSON.stringify(data || {})].join('&')
  } catch {
    return [config.method, config.url, Date.now()].join('&')
  }
}

api.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (!config.dedup) {
      return config
    }
    const key = getRequestKey(config)
    const oldEntry = pendingRequests.get(key)
    if (oldEntry) {
      oldEntry.controller.abort()
      pendingRequests.delete(key)
    }
    const controller = new AbortController()
    if (!config.signal) {
      config.signal = controller.signal
    }
    config._dedupId = ++requestCounter
    config._dedupKey = key
    pendingRequests.set(key, { controller, id: config._dedupId })
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

function cleanupDedup(config) {
  if (!config) return
  const key = config._dedupKey || getRequestKey(config)
  const entry = pendingRequests.get(key)
  if (entry && entry.id === config._dedupId) {
    pendingRequests.delete(key)
  }
}

api.interceptors.response.use(
  (response) => {
    if (!response || !response.config) return response
    cleanupDedup(response.config)
    if (response.config.responseType === 'blob') {
      return response
    }
    if (response.config.url && response.config.url.includes('/checkCode/')) {
      return response
    }

    const responseData = response.data
    if (!responseData || typeof responseData.code === 'undefined') {
      return Promise.reject(new Error('响应格式异常'))
    }
    // JWT 自动续签：检查响应头中的新 Token（在业务状态判断之前，避免业务错误时丢弃续签）
    const newToken = response.headers['x-new-token']
    if (newToken) {
      saveToken(newToken)
    }
    if (responseData.code !== 1) {
      // 只有 40005（未登录）才触发登录过期，40002（无权限）不应清除 token
      if (responseData.code === 40005) {
        handleAuthExpired()
      }
      return Promise.reject(new Error(responseData.message || '请求失败'))
    }
    return responseData.data ?? responseData
  },
  (error) => {
    cleanupDedup(error.config)
    if (error.name === 'CanceledError' || error.code === 'ERR_CANCELED' || axios.isCancel(error)) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      handleAuthExpired()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    const message = error.response?.data?.message || error.message || '请求失败，请重试'
    return Promise.reject(new Error(message))
  }
)

function handleAuthExpired() {
  clearAuth()
  window.dispatchEvent(new CustomEvent('auth:expired'))
}

export const getLoginCheckCode = () => api.get('/user/checkCode/login', {
  validateStatus: () => true,
})

export const getRegisterCheckCode = () => api.get('/user/checkCode/register', {
  validateStatus: () => true,
})

export const login = (data) => api.post('/user/login', data)

export const register = (data) => api.post('/user/register', data)

export const getUserMyself = (config = {}) => api.get('/user/myself', config)

export const logout = () => api.post('/user/logout', {})

export const getUser = (config = {}) => api.get('/user/getUser', config)

export const getUserProfile = (userId, config = {}) => api.get('/user/profile', { params: { userId }, ...config })

export const getAdminUser = (userId, config = {}) => api.post('/user/admin/getUser', { userId }, config)

export const editUser = (data) => api.post('/user/editUser', data)

export const uploadAvatar = (formData, onProgress) => api.post('/picture/avatar', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
  timeout: TIMEOUT_AVATAR,
  onUploadProgress: (progressEvent) => {
    if (onProgress && progressEvent.total) {
      onProgress({ percent: Math.round((progressEvent.loaded * 100) / progressEvent.total) })
    }
  }
})

export const getMarquee = () => api.get('/system/marquee')

export const getPictureList = (current = 1, pageSize = 20, config = {}, tag = '') => {
  const data = { current, pageSize }
  if (tag && tag.trim()) {
    data.tag = tag.trim()
  }
  return api.post('/picture/list', data, config)
}

export const getAdminPictureList = (current = 1, pageSize = 20, status) => {
  const body = { current, pageSize }
  if (status !== undefined && status !== null) {
    body.status = status
  }
  return api.post('/picture/admin/list', body)
}

export const reviewPicture = (pictureId, status, selected) => api.post('/picture/admin/review', { pictureId, status, selected })

export const createSpace = (data) => api.post('/space/create', data)

export const updateSpace = (data) => api.post('/space/update', data)

export const listSpace = (type, config = {}) => api.get('/space/list', { params: { type }, ...config })

export const getSaveableSpaces = (config = {}) => api.get('/space/saveable', config)

export const getSpace = (id) => api.get('/space/getSpace', { params: { id } })

export const spaceListPicture = (data, config = {}) => api.post('/space/pictureList', data, config)

export const adminListSpace = (params) => api.post('/space/admin/list', params)
export const adminUpdateSpace = (data) => api.post('/space/admin/update', data)
export const adminDeleteSpace = (id) => api.post('/space/admin/delete', { id })
export const adminSetSpaceStatus = (id, status) => api.post('/space/admin/setStatus', { id, status })

export const deletePicture = (ids) => api.post('/picture/delete', { ids })

export const updatePicture = (data) => api.put('/picture/update', data)

export const replacePictureFile = (file, pictureId) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('pictureId', pictureId)
  return api.post('/picture/replace', formData)
}

export const getPictureEditMessage = (id) => api.get('/picture/pictureEditMessage', { params: { id } })

export const getRecommendPictures = (data, config = {}) => api.post('/picture/recommend', data, config)

export const getSystemTypes = () => api.get('/system/list')

// AI 相关 API
export const submitAiTag = (id) => api.post('/ai/tags', { id })
export const getAiTagResult = (taskId) => api.get(`/ai/tags/result/${taskId}`)
export const pollAiTagResult = async (taskId, { interval = 2000, timeout = TIMEOUT_AI, signal } = {}) => {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
    const task = await getAiTagResult(taskId)
    if (task.status === 'DONE') return JSON.parse(task.result)
    if (task.status === 'FAILED') throw new Error(task.errorMsg || 'AI识别失败')
    await new Promise((r, j) => {
      const timer = setTimeout(r, interval)
      signal?.addEventListener('abort', () => { clearTimeout(timer); j(new DOMException('Aborted', 'AbortError')) }, { once: true })
    })
  }
  throw new Error('AI识别超时')
}
export const getAiTasks = (data) => api.post('/ai/admin/tasks', data)
export const getAiStats = () => api.get('/ai/admin/stats')
export const getAiConfig = () => api.get('/ai/admin/config')
export const updateAiConfig = (data) => api.post('/ai/admin/config', data)
export const submitAiDraw = (data) => api.post('/ai/draw/submit', data)
export const getAiDrawResult = (taskId) => api.get(`/ai/draw/result/${taskId}`)

export const savePictureByUrl = (url, targetSpaceId) =>
  api.post('/picture/save-by-url', { url, targetSpaceId })

export const searchUsers = (keyword) => api.get('/user/search', { params: { keyword } })
export const getTeamMembers = (spaceId) => api.get('/space/team/members', { params: { spaceId } })
export const teamInvite = (data) => api.post('/space/team/invite', data)
export const teamRemove = (data) => api.post('/space/team/remove', data)
export const teamChangeRole = (data) => api.post('/space/team/changeRole', data)

export const getSystemStats = () => api.get('/system/stats')
export const getAuditLogs = (params) => api.post('/system/audit-log/list', params)

export const uploadPicture = (file, targetSpaceId) => {
  const fd = new FormData()
  fd.append('file', file)
  if (targetSpaceId != null) {
    fd.append('targetSpaceId', targetSpaceId)
  }
  return api.post('/picture/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: TIMEOUT_PICTURE,
  })
}

// ==================== 分片上传 API ====================

/**
 * 秒传校验
 */
export const checkUpload = (data) => api.post('/picture/check', data)

/**
 * 分片上传
 */
export const uploadChunk = (formData, md5, chunkIndex) => {
  const fd = new FormData()
  fd.append('file', formData.get ? formData.get('file') : formData)
  fd.append('md5', md5)
  fd.append('chunkIndex', chunkIndex)
  return api.post('/picture/upload-chunk', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: TIMEOUT_PICTURE,
  })
}

/**
 * 合并分片
 */
export const mergeChunks = (data) => api.post('/picture/merge', data)

// ==================== 分享 API ====================

export const createShare = (data) => api.post('/share/create', data)

export const getShareInfo = (token) => api.get(`/share/info/${token}`)

export const cancelShare = (shareId) => api.post('/share/cancel', { shareId })

export const downloadAiImage = (taskId) => api.get(`/ai/download-image/${taskId}`, { responseType: 'blob' })

export default api
