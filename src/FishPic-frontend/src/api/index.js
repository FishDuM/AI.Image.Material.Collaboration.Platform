import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

api.interceptors.response.use(
  (response) => {
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
      return Promise.reject(new Error(responseData.message || '请求失败'))
    }
    return responseData.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败，请重试'
    return Promise.reject(new Error(message))
  }
)

export const getLoginCheckCode = () => api.get('/user/checkCode/login', {
  validateStatus: () => true,
})

export const getRegisterCheckCode = () => api.get('/user/checkCode/register', {
  validateStatus: () => true,
})

export const login = (data) => api.post('/user/login', data)

export const register = (data) => api.post('/user/register', data)

export const getUserMyself = () => api.get('/user/myself')

export const logout = () => api.get('/user/logout')

export const getUser = () => api.get('/user/getUser')

export const getAdminUser = (userId) => api.post('/user/admin/getUser', { userId })

export const editUser = (data) => api.post('/user/editUser', data)

export const uploadAvatar = (formData, onProgress) => api.post('/picture/avatar', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
  onUploadProgress: (progressEvent) => {
    if (onProgress && progressEvent.total) {
      onProgress({ percent: Math.round((progressEvent.loaded * 100) / progressEvent.total) })
    }
  }
})

export const uploadPostPicture = (formData, onProgress) => api.post('/picture/post', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
  onUploadProgress: (progressEvent) => {
    if (onProgress && progressEvent.total) {
      onProgress({ percent: Math.round((progressEvent.loaded * 100) / progressEvent.total) })
    }
  }
})

export const getMyPosts = (data) => api.post('/post/myPosts', data)

export const getMyCollects = (data) => api.post('/post/myCollects', data)

export const getMyLikes = (data) => api.post('/post/myLikes', data)

export const getMarquee = () => api.get('/system/marquee')

export const getPictureList = (current = 1, pageSize = 20) =>
  api.get('/picture/list', { params: { current, pageSize } })

export const getAdminPictureList = (current = 1, pageSize = 20, status = 3) =>
  api.get('/picture/admin/list', { params: { current, pageSize, status } })

export const reviewPicture = (pictureId, status, selected) => api.post('/picture/admin/review', null, { params: { pictureId, status, selected } })

export const createSpace = (data) => api.post('/space/create', data)

export const listSpace = (type) => api.get('/space/list', { params: { type } })

export default api
