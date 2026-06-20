import axios from 'axios'
import { getToken, saveToken, clearAuth } from '../utils/storage'
import { TIMEOUT_DEFAULT, TIMEOUT_AVATAR, TIMEOUT_PICTURE } from '../utils/constants'

const api = axios.create({
  baseURL: '/api',
  timeout: TIMEOUT_DEFAULT,
  headers: {
    'Content-Type': 'application/json',
  },
})

export const API_BASE_URL = api.defaults.baseURL || ''

api.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  }
)

api.interceptors.response.use(
  (response) => {
    const newToken = response.headers['x-new-token']
    if (newToken) {
      saveToken(newToken)
    }
    consecutiveAuthFailures = 0
    if (response.config.responseType === 'blob') {
      return response.data
    }
    if (response.config.url && response.config.url.includes('/checkCode/')) {
      return response
    }

    const responseData = response.data
    if (!responseData || typeof responseData.code === 'undefined') {
      return Promise.reject(new Error('响应格式异常'))
    }
    if (responseData.code !== 1) {
      if (responseData.code === 40005) {
        handleAuthExpired()
      }
      const businessError = new Error(responseData.message || '请求失败')
      businessError.code = responseData.code
      businessError.data = responseData
      return Promise.reject(businessError)
    }
    return Object.prototype.hasOwnProperty.call(responseData, 'data') ? responseData.data : responseData
  },
  (error) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      handleAuthExpired()
    }
    // 提取后端业务错误消息，替换 Axios 默认的英文提示
    const bizMessage = error.response?.data?.message
    if (bizMessage) {
      const bizError = new Error(bizMessage)
      bizError.code = error.response.data.code
      bizError.data = error.response.data
      return Promise.reject(bizError)
    }
    return Promise.reject(error)
  }
)

let consecutiveAuthFailures = 0
const AUTH_FAIL_THRESHOLD = 3
let authExpiredHandling = false
let lastPasswordChangeAt = 0
const POST_CHANGE_GRACE_MS = 5000

export function markPasswordChange() {
  lastPasswordChangeAt = Date.now()
}

function handleAuthExpired() {
  if (Date.now() - lastPasswordChangeAt < POST_CHANGE_GRACE_MS) {
    return
  }
  consecutiveAuthFailures++
  if (consecutiveAuthFailures < AUTH_FAIL_THRESHOLD) {
    return
  }
  if (authExpiredHandling) return
  authExpiredHandling = true
  clearAuth()
  window.dispatchEvent(new CustomEvent('auth:expired'))
  const currentPath = window.location.pathname + window.location.search
  const isAlreadyOnLogin = currentPath.startsWith('/mobile/login') || currentPath.startsWith('/mobile/register')
  if (!isAlreadyOnLogin) {
    const isMobile = window.innerWidth < 768
    if (isMobile) {
      const redirect = encodeURIComponent(currentPath)
      window.location.href = `/mobile/login?redirect=${redirect}`
    } else {
      window.location.href = '/'
    }
  }
  setTimeout(() => { authExpiredHandling = false }, 5000)
}

export function resetAuthFailureCounter() {
  consecutiveAuthFailures = 0
  authExpiredHandling = false
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
export const getAdminUserDetail = (userId, config = {}) => api.post('/user/admin/getUserDetail', { userId }, config)
export const adminListUsers = (data) => api.post('/user/admin/userList', data)
export const adminSetUserStatus = (userId) => api.post('/user/admin/setStatus', { userId })
export const adminEditUser = (data) => api.post('/user/admin/editUser', data)

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
export const addMarquee = (pictureIds) => api.post('/system/addMarquee', { pictureIds })
export const addSystemType = (tag) => api.post('/system/addList', { value: [tag] })
export const deleteSystemType = (tag) => api.post('/system/deleteType', { value: tag })
export const deleteMarquee = (url) => api.post('/system/deleteMarquee', { url })

export const getPictureList = (current = 1, pageSize = 20, config = {}, tag = '') => {
  const data = { current, pageSize }
  if (tag && tag.trim()) {
    data.tag = tag.trim()
  }
  return api.post('/picture/list', data, config)
}

export const getAdminPictureList = (current = 1, pageSize = 20, selected) => {
  const body = { current, pageSize }
  if (selected != null) {
    body.selected = selected
  }
  return api.post('/picture/admin/list', body)
}

export const reviewPicture = (pictureId, selected) => api.post('/picture/admin/review', { pictureId, selected })

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

export const replacePictureFile = (file, pictureId, options = {}) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('pictureId', pictureId)
  if (options.collab) {
    formData.append('collab', 'true')
  }
  return api.post('/picture/replace', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export const getPictureEditMessage = (id) => api.get('/picture/pictureEditMessage', { params: { id } })

export const getRecommendPictures = (data, config = {}) => api.post('/picture/recommend', data, config)

export const getSystemTypes = () => api.get('/system/list')

export const submitAiTag = (id) => api.post('/ai/tags', { id })
export const getAiTasks = (data) => api.post('/ai/admin/tasks', data)
export const getAiStats = () => api.get('/ai/admin/stats')
export const getAiConfig = () => api.get('/ai/admin/config')
export const updateAiConfig = (data) => api.post('/ai/admin/config', data)
export const submitAiDraw = (data) => api.post('/ai/draw/submit', data)

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

export const checkUpload = (data) => api.post('/picture/check', data)

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

export const mergeChunks = (data) => api.post('/picture/merge', data)

export const createShare = (data) => api.post('/share/create', data)

export const getShareInfo = (token, config) => api.get(`/share/info/${token}`, config)

export const cancelShare = (shareId) => api.post('/share/cancel', { shareId })

export const downloadAiImage = (taskId) => api.get(`/ai/download-image/${taskId}`, { responseType: 'blob' })

export default api
