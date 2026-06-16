import { useEffect, useRef, useState } from 'react'
import { App, Button, Modal } from 'antd'
import { replacePictureFile, uploadPicture } from '../../api'
import { uploadPictureWithChunks } from '../../utils/upload'
import CropperEditor from './CropperEditor'
import './ImageUploadModal.css'

const COS_BASE = import.meta.env.VITE_COS_BASE_URL || ''

export default function ImageEditorModal({ open, imageUrl, spaceId, pictureId, onSuccess, onClose }) {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [localSrc, setLocalSrc] = useState('')
  const cropperRef = useRef(null)
  const objectUrlRef = useRef(null)

  useEffect(() => {
    if (!open || !imageUrl) {
      setLocalSrc('')
      return undefined
    }

    const proxySrc = imageUrl.startsWith(COS_BASE)
      ? imageUrl.replace(COS_BASE, '/cos-proxy')
      : imageUrl
    const abortController = new AbortController()

    fetch(proxySrc, { signal: abortController.signal })
      .then((res) => {
        if (!res.ok) throw new Error(`图片加载失败: ${res.status}`)
        return res.blob()
      })
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        objectUrlRef.current = url
        setLocalSrc(url)
      })
      .catch((error) => {
        if (error?.name !== 'AbortError') {
          setLocalSrc(proxySrc)
        }
      })

    return () => {
      abortController.abort()
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
      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/webp'))
      if (!blob) {
        message.error('裁剪失败')
        return
      }

      const file = new File([blob], 'cropped.webp', { type: 'image/webp' })
      if (pictureId) {
        await replacePictureFile(file, pictureId)
      } else {
        await uploadPictureWithChunks(file, {
          targetSpaceId: spaceId,
          directUpload: targetFile => uploadPicture(targetFile, spaceId),
        })
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
    <Modal title="编辑图片" open={open} onCancel={onClose} footer={null} width={700} destroyOnHidden>
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
