import { useState } from 'react'
import { Modal, Upload, App } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { uploadPicture } from '../../api'
import { isAllowedImageFile, getMaxUploadSize, formatMaxUploadSize } from '../../utils/uploadConstraints'
import './ImageUploadModal.css'

export default function ImageUploadModal({ open, onClose, onSuccess, spaceId }) {
  const { message } = App.useApp()
  const [uploading, setUploading] = useState(false)
  const maxSize = getMaxUploadSize()
  const maxSizeText = formatMaxUploadSize()

  const beforeUpload = (file) => {
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

  const handleUpload = async ({ file, onSuccess: onUploadSuccess, onError }) => {
    setUploading(true)
    const formData = new FormData()
    formData.append('file', file)
    if (spaceId != null) {
      formData.append('targetSpaceId', spaceId)
    }

    try {
      const result = await uploadPicture(formData, spaceId)
      const { url, id } = result
      onUploadSuccess()
      message.success('上传成功')
      onSuccess?.({ url, id })
    } catch (error) {
      onError(error)
      message.error(error.message || '上传失败')
    } finally {
      setUploading(false)
    }
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
      <div className="image-upload-modal-body">
        <Upload.Dragger
          className="image-upload-dragger"
          customRequest={handleUpload}
          maxCount={1}
          beforeUpload={beforeUpload}
          accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
          showUploadList={false}
          disabled={uploading}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽图片到此区域上传</p>
          <p className="ant-upload-hint">
            支持 JPG、PNG、GIF、WebP、HEIC 格式，单张图片不超过 {maxSizeText}
          </p>
        </Upload.Dragger>
      </div>
    </Modal>
  )
}
