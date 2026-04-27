import axios from 'axios'
import { getToken } from '../utils/storage'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = token
    }
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

export default api
