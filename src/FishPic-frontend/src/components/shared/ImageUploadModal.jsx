import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Button, Empty, Image, Input, Modal, Progress, Tabs, Upload } from 'antd'
import { ImportOutlined, InboxOutlined } from '@ant-design/icons'
import { savePictureByUrl, uploadPicture } from '../../api'
import { uploadPictureWithChunks } from '../../utils/upload'
import {
  BROWSER_RENDERABLE_TYPES,
  formatMaxUploadSize,
  getMaxUploadSize,
  validateImageUpload,
} from '../../utils/uploadConstraints'
import CropperEditor from './CropperEditor'
import './ImageUploadModal.css'

function getExt(file) {
  const dot = file.name?.lastIndexOf('.')
  return dot > 0 ? file.name.substring(dot + 1).toLowerCase() : ''
}

function canBrowserRender(file) {
  if (BROWSER_RENDERABLE_TYPES.includes(file.type)) return true
  const ext = getExt(file)
  return BROWSER_RENDERABLE_TYPES.some(type => type === `image/${ext}`)
}

export default function ImageUploadModal({ open, onClose, onSuccess, spaceId }) {
  const { message } = App.useApp()
  const [step, setStep] = useState('select')
  const [activeTab, setActiveTab] = useState('upload')
  const [uploading, setUploading] = useState(false)
  const [objectUrl, setObjectUrl] = useState('')
  const [url, setUrl] = useState('')
  const [previewUrl, setPreviewUrl] = useState('')
  const [previewError, setPreviewError] = useState(false)
  const [urlResolved, setUrlResolved] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploadStatus, setUploadStatus] = useState('')
  const cropperRef = useRef(null)
  const objectUrlRef = useRef('')
  const maxSize = getMaxUploadSize()
  const maxSizeText = formatMaxUploadSize()

  const resetState = useCallback(() => {
    setStep('select')
    setActiveTab('upload')
    setUploading(false)
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }
    objectUrlRef.current = ''
    setObjectUrl('')
    setUrl('')
    setPreviewUrl('')
    setPreviewError(false)
    setUrlResolved(false)
    setUploadProgress(0)
    setUploadStatus('')
  }, [])

  useEffect(() => {
    if (!open) resetState()
  }, [open, resetState])

  const handlePictureUpload = useCallback(async (file) => {
    setUploading(true)
    try {
      const result = await uploadPictureWithChunks(file, {
        targetSpaceId: spaceId,
        directUpload: targetFile => uploadPicture(targetFile, spaceId),
        onStatus: setUploadStatus,
        onProgress: setUploadProgress,
      })
      const picture = result?.picture || result
      message.success(result?.status === 'duplicate' ? '秒传成功' : '上传成功')
      onSuccess?.({ url: picture?.url, id: picture?.id })
      onClose()
    } catch (error) {
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
      setUploadStatus('')
      setUploadProgress(0)
    }
  }, [message, onClose, onSuccess, spaceId])

  const beforeUpload = useCallback((file) => {
    const validation = validateImageUpload(file)
    if (!validation.valid) {
      message.error(validation.message)
      return Upload.LIST_IGNORE
    }

    if (!canBrowserRender(file)) {
      handlePictureUpload(file)
      return false
    }

    const nextObjectUrl = URL.createObjectURL(file)
    objectUrlRef.current = nextObjectUrl
    setObjectUrl(nextObjectUrl)
    setStep('crop')
    return false
  }, [handlePictureUpload, message])

  const handleCropUpload = async () => {
    const cropper = cropperRef.current?.getCropper()
    if (!cropper) return

    const canvas = cropper.getCroppedCanvas()
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/webp'))
    if (!blob) {
      message.warning('裁剪区域为空，请重新选择裁剪区域')
      return
    }
    await handlePictureUpload(new File([blob], 'cropped.webp', { type: 'image/webp' }))
  }

  const handleBack = () => {
    setStep('select')
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }
    objectUrlRef.current = ''
    setObjectUrl('')
  }

  const handleUrlResolve = () => {
    if (!url.trim()) {
      message.warning('请输入图片 URL')
      return
    }
    setPreviewUrl(url.trim())
    setPreviewError(false)
    setUrlResolved(true)
  }

  const handleUrlConfirm = async () => {
    if (!previewUrl) {
      message.warning('请先解析图片 URL')
      return
    }
    setUploading(true)
    try {
      const result = await savePictureByUrl(previewUrl, spaceId)
      message.success('图片导入成功')
      onSuccess?.({ url: result.url, id: result.id })
      onClose()
    } catch (error) {
      message.error(error.message || '导入失败')
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
      handleUrlCancel()
    }
  }

  if (step === 'crop') {
    return (
      <Modal title="裁剪图片" open={open} onCancel={onClose} footer={null} width={700} destroyOnHidden>
        <div className="image-cropper-body">
          <CropperEditor ref={cropperRef} src={objectUrl} />
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
    <Modal title="上传图片" open={open} onCancel={onClose} footer={null} width={520} destroyOnHidden>
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
                {uploading && (
                  <div style={{ marginBottom: 16 }}>
                    {uploadStatus === 'md5' && (
                      <div style={{ textAlign: 'center', color: '#1890ff' }}>正在计算文件指纹...</div>
                    )}
                    {uploadStatus === 'duplicate' && (
                      <div style={{ textAlign: 'center', color: '#52c41a' }}>秒传成功</div>
                    )}
                    {uploadStatus === 'uploading' && (
                      <Progress percent={uploadProgress} status="active" strokeColor={{ from: '#108ee9', to: '#87d068' }} />
                    )}
                  </div>
                )}
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
                    支持 JPG、PNG、GIF、WebP、HEIC、BMP、TIFF、AVIF 及 RAW/PSD 等格式，单张图片不超过 {maxSizeText}
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
                  placeholder="粘贴图片 URL..."
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
                        description="输入 URL 后点击解析预览图片"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        style={{ margin: '32px 0' }}
                      />
                    )}
                  </div>
                </div>
                {urlResolved && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Button onClick={handleUrlCancel}>取消</Button>
                    <Button type="primary" icon={<ImportOutlined />} onClick={handleUrlConfirm} loading={uploading} size="large">
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
