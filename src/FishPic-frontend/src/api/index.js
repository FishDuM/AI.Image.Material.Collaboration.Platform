import axios from 'axios'
import { getToken } from '../utils/storage'

const api = axios.create({
  baseURL: '/api/user',
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
    if (response.data.code !== 1) {
      return Promise.reject(new Error(response.data.message || '请求失败'))
    }
    return response.data.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败，请重试'
    return Promise.reject(new Error(message))
  }
)

export const getLoginCheckCode = () => api.get('/checkCode/login', {
  validateStatus: () => true,
})

export const getRegisterCheckCode = () => api.get('/checkCode/register', {
  validateStatus: () => true,
})

export const login = (data) => api.post('/login', data)

export const register = (data) => api.post('/register', data)

export const getUserMyself = () => api.get('/myself')

export const editUser = (data) => api.post('/editUser', data)

export default api
