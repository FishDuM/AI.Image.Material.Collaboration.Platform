import { checkUpload, mergeChunks, uploadChunk } from '../api'
import { validateImageUpload } from './uploadConstraints'
import { CHUNK_UPLOAD_RETRY_COUNT, CHUNK_UPLOAD_BACKOFF_BASE } from './constants'

export const CHUNK_SIZE = 2 * 1024 * 1024
export const MAX_CONCURRENT_UPLOADS = 5

export const getBase64 = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(reader.result))
    reader.addEventListener('error', reject)
    reader.readAsDataURL(file)
  })
}

export const beforeUpload = (file) => {
  return validateImageUpload(file).valid
}

export const createBeforeUpload = (messageApi) => (file) => {
  const result = validateImageUpload(file)
  if (!result.valid && result.message) {
    messageApi.error(result.message)
  }
  return result.valid
}

export async function computeFileMD5(file) {
  const { default: SparkMD5 } = await import('spark-md5')
  return new Promise((resolve, reject) => {
    const chunks = Math.ceil(file.size / CHUNK_SIZE)
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    let currentChunk = 0

    reader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk += 1
      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }
    reader.onerror = () => reject(new Error('MD5 计算失败'))

    function loadNext() {
      const start = currentChunk * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      reader.readAsArrayBuffer(file.slice(start, end))
    }

    loadNext()
  })
}

export async function uploadLargePicture(file, {
  targetSpaceId,
  onStatus,
  onProgress,
  maxConcurrent = MAX_CONCURRENT_UPLOADS,
} = {}) {
  onStatus?.('md5')
  onProgress?.(0)
  const md5 = await computeFileMD5(file)

  const checkResult = await checkUpload({ md5, size: file.size, targetSpaceId })
  if (checkResult.status === 'duplicate') {
    onStatus?.('duplicate')
    onProgress?.(100)
    return { status: 'duplicate', picture: checkResult.picture }
  }

  onStatus?.('uploading')
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  const uploadedChunks = new Set(checkResult.uploadedChunks || [])
  const pending = []
  for (let i = 0; i < totalChunks; i += 1) {
    if (!uploadedChunks.has(i)) pending.push(i)
  }

  let completed = uploadedChunks.size
  const updateProgress = () => {
    onProgress?.(Math.round((completed / totalChunks) * 100))
  }
  updateProgress()

  const uploadSingleChunk = async (index) => {
    const start = index * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, file.size)
    const chunk = file.slice(start, end)
    const chunkFile = new File([chunk], `chunk_${index}`, { type: file.type })
    const formData = new FormData()
    formData.append('file', chunkFile)

    let lastError
    for (let attempt = 1; attempt <= CHUNK_UPLOAD_RETRY_COUNT; attempt += 1) {
      try {
        await uploadChunk(formData, md5, index)
        completed += 1
        updateProgress()
        return
      } catch (error) {
        lastError = error
        if (attempt < CHUNK_UPLOAD_RETRY_COUNT) {
          await new Promise(resolve => setTimeout(resolve, CHUNK_UPLOAD_BACKOFF_BASE * attempt))
        }
      }
    }
    throw new Error(`分片 ${index} 上传失败(重试 ${CHUNK_UPLOAD_RETRY_COUNT} 次): ${lastError?.message || lastError}`)
  }

  for (let i = 0; i < pending.length; i += maxConcurrent) {
    const batch = pending.slice(i, i + maxConcurrent)
    await Promise.all(batch.map(uploadSingleChunk))
  }

  const mergeResult = await mergeChunks({
    md5,
    size: file.size,
    cosKey: checkResult.cosKey,
    totalChunks,
    targetSpaceId,
  })
  onProgress?.(100)
  return { status: 'uploaded', ...mergeResult }
}

export async function uploadPictureWithChunks(file, {
  targetSpaceId,
  directUpload,
  onStatus,
  onProgress,
} = {}) {
  if (file.size <= CHUNK_SIZE) {
    return directUpload(file)
  }
  return uploadLargePicture(file, { targetSpaceId, onStatus, onProgress })
}
