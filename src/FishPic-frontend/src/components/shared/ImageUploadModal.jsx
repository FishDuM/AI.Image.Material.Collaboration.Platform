import { useState, useRef, useEffect, useCallback } from 'react'
import { Modal, Upload, Button, Space, App, Tabs, Input, Image, Empty } from 'antd'
import {
  InboxOutlined, RotateLeftOutlined, RotateRightOutlined,
  ZoomInOutlined, ZoomOutOutlined, LinkOutlined, ImportOutlined, EyeOutlined,
} from '@ant-design/icons'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { uploadPicture, savePictureByUrl } from '../../api'
import { isAllowedImageFile, getMaxUploadSize, formatMaxUploadSize, BROWSER_RENDERABLE_TYPES } from '../../utils/uploadConstraints'
import './ImageUploadModal.css'

/** 从文件名推断扩展名 */
function getExt(file) {
  const dot = file.name?.lastIndexOf('.')
  return dot > 0 ? file.name.substring(dot + 1).toLowerCase() : ''
}

/** 浏览器能否渲染该图片（能渲才能裁剪） */
function canBrowserRender(file) {
  if (BROWSER_RENDERABLE_TYPES.includes(file.type)) return true
  const ext = getExt(file)
  return BROWSER_RENDERABLE_TYPES.some(t => t === `image/${ext}`)
}

