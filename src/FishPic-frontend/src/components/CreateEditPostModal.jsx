import { useState, useEffect } from 'react'
import { App, Modal, Form, Input, Button, Upload, Select, Switch, Image as AntImage } from 'antd'
import { PlusOutlined, DeleteOutlined, LeftOutlined, RightOutlined, SendOutlined } from '@ant-design/icons'
import api from '../api'
import './CreateEditPostModal.css'

const CHINESE_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二', '十三', '十四', '十五']

const ALLOWED_IMAGE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/jpg',
  'image/gif',
  'image/webp',
  'image/heic',
]

const CreateEditPostModal = ({ open, onClose, editPostDetail, onSuccess }) => {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [submitLoading, setSubmitLoading] = useState(false)
  const [uploadedImages, setUploadedImages] = useState([])
  const [imageId, setImageId] = useState([])
  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const [showUploadSlide, setShowUploadSlide] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editingPostId, setEditingPostId] = useState(null)
  const [modalStep, setModalStep] = useState(1)
  const [touchStartX, setTouchStartX] = useState(0)
  const [touchEndX, setTouchEndX] = useState(0)

  useEffect(() => {
    if (open && editPostDetail) {
      setIsEditing(true)
      setEditingPostId(editPostDetail.id)
      const existingPics = (editPostDetail.pics || editPostDetail.pictureUrl || []).filter(url => url && url.trim())
      const existingImages = existingPics.map((url, index) => ({
        uid: `existing-${index}`,
        name: `image-${index}`,
        status: 'done',
        url,
        pictureId: null,
      }))
      setUploadedImages(existingImages)
      setImageId([])
      setCurrentImageIndex(0)
      setShowUploadSlide(false)
      setModalStep(2)
      form.setFieldsValue({
        title: editPostDetail.title || '',
        content: editPostDetail.content || '',
      })
    } else if (open) {
      setIsEditing(false)
      setEditingPostId(null)
      setUploadedImages([])
      setImageId([])
      setCurrentImageIndex(0)
      setShowUploadSlide(false)
      setModalStep(1)
      form.resetFields()
    }
  }, [open, editPostDetail, form])

  const beforeUpload = (file) => {
    const isAllowedImage = ALLOWED_IMAGE_TYPES.includes(file.type)
    if (!isAllowedImage) {
      message.error('只能上传图片文件（JPEG、PNG、JPG、GIF、WebP、HEIC）！')
    }
    const isLt5M = file.size / 1024 / 1024 < 5
    if (!isLt5M) {
      message.error('图片大小不能超过5MB！')
    }
    return isAllowedImage && isLt5M
  }

  const handleImageUpload = async ({ file, onSuccess: onUploadSuccess, onError }) => {
    if (imageId.length >= 15) {
      message.error('最多只能上传15张图片')
      onError(new Error('超过图片数量限制'))
      return
    }

    const formData = new FormData()
    formData.append('file', file)

    try {
      const result = await api.post('/picture/post', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      const { url, pictureId } = result
      onUploadSuccess({ url, pictureId })
      message.success('上传成功')

      setUploadedImages(prev => [...prev, { uid: file.uid, name: file.name, status: 'done', url, pictureId }])
      setImageId(prev => [...prev, pictureId])
      setModalStep(2)
      if (showUploadSlide) {
        setCurrentImageIndex(prev => prev + 1)
        setShowUploadSlide(false)
      }
    } catch (error) {
      onError(error)
      message.error(error.message || '上传失败')
    }
  }

  const customUploadRequest = (options) => {
    handleImageUpload(options)
  }

  const handleImageRemove = (file) => {
    if (isEditing && !file.pictureId) return false
    const removedIndex = uploadedImages.findIndex(img => img.uid === file.uid)
    setUploadedImages(prev => {
      const next = prev.filter(img => img.uid !== file.uid)
      const currentCover = form.getFieldValue('coverIndex') ?? 0
      if (next.length > 0) {
        let newCover = currentCover
        if (removedIndex === currentCover) {
          newCover = Math.min(currentCover, next.length - 1)
        } else if (removedIndex < currentCover) {
          newCover = currentCover - 1
        }
        form.setFieldsValue({ coverIndex: newCover })
      } else {
        form.setFieldsValue({ coverIndex: 0 })
      }
      return next
    })
    const removedPicture = uploadedImages.find(img => img.uid === file.uid)
    if (removedPicture) {
      setImageId(prev => prev.filter(id => id !== removedPicture.pictureId))
    }
    if (currentImageIndex >= uploadedImages.length - 1) {
      setCurrentImageIndex(Math.max(0, uploadedImages.length - 2))
    }
    setShowUploadSlide(false)
  }

  const handleNextImage = () => {
    if (currentImageIndex === uploadedImages.length - 1 && uploadedImages.length < 15) {
      setShowUploadSlide(true)
    } else {
      setCurrentImageIndex(prev => Math.min(uploadedImages.length - 1, prev + 1))
      setShowUploadSlide(false)
    }
  }

  const handlePrevImage = () => {
    setShowUploadSlide(false)
    setCurrentImageIndex(prev => Math.max(0, prev - 1))
  }

  const handleTouchStart = (e) => {
    setTouchStartX(e.touches[0].clientX)
  }

  const handleTouchMove = (e) => {
    setTouchEndX(e.touches[0].clientX)
  }

  const handleTouchEnd = () => {
    const diff = touchStartX - touchEndX
    const threshold = 50
    if (Math.abs(diff) > threshold) {
      if (diff > 0) {
        if (currentImageIndex === uploadedImages.length - 1 && uploadedImages.length < 15) {
          setShowUploadSlide(true)
        } else {
          setCurrentImageIndex(prev => Math.min(uploadedImages.length - 1, prev + 1))
          setShowUploadSlide(false)
        }
      } else if (diff < 0) {
        if (showUploadSlide) {
          setShowUploadSlide(false)
        } else {
          setCurrentImageIndex(prev => Math.max(0, prev - 1))
        }
      }
    }
  }

  const handleSubmit = async (values) => {
    setSubmitLoading(true)
    try {
      if (isEditing) {
        const editData = {
          id: editingPostId,
          title: values.title,
          content: values.content,
          isPrivate: values.isPrivate ? 1 : 0,
        }
        const coverIndex = values.coverIndex ?? 0
        const selectedImage = uploadedImages[coverIndex]
        if (selectedImage?.pictureId) {
          editData.cover = selectedImage.pictureId
        }
        if (imageId.length > 0) {
          editData.imageId = imageId
        }
        await api.post('/post/editPost', editData)
        message.success('编辑成功！')
      } else {
        const coverIndex = values.coverIndex ?? 0
        const submitData = {
          imageId: imageId,
          title: values.title,
          content: values.content,
          cover: imageId[coverIndex] || imageId[0] || null,
          isPrivate: values.isPrivate ? 1 : 0
        }
        await api.post('/post/post', submitData)
        message.success('发布成功！')
      }
      form.resetFields()
      onClose()
      setUploadedImages([])
      setImageId([])
      setModalStep(1)
      setIsEditing(false)
      setEditingPostId(null)
      if (onSuccess) {
        onSuccess()
      }
    } catch (err) {
      message.error(err.message || (isEditing ? '编辑失败，请重试' : '发布失败，请重试'))
    } finally {
      setSubmitLoading(false)
    }
  }

  const handleCancel = () => {
    form.resetFields()
    setUploadedImages([])
    setImageId([])
    setModalStep(1)
    setIsEditing(false)
    setEditingPostId(null)
    onClose()
  }

  return (
    <Modal
      open={open}
      onCancel={handleCancel}
      footer={null}
      closable={false}
      className="create-post-modal"
      width={900}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} autoComplete="off" className="post-form">
      {modalStep === 1 ? (
        <div className="upload-step">
          <div className="upload-hint-text">发布帖子需要先上传图片</div>
          <Upload
            listType="picture-card"
            className="image-dragger"
            customRequest={customUploadRequest}
            onRemove={handleImageRemove}
            fileList={uploadedImages}
            maxCount={15}
            beforeUpload={beforeUpload}
            accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
            multiple={false}
            showUploadList={false}
          >
            <div className="upload-step-content">
              <PlusOutlined className="upload-step-icon" />
              <div className="upload-step-title">点击或拖拽图片到此区域上传</div>
              <div className="upload-step-desc">支持 JPG、PNG、GIF 格式，单张图片不超过 5MB，最多15张</div>
            </div>
          </Upload>
        </div>
      ) : (
        <div className="xiaohongshu-layout">
          <div className="left-image-area">
            {uploadedImages.length > 0 ? (
              <div
                className="carousel-main"
                onTouchStart={handleTouchStart}
                onTouchMove={handleTouchMove}
                onTouchEnd={handleTouchEnd}
              >
                {showUploadSlide && uploadedImages.length < 15 ? (
                  <Upload
                    listType="picture-card"
                    className="carousel-upload"
                    customRequest={customUploadRequest}
                    onRemove={handleImageRemove}
                    fileList={uploadedImages}
                    maxCount={15}
                    showUploadList={false}
                    beforeUpload={beforeUpload}
                    accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
                  >
                    <button type="button" className="carousel-upload-btn">
                      <PlusOutlined />
                      <div className="upload-text">继续上传</div>
                    </button>
                  </Upload>
                ) : currentImageIndex < uploadedImages.length ? (
                  <AntImage
                    src={uploadedImages[currentImageIndex]?.url}
                    alt={uploadedImages[currentImageIndex]?.name}
                    className="carousel-main-image"
                    preview={true}
                  />
                ) : null}
                {(currentImageIndex < uploadedImages.length || showUploadSlide) && (
                  <>
                    <button
                      type="button"
                      className="carousel-remove-btn"
                      onClick={() => !showUploadSlide && handleImageRemove(uploadedImages[currentImageIndex])}
                      style={{ display: showUploadSlide ? 'none' : (isEditing && !uploadedImages[currentImageIndex]?.pictureId) ? 'none' : 'flex' }}
                    >
                      <DeleteOutlined />
                    </button>
                    <button
                      type="button"
                      className="carousel-arrow carousel-arrow-left"
                      onClick={handlePrevImage}
                      disabled={currentImageIndex === 0 && !showUploadSlide}
                    >
                      <LeftOutlined />
                    </button>
                    <button
                      type="button"
                      className="carousel-arrow carousel-arrow-right"
                      onClick={handleNextImage}
                      disabled={uploadedImages.length >= 15 && !showUploadSlide}
                      style={{ display: showUploadSlide ? 'none' : 'flex' }}
                    >
                      <RightOutlined />
                    </button>
                    <div className="carousel-counter" style={{ display: showUploadSlide ? 'none' : 'block' }}>
                      {currentImageIndex + 1} / {uploadedImages.length}
                    </div>
                  </>
                )}
              </div>
            ) : (
              <Upload
                listType="picture-card"
                className="upload-preview-inline"
                customRequest={customUploadRequest}
                onRemove={handleImageRemove}
                fileList={uploadedImages}
                maxCount={15}
                showUploadList={false}
                beforeUpload={beforeUpload}
                accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
              >
                <button type="button" className="upload-trigger-inline">
                  <PlusOutlined />
                  <div className="upload-text">上传图片</div>
                </button>
              </Upload>
            )}
          </div>
          <div className="right-form-area">
              <Form.Item
                name="title"
                rules={[
                  { required: true, message: '请输入标题' },
                  { max: 50, message: '标题最多50个字' }
                ]}
              >
                <Input placeholder="给帖子起个吸引人的标题吧~" size="large" />
              </Form.Item>

              <Form.Item
                name="content"
                rules={[
                  { required: true, message: '请输入正文' },
                  { max: 5000, message: '正文最多5000个字' }
                ]}
              >
                <Input.TextArea
                  placeholder="写下你的精彩故事，分享生活的每个瞬间..."
                  autoSize={{ minRows: 10, maxRows: 12 }}
                  maxLength={5000}
                  showCount
                />
              </Form.Item>

              <div className="form-row">
                <Form.Item label="帖子封面" name="coverIndex" initialValue={0} layout="horizontal"
                  style={{ marginBottom: 0 }}>
                  <Select
                    placeholder="选择帖子封面"
                    disabled={uploadedImages.length === 0}
                  >
                    {uploadedImages.map((img, index) => (
                      <Select.Option key={index} value={index}>
                        图片{CHINESE_NUMS[index] || index + 1}
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>

                <Form.Item name="isPrivate" valuePropName="checked" initialValue={true}>
                  <div className="privacy-toggle">
                    <Switch checkedChildren="私密" unCheckedChildren="公开" defaultChecked />
                  </div>
                </Form.Item>
              </div>

              <div className="modal-submit-buttons">
                <Button size="large" onClick={handleCancel}>
                  取消
                </Button>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  icon={<SendOutlined />}
                  loading={submitLoading}
                  className="modal-submit-button"
                >
                  {isEditing ? '保存' : '发布'}
                </Button>
              </div>
          </div>
        </div>
      )}
      </Form>
    </Modal>
  )
}

export default CreateEditPostModal
