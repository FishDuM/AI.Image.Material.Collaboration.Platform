import { message } from 'antd'
import { isAllowedImageFile, getMaxUploadSize, formatMaxUploadSize } from './uploadConstraints'

/**
 * 文件转 Base64
 */
export const getBase64 = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(reader.result))
    reader.addEventListener('error', reject)
    reader.readAsDataURL(file)
  })
}

/**
 * 通用图片上传前校验（使用用户等级对应的大小限制）
 */
export const beforeUpload = (file) => {
  const maxSize = getMaxUploadSize()
  const maxSizeText = formatMaxUploadSize()
  const isAllowedImage = isAllowedImageFile(file)
  if (!isAllowedImage) {
    message.error('只能上传图片文件（JPEG、PNG、JPG、GIF、WebP、HEIC）！')
  }
  const isLtSize = file.size <= maxSize
  if (!isLtSize) {
    message.error(`图片大小不能超过${maxSizeText}！`)
  }
  return isAllowedImage && isLtSize
}
