import { getUserInfo } from './storage'

export const ALLOWED_IMAGE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/jpg',
  'image/gif',
  'image/webp',
  'image/heic',
  'image/bmp',
  'image/avif',
  'image/tiff',
  'image/vnd.adobe.photoshop',
  'image/x-eps',
]

/** 浏览器 <img> 能渲染的 MIME 类型 */
export const BROWSER_RENDERABLE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/bmp',
  'image/avif',
]

const ALLOWED_EXTENSIONS = [
  '.jpg', '.jpeg', '.png', '.gif', '.webp', '.heic', '.heif',
  '.bmp', '.tiff', '.tif', '.avif', '.apng',
  '.psd', '.ai', '.eps',
  '.raw', '.dng', '.cr3', '.crw', '.mos', '.erf', '.3fr', '.fff',
  '.kdc', '.dcr', '.rw2', '.pef', '.sr2', '.srf', '.arw', '.nef',
  '.nrw', '.orf', '.mef', '.mrw',
  '.astc', '.tpg',
]

const LEVEL_SIZE_MAP = {
  0: 10 * 1024 * 1024,       // 普通用户 10MB
  1: 1 * 1024 * 1024 * 1024, // VIP 1GB
  2: 10 * 1024 * 1024 * 1024, // SVIP 10GB
}

const DEFAULT_MAX_SIZE = 10 * 1024 * 1024 // 默认 10MB

export function getMaxUploadSize() {
  const user = getUserInfo()
  const level = user?.level ?? 0
  return LEVEL_SIZE_MAP[level] ?? DEFAULT_MAX_SIZE
}

export function formatMaxUploadSize() {
  const bytes = getMaxUploadSize()
  if (bytes >= 1024 * 1024 * 1024) {
    return `${Math.round(bytes / 1024 / 1024 / 1024)}GB`
  }
  return `${Math.round(bytes / 1024 / 1024)}MB`
}

/**
 * 判断文件是否为允许的图片格式
 */
export function isAllowedImageFile(file) {
  if (ALLOWED_IMAGE_TYPES.includes(file.type)) return true
  if (!file.name) return false
  const dot = file.name.lastIndexOf('.')
  if (dot === -1) return false
  const ext = file.name.substring(dot).toLowerCase()
  return ALLOWED_EXTENSIONS.includes(ext)
}

export function validateImageUpload(file) {
  if (!isAllowedImageFile(file)) {
    return { valid: false, reason: 'type', message: '不支持的图片格式' }
  }

  if (file.size > getMaxUploadSize()) {
    return { valid: false, reason: 'size', message: `图片大小不能超过${formatMaxUploadSize()}` }
  }

  return { valid: true }
}
