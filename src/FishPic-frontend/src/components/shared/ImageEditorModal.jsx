import { useState, useRef, useEffect } from 'react'
import { Modal, Button, App } from 'antd'
import { uploadPicture, checkUpload, uploadChunk, mergeChunks } from '../../api'
import CropperEditor from './CropperEditor'
import './ImageUploadModal.css'

const COS_BASE = import.meta.env.VITE_COS_BASE_URL || ''
const CHUNK_SIZE = 2 * 1024 * 1024
const MAX_CONCURRENT = 5

/** 计算 Blob 的 MD5（读取为 ArrayBuffer 后哈希） */
async function computeBlobMD5(blob) {
  const { default: SparkMD5 } = await import('spark-md5')
  const buffer = await blob.arrayBuffer()
  return SparkMD5.ArrayBuffer.hash(buffer)
}

export default function ImageEditorModal({ open, imageUrl, spaceId, onSuccess, onClose }) {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [localSrc, setLocalSrc] = useState('')
  const cropperRef = useRef(null)
  const objectUrlRef = useRef(null)

  // 通过 Vite proxy 获取图片并转为 blob URL，避免 COS 跨域问题
  useEffect(() => {
    if (!open || !imageUrl) {
      setLocalSrc('')
      return
    }
    const proxySrc = imageUrl.startsWith(COS_BASE)
      ? imageUrl.replace(COS_BASE, '/cos-proxy')
      : imageUrl

    fetch(proxySrc)
      .then((res) => {
        if (!res.ok) throw new Error(`图片加载失败: ${res.status}`)
        return res.blob()
      })
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        objectUrlRef.current = url
        setLocalSrc(url)
      })
      .catch(() => {
        setLocalSrc(proxySrc)
      })

    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current)
        objectUrlRef.current = null
      }
      setLocalSrc('')
    }
  }, [open, imageUrl])

  const handleSave = async () => {
    const cropper = cropperRef.current?.getCropper()
    if (!cropper) return
    setLoading(true)
    try {
      const canvas = cropper.getCroppedCanvas()
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/webp'))
      if (!blob) {
        message.error('裁剪失败')
        setLoading(false)
        return
      }

      if (blob.size <= CHUNK_SIZE) {
        await uploadPicture(blob, spaceId)
      } else {
        const md5 = await computeBlobMD5(blob)
        const file = new File([blob], 'cropped.webp', { type: 'image/webp' })
        const checkResult = await checkUpload({ md5, size: file.size, targetSpaceId: spaceId })
        if (checkResult.status === 'duplicate') {
          // 秒传成功
        } else {
          const cosKey = checkResult.cosKey
          const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
          const uploadedChunks = new Set(checkResult.uploadedChunks || [])
          const pending = []
          for (let i = 0; i < totalChunks; i++) {
            if (!uploadedChunks.has(i)) pending.push(i)
          }
          for (let i = 0; i < pending.length; i += MAX_CONCURRENT) {
            const batch = pending.slice(i, i + MAX_CONCURRENT)
            await Promise.all(batch.map(idx => {
              const start = idx * CHUNK_SIZE
              const end = Math.min(start + CHUNK_SIZE, file.size)
              const chunk = file.slice(start, end)
              const chunkFile = new File([chunk], `chunk_${idx}`, { type: file.type })
              const fd = new FormData()
              fd.append('file', chunkFile)
              return uploadChunk(fd, md5, idx)
            }))
          }
          await mergeChunks({ md5, size: file.size, cosKey, totalChunks, targetSpaceId: spaceId })
        }
      }

      message.success('上传成功')
      onSuccess?.()
      onClose()
    } catch (error) {
      message.error(error?.message || '裁剪上传失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title="编辑图片"
      open={open}
      onCancel={onClose}
      footer={null}
      width={700}
      destroyOnHidden
    >
      <div className="image-cropper-body">
        <CropperEditor ref={cropperRef} src={localSrc} />
        <div className="image-cropper-actions">
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={loading} onClick={handleSave}>
            保存
          </Button>
        </div>
      </div>
    </Modal>
  )
}
