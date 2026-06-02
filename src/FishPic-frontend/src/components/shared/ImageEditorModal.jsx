import { useState, useRef, useEffect } from 'react'
import { Modal, Button, App } from 'antd'
import { uploadPicture } from '../../api'
import CropperEditor from './CropperEditor'
import './ImageUploadModal.css'

const COS_BASE = import.meta.env.VITE_COS_BASE_URL || ''

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
      .then((res) => res.blob())
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
      const fd = new FormData()
      fd.append('file', blob, 'cropped-image.webp')
      await uploadPicture(fd, spaceId)
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
