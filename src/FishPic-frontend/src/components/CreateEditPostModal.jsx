import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { App, Modal, Form, Input, Button, Upload, Select, Switch, Tabs, Pagination, Spin, Empty, Popover, Image } from 'antd'
import { PlusOutlined, DeleteOutlined, LeftOutlined, RightOutlined, SendOutlined, CheckOutlined, CloudOutlined, TeamOutlined, ImportOutlined } from '@ant-design/icons'
import { editPost, listSpace, postPictureList, uploadPicture, uploadPost, savePictureByUrl } from '../api'
import MobilePageWrapper from './MobilePageWrapper'
import SpacePickerModal from './shared/SpacePickerModal'
import PostModal from './shared/PostModal'
import { isAllowedImageFile, getMaxUploadSize, formatMaxUploadSize } from '../utils/uploadConstraints'
import './CreateEditPostModal.css'

const CHINESE_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二', '十三', '十四', '十五']

const CreateEditPostModal = ({ open, onClose, editPostDetail, onSuccess, mode = 'modal' }) => {
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
  const [teamSpaces, setTeamSpaces] = useState([])
  const [teamSpacesLoading, setTeamSpacesLoading] = useState(false)
  const [teamSpaceId, setTeamSpaceId] = useState(null)
  const [teamSpaceImages, setTeamSpaceImages] = useState([])
  const [teamSpaceImageTotal, setTeamSpaceImageTotal] = useState(0)
  const [teamSpaceImagePage, setTeamSpaceImagePage] = useState(1)
  const [teamSpaceImageLoading, setTeamSpaceImageLoading] = useState(false)
  const [selectedTeamSpaceImageIds, setSelectedTeamSpaceImageIds] = useState([])
  const [teamSpaceView, setTeamSpaceView] = useState('list')
  const [urlImportUrl, setUrlImportUrl] = useState('')
  const [urlImporting, setUrlImporting] = useState(false)
  const [urlPreviewUrl, setUrlPreviewUrl] = useState('')
  const [urlPreviewError, setUrlPreviewError] = useState(false)
  const [urlResolved, setUrlResolved] = useState(false)
  const [popoverOpen, setPopoverOpen] = useState(false)

  const maxSize = getMaxUploadSize()
  const maxSizeText = formatMaxUploadSize()

  const uploadedImagesRef = useRef(uploadedImages)

  useEffect(() => {
    uploadedImagesRef.current = uploadedImages
  }, [uploadedImages])

  const existingImageIds = useMemo(
    () => uploadedImages.map(img => img.pictureId).filter(Boolean),
    [uploadedImages]
  )

  useEffect(() => {
    if (open && editPostDetail) {
      setIsEditing(true)
      setEditingPostId(editPostDetail.id)
      const existingUrls = (editPostDetail.pics || editPostDetail.pictureUrl || []).filter(url => url && url.trim())
      const existingIds = (editPostDetail.pictureIds || []).filter(Boolean)
      const existingImages = existingIds.map((id, index) => ({
        uid: `existing-${index}`,
        name: `image-${index}`,
        status: 'done',
        url: existingUrls[index] || undefined,
        pictureId: id,
      }))
      setUploadedImages(existingImages)
      setImageId(existingIds.filter(Boolean))
      setCurrentImageIndex(0)
      setShowUploadSlide(false)
      setModalStep(2)
      const coverIndex = (() => {
        if (editPostDetail.cover) {
          const idx = existingIds.indexOf(editPostDetail.cover)
          return idx >= 0 ? idx : 0
        }
        return 0
      })()
      const title = editPostDetail.title || ''
      const content = editPostDetail.content || ''
      const isPrivate = editPostDetail.isPrivate === 1 || editPostDetail.isPrivate === true
      setTimeout(() => {
        form.setFieldsValue({ title, content, isPrivate, coverIndex })
      }, 0)
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
      setSpaceImages([])
      setSpaceImageTotal(0)
      setSpaceId(null)
      setUploadTabKey('manual')
      setTeamSpaces([])
      setTeamSpacesLoading(false)
      setTeamSpaceId(null)
      setTeamSpaceImages([])
      setTeamSpaceImageTotal(0)
      setTeamSpaceImagePage(1)
      setSelectedTeamSpaceImageIds([])
      setTeamSpaceView('list')
      form.resetFields()
      setUrlImportUrl('')
      setUrlImporting(false)
      setUrlPreviewUrl('')
      setUrlPreviewError(false)
      setUrlResolved(false)
      setPopoverOpen(false)
      setTimeout(() => {
        form.setFieldsValue({ coverIndex: 0 })
      }, 0)
    }
  }, [open, editPostDetail, form])

  const fetchSpaceImages = useCallback(async (page, ids) => {
    if (!spaceId) return
    setSpaceImageLoading(true)
    try {
      const result = await postPictureList({ spaceId, pictureIds: ids, current: page, pageSize: 20 })
      const list = result?.records ?? []
      setSpaceImages(list)
      setSpaceImageTotal(result?.total ?? list.length)
    } catch {
      setSpaceImages([])
    } finally {
      setSpaceImageLoading(false)
    }
  }, [spaceId])

  const fetchTeamSpaces = useCallback(async () => {
    setTeamSpacesLoading(true)
    try {
      const result = await listSpace(1)
      const list = Array.isArray(result) ? result : []
      setTeamSpaces(list)
      if (list.length > 0 && !teamSpaceId) {
        setTeamSpaceId(list[0].id)
      }
    } catch {
      setTeamSpaces([])
    } finally {
      setTeamSpacesLoading(false)
    }
  }, [])

  const fetchTeamSpaceImages = useCallback(async (page, ids) => {
    if (!teamSpaceId) return
    setTeamSpaceImageLoading(true)
    try {
      const result = await postPictureList({ spaceId: teamSpaceId, pictureIds: ids, current: page, pageSize: 20 })
      const list = result?.records ?? []
      setTeamSpaceImages(list)
      setTeamSpaceImageTotal(result?.total ?? list.length)
    } catch {
      setTeamSpaceImages([])
    } finally {
      setTeamSpaceImageLoading(false)
    }
  }, [teamSpaceId])

  const handleTeamSpaceSelect = useCallback((id) => {
    setTeamSpaceId(id)
    setTeamSpaceImagePage(1)
    setSelectedTeamSpaceImageIds([])
    setTeamSpaceView('images')
  }, [])

  const handleTeamSpaceBack = useCallback(() => {
    setTeamSpaceView('list')
    setTeamSpaceId(null)
    setTeamSpaceImages([])
    setTeamSpaceImageTotal(0)
    setTeamSpaceImagePage(1)
    setSelectedTeamSpaceImageIds([])
  }, [])

  const handleTeamSpaceImageToggle = useCallback((img) => {
    if (selectedTeamSpaceImageIds.includes(img.id)) {
      setSelectedTeamSpaceImageIds(prev => prev.filter(id => id !== img.id))
      return
    }
    if (img.flag === false) {
      message.warning('该图片已添加，请勿重复选择')
      return
    }
    if (imageId.length + selectedTeamSpaceImageIds.length >= 15) {
      message.warning('最多只能选择15张图片')
      return
    }
    setSelectedTeamSpaceImageIds(prev => [...prev, img.id])
  }, [imageId, selectedTeamSpaceImageIds])

  const handleTeamSpaceConfirm = useCallback(() => {
    if (selectedTeamSpaceImageIds.length === 0) {
      message.warning('请先选择图片')
      return
    }
    const spaceImageMap = new Map(teamSpaceImages.map(img => [img.id, img]))
    const selected = selectedTeamSpaceImageIds.map(id => spaceImageMap.get(id)).filter(Boolean)
    const newImages = selected.map((img) => ({
      uid: `team-space-${img.id}`,
      name: `team-space-image-${img.id}`,
      status: 'done',
      url: img.url,
      pictureId: img.id,
    }))
    setCurrentImageIndex(uploadedImages.length + newImages.length - 1)
    setShowUploadSlide(false)
    setUploadedImages(prev => [...prev, ...newImages])
    setImageId(prev => [...prev, ...selected.map(img => img.id)])
    setModalStep(2)
    setSelectedTeamSpaceImageIds([])
    setTeamSpaceImagePage(1)
    const currentCover = form.getFieldValue('coverIndex')
    if (currentCover == null) {
      form.setFieldsValue({ coverIndex: 0 })
    }
  }, [selectedTeamSpaceImageIds, teamSpaceImages, uploadedImages.length, form])

  const handleUrlImportFromStep = async () => {
    if (!urlImportUrl.trim()) { message.warning('请输入图片URL'); return }
    if (imageId.length >= 15) { message.warning('最多只能上传15张图片'); return }
    setUrlImporting(true)
    try {
      const result = await savePictureByUrl(urlImportUrl.trim())
      const { url, id } = result
      const uid = `url-${Date.now()}`
      setUploadedImages(prev => [...prev, { uid, name: `url-image-${id}`, status: 'done', url, pictureId: id }])
      setImageId(prev => [...prev, id])
      setUrlImportUrl('')
      setUrlPreviewUrl('')
      setUrlResolved(false)
      setModalStep(2)
      const currentCover = form.getFieldValue('coverIndex')
      if (currentCover == null) form.setFieldsValue({ coverIndex: 0 })
      message.success('图片导入成功')
    } catch (e) {
      message.error(e.message || '导入失败')
    } finally {
      setUrlImporting(false)
    }
  }

  const handleStep1UrlResolve = () => {
    if (!urlImportUrl.trim()) { message.warning('请输入图片URL'); return }
    setUrlPreviewUrl(urlImportUrl.trim())
    setUrlPreviewError(false)
    setUrlResolved(true)
  }

  const handleStep1UrlCancel = () => {
    setUrlImportUrl('')
    setUrlPreviewUrl('')
    setUrlPreviewError(false)
    setUrlResolved(false)
  }

  const handleStep2UrlResolve = () => {
    if (!urlImportUrl.trim()) { message.warning('请输入图片URL'); return }
    setUrlPreviewUrl(urlImportUrl.trim())
    setUrlPreviewError(false)
    setUrlResolved(true)
  }

  const handleStep2UrlConfirm = async () => {
    if (imageId.length >= 15) { message.warning('最多只能上传15张图片'); return }
    if (!urlPreviewUrl) { message.warning('请先解析图片URL'); return }
    setUrlImporting(true)
    try {
      const result = await savePictureByUrl(urlPreviewUrl)
      const { url, id } = result
      const uid = `url-${Date.now()}`
      setUploadedImages(prev => [...prev, { uid, name: `url-image-${id}`, status: 'done', url, pictureId: id }])
      setImageId(prev => [...prev, id])
      setUrlImportUrl('')
      setUrlPreviewUrl('')
      setUrlResolved(false)
      setPopoverOpen(false)
      setCurrentImageIndex(prev => prev + 1)
      setShowUploadSlide(false)
      message.success('图片导入成功')
    } catch (e) {
      message.error(e.message || '导入失败')
    } finally {
      setUrlImporting(false)
    }
  }

  const handleStep2UrlCancel = () => {
    setUrlImportUrl('')
    setUrlPreviewUrl('')
    setUrlPreviewError(false)
    setUrlResolved(false)
    setPopoverOpen(false)
  }

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
    if (spaceId) fetchSpaceImages(spaceImagePage, imageId)
  }, [spaceId, spaceImagePage, fetchSpaceImages, imageId])

  useEffect(() => {
    if (teamSpaceId) fetchTeamSpaceImages(teamSpaceImagePage, imageId)
  }, [teamSpaceId, teamSpaceImagePage, fetchTeamSpaceImages, imageId])

  useEffect(() => {
    if (open && !editPostDetail && uploadTabKey === 'team') fetchTeamSpaces()
  }, [open, editPostDetail, uploadTabKey])

  const toggleSpaceImage = useCallback((img) => {
    if (selectedSpaceImageIds.includes(img.id)) {
      setSelectedSpaceImageIds(prev => prev.filter(id => id !== img.id))
      return
    }
    if (img.flag === false) {
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
    setCurrentImageIndex(uploadedImages.length + newImages.length - 1)
    setShowUploadSlide(false)
    setUploadedImages(prev => [...prev, ...newImages])
    setImageId(prev => [...prev, ...selected.map(img => img.id)])
    setModalStep(2)
    setSelectedSpaceImageIds([])
    setSpaceImagePage(1)
    const currentCover = form.getFieldValue('coverIndex')
    if (currentCover == null) {
      form.setFieldsValue({ coverIndex: 0 })
    }
  }, [selectedSpaceImageIds, spaceImages, uploadedImages.length, form])

  const handleSpacePickerConfirm = useCallback((selected) => {
    if (uploadedImagesRef.current.length + selected.length > 15) {
      message.warning('最多只能选择15张图片')
      return
    }
    const newImages = selected.map((img) => ({
      uid: `space-pick-${img.id}`,
      name: `space-image-${img.id}`,
      status: 'done',
      url: img.url,
      pictureId: img.id,
    }))
    setCurrentImageIndex(uploadedImages.length + newImages.length - 1)
    setShowUploadSlide(false)
    setUploadedImages(prev => [...prev, ...newImages])
    setImageId(prev => [...prev, ...selected.map(img => img.id)])
    setModalStep(2)
    const currentCover = form.getFieldValue('coverIndex')
    if (currentCover == null) {
      form.setFieldsValue({ coverIndex: 0 })
    }
  }, [uploadedImages.length, form])

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

  const handleImageUpload = async ({ file, onSuccess: onUploadSuccess, onError }) => {
    if (imageId.length >= 15) {
      message.error('最多只能上传15张图片')
      onError(new Error('超过图片数量限制'))
      return
    }

    const formData = new FormData()
    formData.append('file', file)

    try {
      const result = await uploadPicture(formData)

      const { url, id } = result
      onUploadSuccess({ url, pictureId: id })
      message.success('上传成功')

      setUploadedImages(prev => [...prev, { uid: file.uid, name: file.name, status: 'done', url, pictureId: id }])
      setImageId(prev => [...prev, id])
      setModalStep(2)
      const currentCover = form.getFieldValue('coverIndex')
      if (currentCover == null) {
        form.setFieldsValue({ coverIndex: 0 })
      }
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
      const currentImageIds = [...new Set(uploadedImagesRef.current
        .map(img => img.pictureId)
        .filter(Boolean))]

      if (isEditing) {
        const coverIndex = values.coverIndex ?? 0
        const editData = {
          id: editingPostId,
          title: values.title,
          content: values.content,
          isPrivate: values.isPrivate ? 1 : 0,
          cover: coverIndex,
        }
        if (currentImageIds.length > 0) {
          editData.imageId = currentImageIds
        }
        await editPost(editData)
        message.success('编辑成功！')
      } else {
        const coverIndex = values.coverIndex ?? 0
        const submitData = {
          imageId: currentImageIds,
          title: values.title,
          content: values.content,
          cover: coverIndex,
          isPrivate: values.isPrivate ? 1 : 0
        }
        await uploadPost(submitData)
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

  const renderStep1 = () => (
    <Modal
      open={open && modalStep === 1}
      onCancel={handleCancel}
      footer={null}
      closable={false}
      className={`create-post-modal${uploadTabKey === 'manual' ? ' create-post-modal-compact' : ''}`}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} autoComplete="off" className="post-form">
        <div className="upload-step-hint">至少上传一张图片</div>
        <Tabs
          className="upload-tabs"
          activeKey={uploadTabKey}
          onChange={(key) => {
            setUploadTabKey(key)
            if (key === 'space') {
              setSelectedSpaceImageIds([])
              if (spaceId) {
                fetchSpaceImages(1, imageId)
              }
            }
            if (key === 'team') {
              setTeamSpaceImagePage(1)
              setSelectedTeamSpaceImageIds([])
              fetchTeamSpaces()
            }
          }}
          items={[
            {
              key: 'manual',
              label: '手动上传',
              children: (
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
                    <div className="upload-step-desc">支持 JPG、PNG、GIF、WebP、HEIC 格式，单张图片不超过 {maxSizeText}，最多15张</div>
                  </div>
                </Upload>
              ),
            },
            {
              key: 'url',
              label: 'URL 导入',
              children: (
                <div className="space-select-tab" style={{ padding: '16px 0', display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <Input.Search
                    value={urlImportUrl}
                    onChange={(e) => setUrlImportUrl(e.target.value)}
                    placeholder="粘贴图片URL..."
                    size="large"
                    allowClear
                    enterButton="解析"
                    onSearch={handleStep1UrlResolve}
                  />
                  <div className="import-url-preview-box">
                    {urlPreviewUrl ? (
                      urlPreviewError ? (
                        <Empty description="无法预览该图片" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                      ) : (
                        <Image
                          src={urlPreviewUrl}
                          alt="预览"
                          style={{ maxHeight: 240, objectFit: 'contain' }}
                          onError={() => setUrlPreviewError(true)}
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
                  {urlResolved && (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                      <Button onClick={handleStep1UrlCancel}>取消</Button>
                      <Button
                        type="primary"
                        icon={<ImportOutlined />}
                        onClick={handleUrlImportFromStep}
                        loading={urlImporting}
                        size="large"
                      >
                        确认导入
                      </Button>
                    </div>
                  )}
                </div>
              ),
            },
            {
              key: 'space',
              label: '从私人空间选择',
              children: (
                <div className="space-select-tab">
                  {!spaceId ? (
                    <Empty description="暂无私人空间" style={{ padding: '60px 0' }} />
                  ) : (
                    <>
                      <Spin spinning={spaceImageLoading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
                        <div className="space-image-grid">
                          {spaceImages.map((img) => {
                            const orderIndex = selectedSpaceImageIds.indexOf(img.id)
                            const isSelected = orderIndex !== -1
                            const isInCarousel = img.flag === false
                            return (
                              <div
                                key={img.id}
                                className={`space-image-item${isSelected ? ' space-image-selected' : ''}${isInCarousel ? ' space-image-in-carousel' : ''}`}
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
            {
              key: 'team',
              label: '从团队空间选择',
              children: (
                <div className="space-select-tab">
                  <Spin spinning={teamSpacesLoading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
                    {teamSpaces.length === 0 ? (
                      <Empty description="暂未加入团队空间" style={{ padding: '60px 0' }} />
                    ) : teamSpaceView === 'list' ? (
                      <div className="team-space-list-view">
                        {teamSpaces.map((sp) => (
                          <div
                            key={sp.id}
                            className="team-space-list-card"
                            onClick={() => handleTeamSpaceSelect(sp.id)}
                          >
                            <TeamOutlined className="team-space-card-icon" />
                            <div className="team-space-card-info">
                              <span className="team-space-card-name">{sp.name}</span>
                              {sp.introduction && (
                                <span className="team-space-card-intro" title={sp.introduction}>{sp.introduction}</span>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <>
                        <div className="team-space-image-header">
                          <Button
                            type="link"
                            icon={<LeftOutlined />}
                            onClick={handleTeamSpaceBack}
                            className="team-space-back-btn"
                          >
                            返回空间列表
                          </Button>
                          <span className="team-space-image-title">
                            {teamSpaces.find(s => s.id === teamSpaceId)?.name || '团队空间'}
                          </span>
                        </div>
                        {!teamSpaceId ? (
                          <Empty description="请选择一个团队空间" style={{ padding: '60px 0' }} />
                        ) : (
                          <>
                            <Spin spinning={teamSpaceImageLoading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
                              <div className="space-image-grid">
                                {teamSpaceImages.map((img) => {
                                  const orderIndex = selectedTeamSpaceImageIds.indexOf(img.id)
                                  const isSelected = orderIndex !== -1
                                  const isInCarousel = img.flag === false
                                  return (
                                    <div
                                      key={img.id}
                                      className={`space-image-item${isSelected ? ' space-image-selected' : ''}${isInCarousel ? ' space-image-in-carousel' : ''}`}
                                      onClick={() => handleTeamSpaceImageToggle(img)}
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
                            </Spin>
                            {teamSpaceImages.length === 0 && !teamSpaceImageLoading && (
                              <Empty description="暂无图片" style={{ padding: '40px 0' }} />
                            )}
                            <div className="space-image-footer">
                              <Pagination
                                current={teamSpaceImagePage}
                                total={teamSpaceImageTotal}
                                pageSize={20}
                                size="small"
                                showSizeChanger={false}
                                showTotal={(total) => `共 ${total} 张`}
                                onChange={(page) => setTeamSpaceImagePage(page)}
                              />
                              <Button
                                type="primary"
                                icon={<CheckOutlined />}
                                disabled={selectedTeamSpaceImageIds.length === 0}
                                onClick={handleTeamSpaceConfirm}
                              >
                                确认选择 ({selectedTeamSpaceImageIds.length})
                              </Button>
                            </div>
                          </>
                        )}
                      </>
                    )}
                  </Spin>
                </div>
              ),
            },
          ]}
        />
      </Form>
    </Modal>
  )

  const renderStep2 = () => (
    <PostModal
      open={open && modalStep === 2}
      onClose={handleCancel}
      images={uploadedImages.map(img => img.url).filter(Boolean)}
      currentIndex={currentImageIndex}
      onIndexChange={(index) => { setCurrentImageIndex(index); setShowUploadSlide(false) }}
      onRemove={(_, index) => {
        const file = uploadedImages[index]
        if (file) handleImageRemove(file)
      }}
      onAddImage={customUploadRequest}
      showAddSlide={showUploadSlide}
      maxImages={15}
      onAddMore={() => { setCurrentImageIndex(prev => prev + 1); setShowUploadSlide(true) }}
      touchProps={{
        onTouchStart: handleTouchStart,
        onTouchMove: handleTouchMove,
        onTouchEnd: handleTouchEnd,
      }}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} autoComplete="off" className="post-form">
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
          <Form.Item label="帖子封面" name="coverIndex" layout="horizontal"
            style={{ marginBottom: 0 }}>
            <Select
              placeholder="选择帖子封面"
              disabled={uploadedImages.length === 0}
              onChange={(value) => {
                setCurrentImageIndex(value)
                setShowUploadSlide(false)
              }}
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
            <Popover
              open={popoverOpen}
              onOpenChange={(visible) => {
                setPopoverOpen(visible)
                if (!visible) { setUrlImportUrl(''); setUrlPreviewUrl(''); setUrlResolved(false); setUrlPreviewError(false) }
              }}
              trigger="click"
              placement="bottomLeft"
              title="从 URL 导入"
              content={
                <div style={{ width: 300, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <Input.Search
                    value={urlImportUrl}
                    onChange={(e) => setUrlImportUrl(e.target.value)}
                    placeholder="粘贴图片URL..."
                    allowClear
                    enterButton="解析"
                    onSearch={handleStep2UrlResolve}
                  />
                  {urlPreviewUrl && (
                    urlPreviewError ? (
                      <div style={{ color: '#ff4d4f', fontSize: 12 }}>无法预览该图片</div>
                    ) : (
                      <Image
                        src={urlPreviewUrl}
                        alt="预览"
                        style={{ maxHeight: 160, objectFit: 'contain' }}
                        onError={() => setUrlPreviewError(true)}
                        preview={false}
                      />
                    )
                  )}
                  {urlResolved && (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                      <Button size="small" onClick={handleStep2UrlCancel}>取消</Button>
                      <Button
                        size="small"
                        type="primary"
                        onClick={handleStep2UrlConfirm}
                        loading={urlImporting}
                      >
                        确认导入
                      </Button>
                    </div>
                  )}
                </div>
              }
            >
              <Button
                size="large"
                icon={<ImportOutlined />}
                disabled={imageId.length >= 15}
                className="url-import-button"
              >
                从URL导入
              </Button>
            </Popover>
          </div>
          <div className="modal-submit-right">
            {mode !== 'page' && (
              <>
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
              </>
            )}
          </div>
        </div>
      </Form>
    </PostModal>
  )

  if (mode === 'page') {
    return (
      <MobilePageWrapper
        title={isEditing ? '编辑帖子' : '发布帖子'}
        onClose={handleCancel}
        rightContent={
          modalStep === 2 ? (
            <Button
              type="primary"
              htmlType="submit"
              size="small"
              icon={<SendOutlined />}
              loading={submitLoading}
              className="modal-submit-button"
              onClick={() => form.submit()}
            >
              {isEditing ? '保存' : '发布'}
            </Button>
          ) : null
        }
      >
        {renderStep1()}
        <SpacePickerModal
          open={spacePickerOpen}
          onClose={() => setSpacePickerOpen(false)}
          onConfirm={handleSpacePickerConfirm}
          currentImageCount={existingImageIds.length}
          existingImageIds={existingImageIds}
        />
      </MobilePageWrapper>
    )
  }

  return (
    <>
      {renderStep1()}
      {renderStep2()}
      <SpacePickerModal
        open={spacePickerOpen}
        onClose={() => setSpacePickerOpen(false)}
        onConfirm={handleSpacePickerConfirm}
        currentImageCount={existingImageIds.length}
        existingImageIds={existingImageIds}
      />
    </>
  )
}

export default CreateEditPostModal
