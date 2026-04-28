import { useState, useEffect, useRef, useCallback } from 'react'
import { App, Typography, Button, Modal, Form, Input, Upload, Switch, Select, Image as AntImage, Masonry, Empty, Spin } from 'antd'
import { PlusOutlined, SendOutlined, InboxOutlined, LikeOutlined, SearchOutlined, HeartOutlined, StarOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import api from '../api'
import './CommunitySquare.css'

const { Title } = Typography
const { TextArea } = Input
const { Dragger } = Upload

function PostCard({ post, onClick }) {
  return (
    <div className="post-card" onClick={() => onClick(post)}>
      {post.url ? (
        <AntImage 
          src={post.url} 
          alt={post.title} 
          className="post-card-image" 
          preview={false}
          style={{ objectFit: 'cover', borderRadius: '12px 12px 0 0', overflow: 'hidden' }}
        />
      ) : (
        <div className="post-card-image-placeholder" />
      )}
      <div className="post-card-content">
        <div className="post-card-title">{post.title}</div>
        <div className="post-card-footer">
          <div className="post-card-author">
            {post.avatar ? (
              <img src={post.avatar} alt={post.username} className="post-card-avatar" />
            ) : (
              <div className="post-card-avatar post-card-avatar-default">{post.username?.charAt(0)?.toUpperCase()}</div>
            )}
            <span className="post-card-username">{post.username}</span>
          </div>
          <div className="post-card-likes">
            <LikeOutlined />
            <span>{post.likesNum || 0}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

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
  const [showUploadSlide, setShowUploadSlide] = useState(false)
  const [postList, setPostList] = useState([])
  const [masonryItems, setMasonryItems] = useState([])
  const [postDetailModalOpen, setPostDetailModalOpen] = useState(false)
  const [postDetail, setPostDetail] = useState(null)
  const [postDetailLoading, setPostDetailLoading] = useState(false)
  const [detailImageIndex, setDetailImageIndex] = useState(0)

  const formatRelativeTime = (timeString) => {
    if (!timeString) return ''
    const now = new Date()
    const updateTime = new Date(timeString)
    const diffMs = now - updateTime
    const diffSeconds = Math.floor(diffMs / 1000)
    const diffMinutes = Math.floor(diffSeconds / 60)
    const diffHours = Math.floor(diffMinutes / 60)
    const diffDays = Math.floor(diffHours / 24)

    if (diffDays >= 7) {
      return updateTime.toLocaleString('zh-CN')
    } else if (diffDays >= 1) {
      return `${diffDays}天前`
    } else if (diffHours >= 1) {
      return `${diffHours}小时前`
    } else if (diffMinutes >= 1) {
      return `${diffMinutes}分钟前`
    } else {
      return '刚刚'
    }
  }

  const fetchPostList = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.post('/post/postList', {
        current: 1,
        size: 20,
      })
      if (result && result.records) {
        setPostList(result.records)
        // 不设置固定高度，让Masonry根据实际内容自动计算
        const items = result.records.map((post, index) => ({
          key: post.id || index,
          data: post,
        }))
        setMasonryItems(items)
      }
    } catch {
      message.error('获取帖子列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  const fetchPostDetail = useCallback(async (postId) => {
    setPostDetailLoading(true)
    setDetailImageIndex(0)
    try {
      const result = await api.get('/post/getPost', { params: { id: postId } })
      if (result) {
        setPostDetail(result)
        setPostDetailModalOpen(true)
      }
    } catch {
      message.error('获取帖子详情失败')
    } finally {
      setPostDetailLoading(false)
    }
  }, [message])

  const handlePostClick = useCallback((post) => {
    fetchPostDetail(post.id)
  }, [fetchPostDetail])

  const handlePostDetailClose = () => {
    setPostDetailModalOpen(false)
    setPostDetail(null)
    setDetailImageIndex(0)
  }

  const handlePostDetailPrevImage = () => {
    if (postDetail && postDetail.pictureUrl && postDetail.pictureUrl.length > 0) {
      const validPics = (postDetail.pictureUrl || []).filter(url => url && url.trim())
      setDetailImageIndex(prev => Math.max(0, prev - 1))
    }
  }

  const handlePostDetailNextImage = () => {
    if (postDetail && postDetail.pictureUrl && postDetail.pictureUrl.length > 0) {
      const validPics = (postDetail.pictureUrl || []).filter(url => url && url.trim())
      setDetailImageIndex(prev => Math.min(validPics.length - 1, prev + 1))
    }
  }

  useEffect(() => {
    fetchPostList()
  }, [fetchPostList])

  useEffect(() => {
    if (isModalOpen && pictureIds.length > 0 && !form.getFieldValue('cover')) {
      form.setFieldValue('cover', pictureIds[0])
    }
  }, [form, pictureIds, isModalOpen])

  const handleCreatePost = () => {
    setModalStep(1)
    setUploadedImages([])
    setPictureIds([])
    setCurrentImageIndex(0)
    setShowUploadSlide(false)
    setIsModalOpen(true)
  }

  const handleCancel = () => {
    setIsModalOpen(false)
    setUploadedImages([])
    setPictureIds([])
    setCurrentImageIndex(0)
    setShowUploadSlide(false)
    setModalStep(1)
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
      if (showUploadSlide) {
        setCurrentImageIndex(prev => prev + 1)
        setShowUploadSlide(false)
      }
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
      const submitData = {
        imageId: pictureIds,
        title: values.title,
        content: values.content,
        cover: values.cover || pictureIds[0] || null,
        isPrivate: values.isPrivate ? 1 : 0
      }
      await api.post('/post/post', submitData)
      message.success('发布成功！')
      form.resetFields()
      setIsModalOpen(false)
      setUploadedImages([])
      setPictureIds([])
      setModalStep(1)
      fetchPostList()
    } catch {
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
        <div className="search-area">
          <Input
            placeholder="搜索帖子..."
            prefix={<SearchOutlined />}
            className="search-input"
          />
          <Button
            type="primary"
            icon={<SearchOutlined />}
            className="search-button"
          >
            搜索
          </Button>
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

      {loading ? (
        <div className="loading-wrapper">
          <Spin size="large" />
        </div>
      ) : postList.length === 0 ? (
        <div className="empty-state-wrapper">
          <Empty description="暂无帖子，快来发布第一条吧" />
        </div>
      ) : (
        <div className="masonry-wrapper">
          <Masonry
            columns={{ xs: 2, sm: 3, md: 4, lg: 5 }}
            gutter={[12, 12]}
            items={masonryItems}
            itemRender={(item) => <PostCard post={item.data} onClick={handlePostClick} />}
          />
        </div>
      )}

      <Modal
        open={postDetailModalOpen}
        onCancel={handlePostDetailClose}
        footer={null}
        className="post-detail-modal"
        width={1000}
        centered
      >
        {postDetailLoading ? (
          <div className="loading-wrapper">
            <Spin size="large" />
          </div>
        ) : postDetail ? (
          <div className="xiaohongshu-layout post-detail-layout">
            <div className="left-image-area post-detail-image-area">
              {(() => {
                const validPics = (postDetail.pictureUrl || []).filter(url => url && url.trim())
                return validPics.length > 0 ? (
                  <div className="carousel-main post-detail-carousel">
                    <AntImage
                      src={validPics[detailImageIndex]}
                      alt={postDetail.title}
                      className="carousel-main-image post-detail-main-image"
                      preview={false}
                    />
                    {validPics.length > 1 && (
                      <>
                        <button
                          type="button"
                          className="carousel-arrow carousel-arrow-left"
                          onClick={handlePostDetailPrevImage}
                          disabled={detailImageIndex === 0}
                        >
                          <LeftOutlined />
                        </button>
                        <button
                          type="button"
                          className="carousel-arrow carousel-arrow-right"
                          onClick={handlePostDetailNextImage}
                          disabled={detailImageIndex === validPics.length - 1}
                        >
                          <RightOutlined />
                        </button>
                        <div className="carousel-counter">
                          {detailImageIndex + 1} / {validPics.length}
                        </div>
                      </>
                    )}
                  </div>
                ) : (
                  <div className="no-image-placeholder">
                    <Empty description="暂无图片" />
                  </div>
                )
              })()}
            </div>
            <div className="right-form-area post-detail-content-area">
              <div className="post-detail-header">
                <div className="post-detail-user-info">
                  {postDetail.avatar ? (
                    <img src={postDetail.avatar} alt={postDetail.username} className="post-detail-avatar" />
                  ) : (
                    <div className="post-detail-avatar post-detail-avatar-default">
                      {postDetail.username?.charAt(0)?.toUpperCase()}
                    </div>
                  )}
                  <span className="post-detail-username">{postDetail.username}</span>
                </div>
              </div>
              <div className="post-detail-title">{postDetail.title}</div>
              <div className="post-detail-content">{postDetail.content}</div>
              <div className="post-detail-stats">
                <div className="post-detail-stat-item">
                  <LikeOutlined />
                  <span>{postDetail.likesNum || 0}</span>
                </div>
                <div className="post-detail-stat-item">
                  <HeartOutlined />
                  <span>{postDetail.collectsNum || 0}</span>
                </div>
                <div className="post-detail-stat-item">
                  <StarOutlined />
                  <span>{postDetail.commentNum || 0}</span>
                </div>
              </div>
              <div className="post-detail-time">
                {postDetail.updateTime ? formatRelativeTime(postDetail.updateTime) : ''}
              </div>
            </div>
          </div>
        ) : null}
      </Modal>

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
                  {showUploadSlide && uploadedImages.length < 15 ? (
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
                  ) : currentImageIndex < uploadedImages.length ? (
                    <AntImage
                      src={uploadedImages[currentImageIndex]?.url}
                      alt={uploadedImages[currentImageIndex]?.name}
                      className="carousel-main-image"
                      preview={true}
                      style={{ cursor: 'pointer', pointerEvents: 'none' }}
                    />
                  ) : null}
                  {(currentImageIndex < uploadedImages.length || showUploadSlide) && (
                    <>
                      <button
                        type="button"
                        className="carousel-remove-btn"
                        onClick={() => !showUploadSlide && handleImageRemove(uploadedImages[currentImageIndex])}
                        style={{ display: showUploadSlide ? 'none' : 'flex' }}
                      >
                        <span role="img" aria-label="delete" className="anticon anticon-delete">
                          <svg viewBox="64 64 896 896" focusable="false" data-icon="delete" width="1em"
                            height="1em"
                            fill="currentColor" aria-hidden="true">
                            <path
                              d="M360 184h-8c4.4 0 8-3.6 8-8v8h304v-8c0 4.4 3.6 8 8 8h-8v72h72v-80c0-35.3-28.7-64-64-64H352c-35.3 0-64 28.7-64 64v80h72v-72zm504 72H160c-17.7 0-32 14.3-32 32v32c0 4.4 3.6 8 8 8h60.4l24.7 523c1.6 34.1 29.8 61 63.9 61h454c34.2 0 62.3-26.8 63.9-61l24.7-523H888c4.4 0 8-3.6 8-8v-32c0-17.7-14.3-32-32-32zM731.3 840H292.7l-24.2-512h487l-24.2 512z"></path>
                          </svg>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="carousel-arrow carousel-arrow-left"
                        onClick={handlePrevImage}
                        disabled={currentImageIndex === 0 && !showUploadSlide}
                      >
                        <span role="img" aria-label="left" className="anticon anticon-left">
                          <svg viewBox="64 64 896 896" focusable="false" data-icon="left" width="1em"
                            height="1em"
                            fill="currentColor" aria-hidden="true">
                            <path
                              d="M724 218.3V141c0-6.7-7.7-10.4-12.9-6.3L260.3 486.8a31.86 31.86 0 000 50.3l450.8 352.1c5.3 4.1 12.9.4 12.9-6.3v-77.3c0-4.9-2.3-9.6-6.1-12.6l-360-281 360-281.1c3.8-3 6.1-7.7 6.1-12.6z"></path>
                          </svg>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="carousel-arrow carousel-arrow-right"
                        onClick={handleNextImage}
                        disabled={uploadedImages.length >= 15 && !showUploadSlide}
                        style={{ display: showUploadSlide ? 'none' : 'flex' }}
                      >
                        <span role="img" aria-label="right" className="anticon anticon-right">
                          <svg viewBox="64 64 896 896" focusable="false" data-icon="right" width="1em"
                            height="1em"
                            fill="currentColor" aria-hidden="true">
                            <path
                              d="M765.7 486.8L314.9 134.7A7.97 7.97 0 00302 141v77.3c0 4.9 2.3 9.6 6.1 12.6l360 281.1-360 281.1c-3.9 3-6.1 7.7-6.1 12.6V883c0 6.7 7.7 10.4 12.9 6.3l450.8-352.1a31.96 31.96 0 000-50.4z"></path>
                          </svg>
                        </span>
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
                  <TextArea
                    placeholder="写下你的精彩故事，分享生活的每个瞬间..."
                    autoSize={{ minRows: 10, maxRows: 12 }}
                    maxLength={5000}
                    showCount
                  />
                </Form.Item>

                <div className="form-row">
                  <Form.Item label="帖子封面" name="cover" initialValue={pictureIds[0] || null} layout="horizontal"
                    style={{ marginBottom: 0 }}>
                    <Select
                      placeholder="选择帖子封面"
                      disabled={pictureIds.length === 0}
                      onChange={(value) => {
                        form.setFieldValue('cover', value)
                      }}
                    >
                      {uploadedImages.map((img, index) => (
                        <Select.Option key={img.pictureId} value={img.pictureId}>
                          图片 {index + 1}
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
