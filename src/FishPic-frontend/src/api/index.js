import axios from 'axios'
import { getToken, clearAuth } from '../utils/storage'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
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
    if (config.noDedup) {
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
      return new Promise(() => {})
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

const cacheMap = new Map()
const CACHE_TTL = 5 * 60 * 1000

export function clearCache(pattern) {
  if (!pattern) {
    cacheMap.clear()
    return
  }
  for (const key of cacheMap.keys()) {
    if (key.includes(pattern)) {
      cacheMap.delete(key)
    }
  }
}

function getCacheKey(config) {
  const { method, url, params } = config
  return [method, url, JSON.stringify(params || {})].join('&')
}

export const cachedGet = (axiosInstance) => {
  return async (config) => {
    const key = getCacheKey(config)
    const cached = cacheMap.get(key)
    if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
      return cached.data
    }
    const response = await axiosInstance(config)
    cacheMap.set(key, { data: response, timestamp: Date.now() })
    return response
  }
}

const MAX_RETRIES = 2
const RETRY_DELAY = 1000

export async function withRetry(fn, retries = MAX_RETRIES, delay = RETRY_DELAY) {
  let lastError
  for (let i = 0; i <= retries; i++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      if (error.name === 'CanceledError' || error.code === 'ERR_CANCELED' || axios.isCancel(error)) {
        throw error
      }
      if (error.response && error.response.status >= 400 && error.response.status < 500) {
        throw error
      }
      if (i < retries) {
        await new Promise(resolve => setTimeout(resolve, delay * (i + 1)))
      }
    }
  }
  throw lastError
}

export function useAbortController() {
  const controller = new AbortController()
  const abort = () => controller.abort()
  return { signal: controller.signal, abort }
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
  timeout: 60000,
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

export const getPictureList = (current = 1, pageSize = 20, config = {}) =>
  api.get('/picture/list', { params: { current, pageSize }, ...config })

export const getAdminPictureList = (current = 1, pageSize = 20, status = 3) =>
  api.get('/picture/admin/list', { params: { current, pageSize, status } })

export const reviewPicture = (pictureId, status, selected) => api.post('/picture/admin/review', null, { params: { pictureId, status, selected } })

export const createSpace = (data) => api.post('/space/create', data)

export const updateSpace = (data) => api.post('/space/update', data)

export const listSpace = (type, config = {}) => api.get('/space/list', { params: { type }, ...config })

export const getSpace = (id) => api.get('/space/getSpace', { params: { id } })

export const spaceListPicture = (data, config = {}) => api.post('/space/pictureList', data, config)

export const adminListSpace = (params) => api.get('/space/admin/list', { params })
export const adminUpdateSpace = (data) => api.post('/space/admin/update', data)
export const adminDeleteSpace = (id) => api.post('/space/admin/delete', { id })
export const adminSetSpaceStatus = (id, status) => api.post('/space/admin/setStatus', { id, status })

export const postPictureList = (data, config = {}) => api.post('/post/pictureList', data, config)

export const deletePicture = (ids) => api.delete('/picture/delete', { data: { ids } })

export const updatePicture = (data) => api.put('/picture/update', data)

export const likePost = (id) => api.post('/post/like', null, { params: { id } })

export const collectPost = (id) => api.post('/post/collect', null, { params: { id } })

export const getPost = (id, config = {}) => api.get('/post/getPost', { params: { id }, ...config })

export const editPost = (data) => api.post('/post/editPost', data)

export const uploadPost = (data) => api.post('/post/post', data)

export const getPostList = (data, config = {}) => api.post('/post/postList', data, config)

export const followUser = (userId) => api.post('/user/follow', { userId })

export const getUserProfile = (userId, config = {}) => api.get('/user/profile', { params: { userId }, ...config })

export const updatePrivacy = (data) => api.post('/user/privacy', data)

export const getFans = (params, config = {}) => api.get('/user/fans', { params, ...config })

export const getFollows = (params, config = {}) => api.get('/user/follows', { params, ...config })

export const getSystemTypes = () => api.get('/system/list')

export const createComment = (data) => api.post('/comment/create', data)

export const getCommentList = (data, config = {}) => api.post('/comment/list', data, config)

export const deleteComment = (id) => api.post('/comment/delete', null, { params: { id } })

export const reviewComment = (id, status) => api.post('/comment/review', null, { params: { id, status } })

export const adminDeleteComment = (id) => api.post('/comment/adminDelete', null, { params: { id } })

export const getAdminCommentList = (data) => api.post('/comment/admin/list', data)

export const uploadPicture = (formData, targetSpaceId) => {
  const fd = new FormData()
  fd.append('file', formData.get('file'))
  if (targetSpaceId != null) {
    fd.append('targetSpaceId', targetSpaceId)
  }
  return api.post('/picture/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

export default api