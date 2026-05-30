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

/** 浏览器 <img> 能渲染的 MIME 类型（可用于裁剪） */
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
  0: 3 * 1024 * 1024,   // 普通用户 3MB
  1: 5 * 1024 * 1024,   // VIP 5MB
  2: 20 * 1024 * 1024,  // SVIP 20MB
}

const DEFAULT_MAX_SIZE = 5 * 1024 * 1024 // 默认 5MB

export function getMaxUploadSize() {
  const user = getUserInfo()
  const level = user?.level ?? 0
  return LEVEL_SIZE_MAP[level] ?? DEFAULT_MAX_SIZE
}

export function formatMaxUploadSize() {
  const bytes = getMaxUploadSize()
  return `${Math.round(bytes / 1024 / 1024)}MB`
}

/**
 * 判断文件是否为允许的图片格式
 * 优先用浏览器上报的MIME类型，不可用时（如Windows上HEIC会报空串）从扩展名回退判断
 */
export function isAllowedImageFile(file) {
  if (ALLOWED_IMAGE_TYPES.includes(file.type)) return true
  if (!file.name) return false
  const dot = file.name.lastIndexOf('.')
  if (dot === -1) return false
  const ext = file.name.substring(dot).toLowerCase()
  return ALLOWED_EXTENSIONS.includes(ext)
}
