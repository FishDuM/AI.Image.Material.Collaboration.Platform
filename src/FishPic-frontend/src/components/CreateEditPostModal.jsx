import { useState, useEffect, useCallback } from 'react'
import { App, Modal, Form, Input, Button, Upload, Select, Switch, Image as AntImage, Tabs, Pagination, Spin, Empty } from 'antd'
import { PlusOutlined, DeleteOutlined, LeftOutlined, RightOutlined, SendOutlined, CheckOutlined, CloudOutlined } from '@ant-design/icons'
import api, { listSpace, spaceListPicture } from '../api'
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

const SpacePickerModal = ({ open, onClose, onConfirm, currentImageCount, existingImageIds = [] }) => {
  const { message: msg } = App.useApp()
  const [activeTab, setActiveTab] = useState('private')
  const [spaceId, setSpaceId] = useState(null)
  const [images, setImages] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])

  useEffect(() => {
    if (open) {
      setActiveTab('private')
      setSelectedIds([])
      setPage(1)
    }
  }, [open])

  useEffect(() => {
    const loadSpace = async () => {
      try {
        const result = await listSpace(0)
        const list = Array.isArray(result) ? result : []
        if (list.length > 0 && list[0].id) {
          setSpaceId(list[0].id)
        } else {
          setSpaceId(null)
        }
      } catch {
        setSpaceId(null)
      }
    }
    if (open && activeTab === 'private') loadSpace()
  }, [open, activeTab])

  const fetchImages = useCallback(async (p) => {
    if (!spaceId) return
    setLoading(true)
    try {
      const result = await spaceListPicture({ spaceId, current: p, pageSize: 20 })
      const list = Array.isArray(result?.records) ? result.records : []
      const t = typeof result?.total === 'number' ? result.total : list.length
      setImages(list)
      setTotal(t)
    } catch {
      setImages([])
    } finally {
      setLoading(false)
    }
  }, [spaceId])

  useEffect(() => {
    if (spaceId && activeTab === 'private') fetchImages(page)
  }, [spaceId, page, fetchImages, activeTab])

  const toggleImage = useCallback((img) => {
    if (selectedIds.includes(img.id)) {
      setSelectedIds(prev => prev.filter(id => id !== img.id))
      return
    }
    if (existingImageIds.includes(img.id)) {
      msg.warning('该图片已添加，请勿重复选择')
      return
    }
    if (currentImageCount + selectedIds.length >= 15) {
      msg.warning(`最多只能选择15张图片（已选择${currentImageCount}张）`)
      return
    }
    setSelectedIds(prev => [...prev, img.id])
  }, [currentImageCount, selectedIds, existingImageIds, msg])

  const handleConfirm = useCallback(() => {
    if (selectedIds.length === 0) {
      msg.warning('请先选择图片')
      return
    }
    const imageMap = new Map(images.map(img => [img.id, img]))
    const selected = selectedIds.map(id => imageMap.get(id)).filter(Boolean)
    onConfirm(selected)
    setSelectedIds([])
    setPage(1)
    onClose()
  }, [selectedIds, images, onConfirm, msg, onClose])

  const handleTabChange = (key) => {
    setActiveTab(key)
    setSelectedIds([])
    setPage(1)
  }

  return (
    <Modal
      open={open}
      onCancel={() => { setSelectedIds([]); setPage(1); onClose() }}
      title="从空间中获取"
      width={640}
      className="space-picker-modal"
      footer={[
        <Button key="cancel" onClick={() => { setSelectedIds([]); setPage(1); onClose() }}>
          取消
        </Button>,
        <Button
          key="confirm"
          type="primary"
          icon={<CheckOutlined />}
          disabled={selectedIds.length === 0}
          onClick={handleConfirm}
        >
          确认选择 ({selectedIds.length})
        </Button>,
      ]}
    >
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        className="space-picker-tabs"
        items={[
          {
            key: 'private',
            label: '私人空间',
            children: (
              <div className="space-picker-tab-content">
                {!spaceId ? (
                  <Empty description="暂无私人空间" style={{ padding: '60px 0' }} />
                ) : (
                  <>
                    <Spin spinning={loading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                      <div className="space-image-grid space-picker-grid">
                        {images.map((img) => {
                          const orderIndex = selectedIds.indexOf(img.id)
                          const isSelected = orderIndex !== -1
                          const isInCarousel = existingImageIds.includes(img.id)
                          return (
                            <div
                              key={img.id}
                              className={`space-image-item ${isSelected ? 'space-image-selected' : ''} ${isInCarousel ? 'space-image-in-carousel' : ''}`}
                              onClick={() => toggleImage(img)}
                            >
                              <img src={img.url} alt="" className="space-image-thumb" />
                              <div className="space-image-check">
                                {isSelected ? (
                                  <span className="space-order-badge">{orderIndex + 1}</span>
                                ) : isInCarousel ? (
                                  <span className="space-order-exists">已有</span>
                                ) : (
                                  <span className="space-order-empty" />
                                )}
                              </div>
                            </div>
                          )
                        })}
                      </div>
                      {images.length === 0 && !loading && (
                        <Empty description="暂无图片" style={{ padding: '40px 0' }} />
                      )}
                    </Spin>
                    <div className="space-image-footer space-picker-footer">
                      <Pagination
                        current={page}
                        total={total}
                        pageSize={20}
                        size="small"
                        showSizeChanger={false}
                        showTotal={(t) => `共 ${t} 张`}
                        onChange={(p) => setPage(p)}
                      />
                      <span className="space-picker-limit-hint">
                        {currentImageCount > 0 ? `已选 ${currentImageCount} 张，还可选 ${Math.max(0, 15 - currentImageCount)} 张` : `最多可选 15 张`}
                      </span>
                    </div>
                  </>
                )}
              </div>
            ),
          },
          {
            key: 'team',
            label: '团队空间',
            children: (
              <div className="space-picker-tab-content">
                <Empty description="团队空间功能开发中..." style={{ padding: '60px 0' }} />
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}

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
  const [spaceImages, setSpaceImages] = useState([])
  const [spaceImageTotal, setSpaceImageTotal] = useState(0)
  const [spaceImagePage, setSpaceImagePage] = useState(1)
  const [spaceImageLoading, setSpaceImageLoading] = useState(false)
  const [selectedSpaceImageIds, setSelectedSpaceImageIds] = useState([])
  const [spaceId, setSpaceId] = useState(null)
  const [uploadTabKey, setUploadTabKey] = useState('manual')
  const [spacePickerOpen, setSpacePickerOpen] = useState(false)

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
      setSelectedSpaceImageIds([])
      setSpaceImagePage(1)
      setUploadTabKey('manual')
      form.resetFields()
    }
  }, [open, editPostDetail, form])

  const fetchSpaceImages = useCallback(async (page) => {
    if (!spaceId) return
    setSpaceImageLoading(true)
    try {
      const result = await spaceListPicture({ spaceId, current: page, pageSize: 20 })
      const list = Array.isArray(result?.records) ? result.records : []
      const total = typeof result?.total === 'number' ? result.total : list.length
      setSpaceImages(list)
      setSpaceImageTotal(total)
    } catch {
      setSpaceImages([])
    } finally {
      setSpaceImageLoading(false)
    }
  }, [spaceId])

  useEffect(() => {
    const loadSpace = async () => {
      try {
        const result = await listSpace(0)
        const list = Array.isArray(result) ? result : []
        if (list.length > 0 && list[0].id) {
          setSpaceId(list[0].id)
        }
      } catch {
        setSpaceId(null)
      }
    }
    if (open && !editPostDetail) loadSpace()
  }, [open, editPostDetail])

  useEffect(() => {
    if (spaceId) fetchSpaceImages(spaceImagePage)
  }, [spaceId, spaceImagePage, fetchSpaceImages])

  const toggleSpaceImage = useCallback((img) => {
    if (selectedSpaceImageIds.includes(img.id)) {
      setSelectedSpaceImageIds(prev => prev.filter(id => id !== img.id))
      return
    }
    if (imageId.includes(img.id)) {
      message.warning('该图片已添加，请勿重复选择')
      return
    }
    if (imageId.length >= 15) {
      message.warning('最多只能选择15张图片')
      return
    }
    setSelectedSpaceImageIds(prev => [...prev, img.id])
  }, [imageId, selectedSpaceImageIds])

  const handleConfirmSpaceSelect = useCallback(() => {
    if (selectedSpaceImageIds.length === 0) {
      message.warning('请先选择图片')
      return
    }
    const spaceImageMap = new Map(spaceImages.map(img => [img.id, img]))
    const selected = selectedSpaceImageIds.map(id => spaceImageMap.get(id)).filter(Boolean)
    const newImages = selected.map((img) => ({
      uid: `space-${img.id}`,
      name: `space-image-${img.id}`,
      status: 'done',
      url: img.url,
      pictureId: img.id,
    }))
    setUploadedImages(prev => [...prev, ...newImages])
    setImageId(prev => [...prev, ...selected.map(img => img.id)])
    setModalStep(2)
    setSelectedSpaceImageIds([])
    setSpaceImagePage(1)
  }, [selectedSpaceImageIds, spaceImages])

  const handleSpacePickerConfirm = useCallback((selected) => {
    const newImages = selected.map((img) => ({
      uid: `space-pick-${img.id}`,
      name: `space-image-${img.id}`,
      status: 'done',
      url: img.url,
      pictureId: img.id,
    }))
    setUploadedImages(prev => [...prev, ...newImages])
    setImageId(prev => [...prev, ...selected.map(img => img.id)])
    setModalStep(2)
  }, [])

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
    if (currentImageIndex === uploadedImages.length - 1) {
      if (uploadedImages.length < 15 && !showUploadSlide) {
        setShowUploadSlide(true)
      }
    } else {
      setCurrentImageIndex(prev => Math.min(uploadedImages.length - 1, prev + 1))
      setShowUploadSlide(false)
    }
  }

  const handlePrevImage = () => {
    if (showUploadSlide) {
      setShowUploadSlide(false)
      setCurrentImageIndex(uploadedImages.length - 1)
    } else if (currentImageIndex > 0) {
      setCurrentImageIndex(prev => prev - 1)
    }
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
    setSelectedSpaceImageIds([])
    setSpaceImagePage(1)
    onClose()
  }

  return (
    <Modal
      open={open}
      onCancel={handleCancel}
      footer={null}
      closable={false}
      className={`create-post-modal ${modalStep === 1 && uploadTabKey === 'manual' ? 'create-post-modal-compact' : ''}`}
      width={modalStep === 1 && uploadTabKey === 'manual' ? 600 : 900}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} autoComplete="off" className="post-form">
      {modalStep === 1 ? (
        <div className="upload-step">
          <div className="upload-step-hint">至少上传一张图片</div>
          <Tabs
            className="upload-tabs"
            activeKey={uploadTabKey}
            onChange={setUploadTabKey}
            items={[
              {
                key: 'manual',
                label: '手动上传',
                children: (
                  <div className="upload-tab-content">
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
                ),
              },
              {
                key: 'space',
                label: '从私人空间选择',
                children: (
                  <div className="upload-tab-content space-select-tab">
                    {!spaceId ? (
                      <Empty description="暂无私人空间" style={{ padding: '60px 0' }} />
                    ) : (
                      <>
                        <Spin spinning={spaceImageLoading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                          <div className="space-image-grid">
                            {spaceImages.map((img) => {
                              const orderIndex = selectedSpaceImageIds.indexOf(img.id)
                              const isSelected = orderIndex !== -1
                              const isInCarousel = imageId.includes(img.id)
                              return (
                                <div
                                  key={img.id}
                                  className={`space-image-item ${isSelected ? 'space-image-selected' : ''} ${isInCarousel ? 'space-image-in-carousel' : ''}`}
                                  onClick={() => toggleSpaceImage(img)}
                                >
                                  <img src={img.url} alt="" className="space-image-thumb" />
                                  <div className="space-image-check">
                                    {isSelected ? (
                                      <span className="space-order-badge">{orderIndex + 1}</span>
                                    ) : isInCarousel ? (
                                      <span className="space-order-exists">已有</span>
                                    ) : (
                                      <span className="space-order-empty" />
                                    )}
                                  </div>
                                </div>
                              )
                            })}
                          </div>
                          {spaceImages.length === 0 && !spaceImageLoading && (
                            <Empty description="暂无图片" style={{ padding: '40px 0' }} />
                          )}
                        </Spin>
                        <div className="space-image-footer">
                          <Pagination
                            current={spaceImagePage}
                            total={spaceImageTotal}
                            pageSize={20}
                            size="small"
                            showSizeChanger={false}
                            showTotal={(total) => `共 ${total} 张`}
                            onChange={(page) => setSpaceImagePage(page)}
                          />
                          <Button
                            type="primary"
                            icon={<CheckOutlined />}
                            disabled={selectedSpaceImageIds.length === 0}
                            onClick={handleConfirmSpaceSelect}
                          >
                            确认选择 ({selectedSpaceImageIds.length})
                          </Button>
                        </div>
                      </>
                    )}
                  </div>
                ),
              },
            ]}
          />
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
                      style={{ display: showUploadSlide ? 'none' : 'flex' }}
                    >
                      <DeleteOutlined />
                    </button>
                    <button
                      type="button"
                      className="carousel-arrow carousel-arrow-left"
                      onClick={handlePrevImage}
                      disabled={uploadedImages.length <= 1 || (currentImageIndex === 0 && !showUploadSlide)}
                    >
                      <LeftOutlined />
                    </button>
                    <button
                      type="button"
                      className="carousel-arrow carousel-arrow-right"
                      onClick={handleNextImage}
                      disabled={currentImageIndex >= uploadedImages.length - 1 && (uploadedImages.length >= 15 || showUploadSlide)}
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

                <Form.Item name="isPrivate" valuePropName="checked" initialValue={false}>
                  <div className="privacy-toggle">
                    <Switch checkedChildren="私密" unCheckedChildren="公开" />
                  </div>
                </Form.Item>
              </div>

              <div className="modal-submit-buttons">
                <div className="modal-submit-left">
                  <Button
                    size="large"
                    icon={<CloudOutlined />}
                    onClick={() => setSpacePickerOpen(true)}
                    disabled={imageId.length >= 15}
                    className="space-fetch-button"
                  >
                    从空间中获取
                  </Button>
                </div>
                <div className="modal-submit-right">
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
        </div>
      )}
      </Form>
      <SpacePickerModal
        open={spacePickerOpen}
        onClose={() => setSpacePickerOpen(false)}
        onConfirm={handleSpacePickerConfirm}
        currentImageCount={imageId.length}
        existingImageIds={imageId}
      />
    </Modal>
  )
}

export default CreateEditPostModal
