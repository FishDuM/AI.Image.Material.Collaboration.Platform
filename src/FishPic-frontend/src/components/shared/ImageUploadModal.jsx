import { useState, useRef, useEffect, useCallback } from 'react'
import { Modal, Upload, Button, App, Tabs, Input, Image, Empty, Progress } from 'antd'
import {
  InboxOutlined, ImportOutlined,
} from '@ant-design/icons'
import SparkMD5 from 'spark-md5'
import { uploadPicture, savePictureByUrl, checkUpload, uploadChunk, mergeChunks } from '../../api'
import { isAllowedImageFile, getMaxUploadSize, formatMaxUploadSize, BROWSER_RENDERABLE_TYPES } from '../../utils/uploadConstraints'
import CropperEditor from './CropperEditor'
import './ImageUploadModal.css'

/** 分片大小 2MB */
const CHUNK_SIZE = 2 * 1024 * 1024
/** 最大并发上传分片数 */
const MAX_CONCURRENT = 5
function getExt(file) {
  const dot = file.name?.lastIndexOf('.')
  return dot > 0 ? file.name.substring(dot + 1).toLowerCase() : ''
}

/** 浏览器能否渲染该图片 */
function canBrowserRender(file) {
  if (BROWSER_RENDERABLE_TYPES.includes(file.type)) return true
  const ext = getExt(file)
  return BROWSER_RENDERABLE_TYPES.some(t => t === `image/${ext}`)
}

/** 计算文件 MD5（流式） */
function computeFileMD5(file) {
  return new Promise((resolve, reject) => {
    const blobSlice = File.prototype.slice
    const chunks = Math.ceil(file.size / CHUNK_SIZE)
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    let currentChunk = 0

    reader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk++
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
      reader.readAsArrayBuffer(blobSlice.call(file, start, end))
    }
    loadNext()
  })
}

export default function ImageUploadModal({ open, onClose, onSuccess, spaceId }) {
  const { message } = App.useApp()
  const [step, setStep] = useState('select') // 'select' | 'crop'
  const [activeTab, setActiveTab] = useState('upload') // 'upload' | 'url'
  const [selectedFile, setSelectedFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const cropperRef = useRef(null)
  const objectUrlRef = useRef('')
  const maxSize = getMaxUploadSize()
  const maxSizeText = formatMaxUploadSize()

  // URL 导入状态
  const [url, setUrl] = useState('')
  const [previewUrl, setPreviewUrl] = useState('')
  const [previewError, setPreviewError] = useState(false)
  const [urlResolved, setUrlResolved] = useState(false)

  // 分片上传进度
  const [uploadProgress, setUploadProgress] = useState(0)
  const [uploadStatus, setUploadStatus] = useState('') // 'md5' | 'uploading' | 'duplicate' | ''

  // 重置所有状态
  const resetState = useCallback(() => {
    setStep('select')
    setActiveTab('upload')
    setSelectedFile(null)
    setUploading(false)
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }
    objectUrlRef.current = ''
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

  /**
   * 分片上传大文件
   */
  const chunkUpload = async (file) => {
    setUploading(true)
    setUploadStatus('md5')
    setUploadProgress(0)
    try {
      // 1. 计算 MD5
      const md5 = await computeFileMD5(file)
      setUploadStatus('')

      // 2. 秒传校验
      const checkResult = await checkUpload({ md5, size: file.size, targetSpaceId: spaceId })

      if (checkResult.status === 'duplicate') {
        setUploadStatus('duplicate')
        message.success('秒传成功！')
        onSuccess?.({ url: checkResult.picture.url, id: checkResult.picture.id })
        onClose()
        return
      }

      setUploadStatus('uploading')
      const cosKey = checkResult.cosKey
      const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
      let uploadedChunks = new Set(checkResult.uploadedChunks || [])

      // 3. 并发分片上传
      const pending = []
      for (let i = 0; i < totalChunks; i++) {
        if (uploadedChunks.has(i)) continue
        pending.push(i)
      }

      // 并发上传（最多 MAX_CONCURRENT 个）
      const results = new Array(totalChunks)
      let completed = uploadedChunks.size

      const updateProgress = () => {
        setUploadProgress(Math.round((completed / totalChunks) * 100))
      }
      updateProgress()

      // 分片上传带重试
      const uploadSingleChunk = async (index) => {
        const start = index * CHUNK_SIZE
        const end = Math.min(start + CHUNK_SIZE, file.size)
        const chunk = file.slice(start, end)
        const chunkFile = new File([chunk], `chunk_${index}`, { type: file.type })
        const fd = new FormData()
        fd.append('file', chunkFile)
        let lastErr
        for (let attempt = 1; attempt <= 3; attempt++) {
          try {
            const result = await uploadChunk(fd, md5, index)
            results[index] = result
            completed++
            updateProgress()
            return
          } catch (e) {
            lastErr = e
            if (attempt < 3) {
              // 退避:500ms / 1500ms
              await new Promise(r => setTimeout(r, 500 * attempt))
            }
          }
        }
        throw new Error(`分片 ${index} 上传失败(重试 3 次): ${lastErr?.message || lastErr}`)
      }

      // 分批并发
      for (let i = 0; i < pending.length; i += MAX_CONCURRENT) {
        const batch = pending.slice(i, i + MAX_CONCURRENT)
        await Promise.all(batch.map(idx => uploadSingleChunk(idx)))
      }

      // 4. 合并分片
      const mergeResult = await mergeChunks({
        md5,
        size: file.size,
        cosKey,
        totalChunks,
        targetSpaceId: spaceId,
      })

      message.success('上传成功')
      onSuccess?.({ url: mergeResult.url, id: mergeResult.id })
      onClose()
    } catch (error) {
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
      setUploadStatus('')
      setUploadProgress(0)
    }
  }

  /**
   * 统一上传入口：小文件直接上传，大文件走分片
   */
  const directUpload = async (file) => {
    if (file.size <= CHUNK_SIZE) {
      setUploading(true)
      try {
        const result = await uploadPicture(file, spaceId)
        message.success('上传成功')
        onSuccess?.({ url: result.url, id: result.id })
        onClose()
      } catch (error) {
        message.error(error.message || '上传失败')
      } finally {
        setUploading(false)
      }
    } else {
      await chunkUpload(file)
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

  const handleCropUpload = async () => {
    const cropper = cropperRef.current?.getCropper()
    if (!cropper) return
    setUploading(true)
    try {
      const canvas = cropper.getCroppedCanvas()
      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/webp'))
      if (!blob) {
        message.warning('裁剪区域为空，请重新选择裁剪区域')
        return
      }
      await directUpload(blob)
    } catch (error) {
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const handleBack = () => {
    setStep('select')
    setSelectedFile(null)
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
    }
    objectUrlRef.current = ''
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
          <CropperEditor ref={cropperRef} src={objectUrlRef.current} />
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
                {uploading && (
                  <div style={{ marginBottom: 16 }}>
                    {uploadStatus === 'md5' && (
                      <div style={{ textAlign: 'center', color: '#1890ff' }}>正在计算文件指纹...</div>
                    )}
                    {uploadStatus === 'duplicate' && (
                      <div style={{ textAlign: 'center', color: '#52c41a' }}>秒传成功！</div>
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