export default function ImageUploadModal({ open, onClose, onSuccess, spaceId }) {
  const { message } = App.useApp()
  const [step, setStep] = useState('select') // 'select' | 'crop'
  const [activeTab, setActiveTab] = useState('upload') // 'upload' | 'url'
  const [selectedFile, setSelectedFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const imgRef = useRef(null)
  const cropperRef = useRef(null)
  const objectUrlRef = useRef('')
  const maxSize = getMaxUploadSize()
  const maxSizeText = formatMaxUploadSize()

  // URL 导入状态
  const [url, setUrl] = useState('')
  const [previewUrl, setPreviewUrl] = useState('')
  const [previewError, setPreviewError] = useState(false)
  const [urlResolved, setUrlResolved] = useState(false)

  // 重置所有状态
  const resetState = useCallback(() => {
    setStep('select')
    setActiveTab('upload')
    setSelectedFile(null)
    setUploading(false)
    cropperRef.current?.destroy()
    cropperRef.current = null
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
      objectUrlRef.current = ''
    }
    setUrl('')
    setPreviewUrl('')
    setPreviewError(false)
    setUrlResolved(false)
  }, [])

  useEffect(() => {
    if (!open) resetState()
  }, [open, resetState])

  // 初始化 Cropper
  useEffect(() => {
    if (step !== 'crop' || !imgRef.current || !objectUrlRef.current) return
    cropperRef.current?.destroy()
    cropperRef.current = new Cropper(imgRef.current, {
      viewMode: 1,
      autoCropArea: 1,
      dragMode: 'crop',
      background: false,
    })
    return () => {
      cropperRef.current?.destroy()
      cropperRef.current = null
    }
  }, [step])

  const directUpload = async (file) => {
    setUploading(true)
    try {
      const fd = new FormData()
      fd.append('file', file)
      const result = await uploadPicture(fd, spaceId)
      message.success('上传成功')
      onSuccess?.({ url: result.url, id: result.id })
      onClose()
    } catch (error) {
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const beforeUpload = useCallback((file) => {
    if (!isAllowedImageFile(file)) {
      message.error('不支持的图片格式！')
      return Upload.LIST_IGNORE
    }
    if (file.size > maxSize) {
      message.error(`图片大小不能超过${maxSizeText}！`)
      return Upload.LIST_IGNORE
    }
    // 浏览器不支持的格式（HEIC、TIFF、RAW 等）跳过裁剪直接上传
    if (!canBrowserRender(file)) {
      directUpload(file)
      return false
    }
    setSelectedFile(file)
    objectUrlRef.current = URL.createObjectURL(file)
    setStep('crop')
    return false
  }, [message, maxSize, maxSizeText])

  const handleRotate = (degree) => {
    const cropper = cropperRef.current
    if (!cropper) return
    const { x, y, width, height } = cropper.getData()
    cropper.rotate(degree)
    cropper.setData({ x, y, width, height })
  }

  const handleCropUpload = async () => {
    const cropper = cropperRef.current
    if (!cropper) return
    setUploading(true)
    try {
      const canvas = cropper.getCroppedCanvas()
      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/webp'))
      const fd = new FormData()
      fd.append('file', blob, selectedFile.name.replace(/\.[^.]+$/, '.webp'))
      const result = await uploadPicture(fd, spaceId)
      message.success('上传成功')
      onSuccess?.({ url: result.url, id: result.id })
      onClose()
    } catch (error) {
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const handleBack = () => {
    setStep('select')
    setSelectedFile(null)
    cropperRef.current?.destroy()
    cropperRef.current = null
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
      objectUrlRef.current = ''
    }
  }

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText()
      if (text) setUrl(text)
    } catch {
      message.warning('无法读取剪贴板')
    }
  }

  const handleUrlResolve = () => {
    if (!url.trim()) { message.warning('请输入图片URL'); return }
    setPreviewUrl(url.trim())
    setPreviewError(false)
    setUrlResolved(true)
  }

  const handleUrlConfirm = async () => {
    if (!previewUrl) { message.warning('请先解析图片URL'); return }
    setUploading(true)
    try {
      const result = await savePictureByUrl(previewUrl, spaceId)
      message.success('图片导入成功')
      onSuccess?.({ url: result.url, id: result.id })
      onClose()
    } catch (e) {
      message.error(e.message || '导入失败')
    } finally {
      setUploading(false)
    }
  }

  const handleUrlCancel = () => {
    setUrl('')
    setPreviewUrl('')
    setPreviewError(false)
    setUrlResolved(false)
  }

  const handleTabChange = (key) => {
    setActiveTab(key)
    if (key === 'url') {
      setUrl('')
      setPreviewUrl('')
      setPreviewError(false)
      setUrlResolved(false)
    }
  }

  // 裁剪模式：不显示 Tabs，只显示裁剪 UI
  if (step === 'crop') {
    return (
      <Modal
        title="裁剪图片"
        open={open}
        onCancel={onClose}
        footer={null}
        width={700}
        destroyOnHidden
      >
        <div className="image-cropper-body">
          <div className="image-cropper-container">
            <img ref={imgRef} src={objectUrlRef.current} alt="裁剪预览" />
          </div>
          <div className="image-cropper-toolbar">
            <Space>
              <Button icon={<ZoomOutOutlined />} onClick={() => cropperRef.current?.zoom(-0.1)} size="small" title="缩小" />
              <Button icon={<ZoomInOutlined />} onClick={() => cropperRef.current?.zoom(0.1)} size="small" title="放大" />
              <Button icon={<RotateLeftOutlined />} onClick={() => handleRotate(-90)} size="small" title="左旋转" />
              <Button icon={<RotateRightOutlined />} onClick={() => handleRotate(90)} size="small" title="右旋转" />
            </Space>
          </div>
          <div className="image-cropper-actions">
            <Button onClick={handleBack}>重新选择</Button>
            <Button type="primary" loading={uploading} onClick={handleCropUpload}>
              确认上传
            </Button>
          </div>
        </div>
      </Modal>
    )
  }

  return (
    <Modal
      title="上传图片"
      open={open}
      onCancel={onClose}
      footer={null}
      width={520}
      destroyOnHidden
    >
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        className="image-upload-tabs"
        items={[
          {
            key: 'upload',
            label: '文件上传',
            children: (
              <div className="image-upload-modal-body">
                <Upload.Dragger
                  className="image-upload-dragger"
                  beforeUpload={beforeUpload}
                  maxCount={1}
                  showUploadList={false}
                  accept=".jpeg,.jpg,.png,.gif,.webp,.heic,.heif,.bmp,.tiff,.tif,.avif,.apng,.psd,.ai,.eps,.raw,.dng,.cr3,.crw,.arw,.nef,.orf,.rw2"
                >
                  <p className="ant-upload-drag-icon">
                    <InboxOutlined />
                  </p>
                  <p className="ant-upload-text">点击或拖拽图片到此区域上传</p>
                  <p className="ant-upload-hint">
                    支持 JPG、PNG、GIF、WebP、HEIC、BMP、TIFF、AVIF 及各类 RAW/PSD 等格式，单张图片不超过 {maxSizeText}
                  </p>
                </Upload.Dragger>
              </div>
            ),
          },
          {
            key: 'url',
            label: 'URL 导入',
            children: (
              <div className="image-upload-modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <Input.Search
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="粘贴图片URL..."
                  size="large"
                  allowClear
                  enterButton="解析"
                  onSearch={handleUrlResolve}
                />
                <div>
                  <div className="import-url-label">图片预览</div>
                  <div className="import-url-preview-box">
                    {previewUrl ? (
                      previewError ? (
                        <Empty description="无法预览该图片" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ) : (
                        <Image
                          src={previewUrl}
                          alt="预览"
                          style={{ maxHeight: 240, objectFit: 'contain' }}
                          onError={() => setPreviewError(true)}
                          preview={{ cover: false }}
                        />
                      )
                    ) : (
                      <Empty
                        description="输入URL后点击解析预览图片"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        style={{ margin: '32px 0' }}
                      />
                    )}
                  </div>
                </div>
                {urlResolved && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Button onClick={handleUrlCancel}>取消</Button>
                    <Button
                      type="primary"
                      icon={<ImportOutlined />}
                      onClick={handleUrlConfirm}
                      loading={uploading}
                      size="large"
                    >
                      确认导入
                    </Button>
                  </div>
                )}
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}
