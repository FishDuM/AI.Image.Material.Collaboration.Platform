import { useState, useEffect, useRef } from 'react'
import { App, Card, Typography, Empty, Button, Modal, Form, Input, Upload } from 'antd'
import { PlusOutlined, SendOutlined, InboxOutlined } from '@ant-design/icons'
import api from '../api'
import './CommunitySquare.css'

const { Title } = Typography
const { TextArea } = Input
const { Dragger } = Upload

function CommunitySquare() {
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [submitLoading, setSubmitLoading] = useState(false)
  const [form] = Form.useForm()
  const [modalStep, setModalStep] = useState(1)
  const [uploadedImages, setUploadedImages] = useState([])
  const [pictureIds, setPictureIds] = useState([])
  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const carouselRef = useRef(null)

  useEffect(() => {
    // 页面加载逻辑
  }, [])

  const handleCreatePost = () => {
    setModalStep(1)
    setUploadedImages([])
    setPictureIds([])
    setCurrentImageIndex(0)
    setIsModalOpen(true)
  }

  const handleCancel = () => {
    setIsModalOpen(false)
    setUploadedImages([])
    setPictureIds([])
    setCurrentImageIndex(0)
    setModalStep(1)
    if (modalStep === 2) {
      form.resetFields()
    }
  }

  const handleImageUpload = async ({ file, onSuccess, onError }) => {
    if (pictureIds.length >= 15) {
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
      onSuccess({ url, pictureId })
      message.success('上传成功')

      setUploadedImages(prev => [...prev, { uid: file.uid, name: file.name, status: 'done', url, pictureId }])
      setPictureIds(prev => [...prev, pictureId])
      setModalStep(2)
    } catch (error) {
      onError(error)
      message.error('上传失败')
    }
  }

  const customUploadRequest = (options) => {
    handleImageUpload(options)
  }

  const handleImageRemove = (file) => {
    setUploadedImages(prev => prev.filter(img => img.uid !== file.uid))
    const removedPicture = uploadedImages.find(img => img.uid === file.uid)
    if (removedPicture) {
      setPictureIds(prev => prev.filter(id => id !== removedPicture.pictureId))
    }
    if (currentImageIndex >= uploadedImages.length - 1) {
      setCurrentImageIndex(Math.max(0, uploadedImages.length - 2))
    }
  }

  const touchStartX = useRef(0)
  const touchEndX = useRef(0)

  const handleTouchStart = (e) => {
    touchStartX.current = e.touches[0].clientX
  }

  const handleTouchMove = (e) => {
    touchEndX.current = e.touches[0].clientX
  }

  const handleTouchEnd = () => {
    const diff = touchStartX.current - touchEndX.current
    const threshold = 50
    if (Math.abs(diff) > threshold) {
      if (diff > 0 && currentImageIndex < uploadedImages.length - 1) {
        setCurrentImageIndex(prev => prev + 1)
      } else if (diff < 0 && currentImageIndex > 0) {
        setCurrentImageIndex(prev => prev - 1)
      }
    }
  }

  const handleSubmit = async (values) => {
    setSubmitLoading(true)
    try {
      console.log('提交发帖数据:', { ...values, pictureIds })
      message.success('发布成功！')
      setIsModalOpen(false)
      form.resetFields()
      setUploadedImages([])
      setPictureIds([])
      setModalStep(1)
    } catch (error) {
      message.error('发布失败，请重试')
    } finally {
      setSubmitLoading(false)
    }
  }

  const uploadProps = {
    name: 'file',
    multiple: true,
    listType: 'picture-card',
    customRequest: customUploadRequest,
    onRemove: handleImageRemove,
    fileList: uploadedImages,
    maxCount: 15,
  }

  return (
    <main className="community-square-container">
      <div className="community-square-header">
        <div className="header-left">
          <Title level={2}>社区广场</Title>
          <p className="header-subtitle">探索社区精彩内容和分享</p>
        </div>
        <Button 
          type="primary" 
          icon={<PlusOutlined />} 
          size="large"
          className="post-button"
          onClick={handleCreatePost}
        >
          发帖
        </Button>
      </div>

      <Card className="community-content-card" variant="borderless">
        <div className="empty-state-wrapper">
          <Empty description="功能开发中，敬请期待" />
        </div>
      </Card>

      <Modal
        open={isModalOpen}
        onCancel={handleCancel}
        footer={null}
        className="create-post-modal"
        width={900}
      >
        {modalStep === 1 ? (
          <div className="upload-step">
            <div className="upload-hint-text">发布帖子需要先上传图片</div>
            <Dragger {...uploadProps} className="image-dragger">
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽图片到此区域上传</p>
              <p className="ant-upload-hint">
                支持 JPG、PNG、GIF 格式，单张图片不超过 5MB，最多15张
              </p>
            </Dragger>
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
                  {currentImageIndex < uploadedImages.length ? (
                    <>
                      <img
                        src={uploadedImages[currentImageIndex]?.url}
                        alt={uploadedImages[currentImageIndex]?.name}
                        className="carousel-main-image"
                      />
                      <button
                        type="button"
                        className="carousel-remove-btn"
                        onClick={() => handleImageRemove(uploadedImages[currentImageIndex])}
                      >
                        <span role="img" aria-label="delete" className="anticon anticon-delete">
                          <svg viewBox="64 64 896 896" focusable="false" data-icon="delete" width="1em" height="1em" fill="currentColor" aria-hidden="true">
                            <path d="M360 184h-8c4.4 0 8-3.6 8-8v8h304v-8c0 4.4 3.6 8 8 8h-8v72h72v-80c0-35.3-28.7-64-64-64H352c-35.3 0-64 28.7-64 64v80h72v-72zm504 72H160c-17.7 0-32 14.3-32 32v32c0 4.4 3.6 8 8 8h60.4l24.7 523c1.6 34.1 29.8 61 63.9 61h454c34.2 0 62.3-26.8 63.9-61l24.7-523H888c4.4 0 8-3.6 8-8v-32c0-17.7-14.3-32-32-32zM731.3 840H292.7l-24.2-512h487l-24.2 512z"></path>
                          </svg>
                        </span>
                      </button>
                    </>
                  ) : (
                    <Upload
                      listType="picture-card"
                      className="carousel-upload"
                      customRequest={customUploadRequest}
                      onRemove={handleImageRemove}
                      fileList={uploadedImages}
                      maxCount={15}
                      showUploadList={false}
                    >
                      <button type="button" className="carousel-upload-btn">
                        <PlusOutlined />
                        <div className="upload-text">继续上传</div>
                      </button>
                    </Upload>
                  )}
                  {currentImageIndex > 0 && (
                    <button
                      type="button"
                      className="carousel-arrow carousel-arrow-left"
                      onClick={() => setCurrentImageIndex(prev => prev - 1)}
                    >
                      <span role="img" aria-label="left" className="anticon anticon-left">
                        <svg viewBox="64 64 896 896" focusable="false" data-icon="left" width="1em" height="1em" fill="currentColor" aria-hidden="true">
                          <path d="M724 218.3V141c0-6.7-7.7-10.4-12.9-6.3L260.3 486.8a31.86 31.86 0 000 50.3l450.8 352.1c5.3 4.1 12.9.4 12.9-6.3v-77.3c0-4.9-2.3-9.6-6.1-12.6l-360-281 360-281.1c3.8-3 6.1-7.7 6.1-12.6z"></path>
                        </svg>
                      </span>
                    </button>
                  )}
                  {currentImageIndex < uploadedImages.length && (
                    <button
                      type="button"
                      className="carousel-arrow carousel-arrow-right"
                      onClick={() => setCurrentImageIndex(prev => prev + 1)}
                    >
                      <span role="img" aria-label="right" className="anticon anticon-right">
                        <svg viewBox="64 64 896 896" focusable="false" data-icon="right" width="1em" height="1em" fill="currentColor" aria-hidden="true">
                          <path d="M765.7 486.8L314.9 134.7A7.97 7.97 0 00302 141v77.3c0 4.9 2.3 9.6 6.1 12.6l360 281.1-360 281.1c-3.9 3-6.1 7.7-6.1 12.6V883c0 6.7 7.7 10.4 12.9 6.3l450.8-352.1a31.96 31.96 0 000-50.4z"></path>
                        </svg>
                      </span>
                    </button>
                  )}
                  <div className="carousel-counter">
                    {currentImageIndex + 1} / {uploadedImages.length + 1}
                  </div>
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
                >
                  <button type="button" className="upload-trigger-inline">
                    <PlusOutlined />
                    <div className="upload-text">上传图片</div>
                  </button>
                </Upload>
              )}
            </div>
            <div className="right-form-area">
              <Form
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                autoComplete="off"
                className="post-form"
              >
                <Form.Item
                  label="标题"
                  name="title"
                  rules={[
                    { required: true, message: '请输入标题' },
                    { max: 50, message: '标题最多50个字' }
                  ]}
                >
                  <Input placeholder="填写标题会有更多赞哦~" size="large" />
                </Form.Item>

                <Form.Item
                  label="正文"
                  name="content"
                  rules={[
                    { required: true, message: '请输入正文' },
                    { max: 5000, message: '正文最多5000个字' }
                  ]}
                >
                  <TextArea
                    placeholder="分享你的生活瞬间..."
                    autoSize={{ minRows: 10, maxRows: 12 }}
                    maxLength={5000}
                    showCount
                  />
                </Form.Item>

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
                    发布
                  </Button>
                </div>
              </Form>
            </div>
          </div>
        )}
      </Modal>
      </main>
    )
  }

  export default CommunitySquare
