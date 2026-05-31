import { useState, useRef, useEffect, useCallback } from 'react'
import { Modal, Button, Space, App } from 'antd'
import { RotateLeftOutlined, RotateRightOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { uploadPicture } from '../../api'
import './ImageUploadModal.css'

const COS_BASE = 'https://fish-picture-1333236187.cos.ap-guangzhou.myqcloud.com'

export default function ImageEditorModal({ open, imageUrl, spaceId, onSuccess, onClose }) {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [localSrc, setLocalSrc] = useState('')
  const [cropperReady, setCropperReady] = useState(false)
  const imgRef = useRef(null)
  const cropperRef = useRef(null)
  const objectUrlRef = useRef(null)

  const destroyCropper = useCallback(() => {
    cropperRef.current?.destroy()
    cropperRef.current = null
  }, [])

  // 通过 Vite proxy 获取图片并转为 blob URL，避免 COS 跨域问题
  useEffect(() => {
    if (!open || !imageUrl) {
      setLocalSrc('')
      return
    }
    const proxySrc = imageUrl.startsWith(COS_BASE)
      ? imageUrl.replace(COS_BASE, '/cos-proxy')
      : imageUrl

    setCropperReady(false)
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

  // 图片加载完成后初始化 cropper
  useEffect(() => {
    if (!open || !localSrc) {
      destroyCropper()
      return
    }
    const img = imgRef.current
    if (!img) return

    const init = () => {
      destroyCropper()
      cropperRef.current = new Cropper(img, {
        viewMode: 1,
        autoCropArea: 1,
        dragMode: 'crop',
        background: false,
        ready: () => setCropperReady(true),
      })
    }

    if (img.complete) {
      init()
    } else {
      img.addEventListener('load', init)
    }

    return () => {
      img.removeEventListener('load', init)
      destroyCropper()
    }
  }, [open, localSrc, destroyCropper])

  const handleRotate = (degree) => {
    const cropper = cropperRef.current
    if (!cropper) return
    const { x, y, width, height } = cropper.getData()
    cropper.rotate(degree)
    cropper.setData({ x, y, width, height })
  }

  const handleSave = async () => {
    const cropper = cropperRef.current
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
        <div className="image-cropper-container" style={{ opacity: cropperReady ? 1 : 0 }}>
          {localSrc ? (
            <img ref={imgRef} src={localSrc} alt="编辑预览" />
          ) : (
            <div className="image-cropper-loading">加载中...</div>
          )}
        </div>
        {localSrc && cropperReady && (
          <div className="image-cropper-toolbar">
            <Space>
              <Button icon={<ZoomOutOutlined />} onClick={() => cropperRef.current?.zoom(-0.1)} size="small" title="缩小" />
              <Button icon={<ZoomInOutlined />} onClick={() => cropperRef.current?.zoom(0.1)} size="small" title="放大" />
              <Button icon={<RotateLeftOutlined />} onClick={() => handleRotate(-90)} size="small" title="左旋转" />
              <Button icon={<RotateRightOutlined />} onClick={() => handleRotate(90)} size="small" title="右旋转" />
            </Space>
          </div>
        )}
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
