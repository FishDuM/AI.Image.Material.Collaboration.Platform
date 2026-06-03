import axios from 'axios'
import { getToken, clearAuth } from '../utils/storage'
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
  const { method, url, params, data } = config
  return [method, url, JSON.stringify(params || {}), JSON.stringify(data || {})].join('&')
}

api.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = token
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
    if (responseData.code !== 1) {
      if (responseData.code === 40005 || responseData.code === 40002) {
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

export const getMyPosts = (data, config = {}) => api.post('/post/myPosts', data, config)

export const getMyCollects = (data, config = {}) => api.post('/post/myCollects', data, config)

export const getMyLikes = (data, config = {}) => api.post('/post/myLikes', data, config)

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

export const getSpace = (id) => api.get('/space/getSpace', { params: { id } })

export const spaceListPicture = (data, config = {}) => api.post('/space/pictureList', data, config)

export const adminListSpace = (params) => api.post('/space/admin/list', params)
export const adminUpdateSpace = (data) => api.post('/space/admin/update', data)
export const adminDeleteSpace = (id) => api.post('/space/admin/delete', { id })
export const adminSetSpaceStatus = (id, status) => api.post('/space/admin/setStatus', { id, status })

export const postPictureList = (data, config = {}) => api.post('/post/pictureList', data, config)

export const deletePicture = (ids) => api.delete('/picture/delete', { data: { ids } })

export const updatePicture = (data) => api.put('/picture/update', data)

export const getPictureEditMessage = (id) => api.get('/picture/pictureEditMessage', { params: { id } })

export const likePost = (id) => api.post('/post/like', { id })

export const collectPost = (id) => api.post('/post/collect', { id })

export const getPost = (id, config = {}) => api.get('/post/getPost', { params: { id }, ...config })

export const editPost = (data) => api.post('/post/editPost', data)

export const uploadPost = (data) => api.post('/post/post', data)

export const getPostList = (data, config = {}) => api.post('/post/postList', data, config)

export const getRecommendPosts = (data, config = {}) => api.post('/post/recommend', data, config)

export const getRecommendPictures = (data, config = {}) => api.post('/picture/recommend', data, config)

export const followUser = (userId) => api.post('/user/follow', { userId })

export const getUserProfile = (userId, config = {}) => api.get('/user/profile', { params: { userId }, ...config })

export const updatePrivacy = (data) => api.post('/user/privacy', data)

export const getFans = (data, config = {}) => api.post('/user/fans', data, config)

export const getFollows = (data, config = {}) => api.post('/user/follows', data, config)

export const getSystemTypes = () => api.get('/system/list')

export const createComment = (data) => api.post('/comment/create', data)

export const getCommentList = (data, config = {}) => api.post('/comment/list', data, config)

export const deleteComment = (id) => api.post('/comment/delete', { id })

export const reviewComment = (id, status) => api.post('/comment/review', { id, status })

export const adminDeleteComment = (id) => api.post('/comment/adminDelete', { id })

export const getAdminCommentList = (data) => api.post('/comment/admin/list', data)

export const getAdminPostList = (data) => api.post('/post/admin/list', data)
export const reviewPost = (id, status) => api.post('/post/admin/review', { id, status })
export const adminDeletePost = (id) => api.post('/post/admin/delete', { id })

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
export const submitAiGenerate = (data, config = {}) => api.post('/ai/draw', data, { timeout: TIMEOUT_AI, ...config })
export const submitAiDraw = (data) => api.post('/ai/draw/submit', data)
export const getAiDrawResult = (taskId) => api.get(`/ai/draw/result/${taskId}`)

export const savePictureByUrl = (url, targetSpaceId) =>
  api.post('/picture/save-by-url', { url, targetSpaceId })

export const searchUsers = (keyword) => api.get('/user/search', { params: { keyword } })
export const getTeamMembers = (spaceId) => api.get('/space/team/members', { params: { spaceId } })
export const teamInvite = (data) => api.post('/space/team/invite', data)
export const teamRemove = (data) => api.post('/space/team/remove', data)
export const teamChangeRole = (data) => api.post('/space/team/changeRole', data)

export const uploadPicture = (formData, targetSpaceId) => {
  const fd = new FormData()
  fd.append('file', formData.get('file'))
  if (targetSpaceId != null) {
    fd.append('targetSpaceId', targetSpaceId)
  }
  return api.post('/picture/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: TIMEOUT_PICTURE,
  })
}

export default api
