import { useState, useEffect, useCallback, useRef } from 'react'
import { spaceListPicture, deletePicture, updatePicture, getPictureEditMessage, submitAiTag } from '../api'
import { useFetchWithCleanup } from './useRequestUtils'
import { PAGE_SIZE, LOAD_MORE_THRESHOLD } from '../utils/constants'
import { isCanceledError } from '../utils/error'

/**
 * 图片数据获取 + 搜索 + 无限滚动
 */
export function usePictureFetch({ spaces = [], spaceId, pageSize = PAGE_SIZE, pagination = false }) {
  const [pictures, setPictures] = useState([])
  const [pictureLoading, setPictureLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [picturePage, setPicturePage] = useState(1)
  const [pictureTotal, setPictureTotal] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const currentPageRef = useRef(1)
  const loadingMoreRef = useRef(false)
  const { createSignal } = useFetchWithCleanup()
  const resolvedSpaceId = spaceId ?? spaces[0]?.id

  const fetchPictures = useCallback(async (spaceId, page, keyword, append = false, signal) => {
    if (!spaceId) return
    if (append) {
      if (loadingMoreRef.current) return
      loadingMoreRef.current = true
      setLoadingMore(true)
    } else {
      setPictureLoading(true)
    }
    setPicturePage(page)
    try {
      const params = { spaceId, current: page, pageSize }
      if (keyword && keyword.trim()) params.keyword = keyword.trim()
      const result = await spaceListPicture(params, signal ? { signal } : {})
      const list = Array.isArray(result?.records) ? result.records : []
      const total = typeof result?.total === 'number' ? result.total : list.length
      if (append) {
        setPictures(prev => {
          const existIds = new Set(prev.map(p => p.id))
          const unique = list.filter(p => !existIds.has(p.id))
          return unique.length > 0 ? [...prev, ...unique] : prev
        })
      } else {
        setPictures(list)
      }
      setPictureTotal(total)
      const totalPages = result.pages ?? Math.ceil((result.total || 0) / pageSize)
      currentPageRef.current = page
      setHasMore(page < totalPages)
    } catch (err) {
      if (isCanceledError(err)) return
      if (!append) setPictures([])
    } finally {
      setPictureLoading(false)
      setLoadingMore(false)
      loadingMoreRef.current = false
    }
  }, [pageSize])

  const refreshPictures = useCallback((spaceId, page, keyword, append) => {
    const signal = createSignal()
    fetchPictures(spaceId, page, keyword, append, signal)
  }, [fetchPictures, createSignal])

  useEffect(() => {
    if (resolvedSpaceId) {
      refreshPictures(resolvedSpaceId, 1)
    }
  }, [resolvedSpaceId, refreshPictures])

  useEffect(() => {
    if (pagination) return undefined
    const handleScroll = () => {
      if (loadingMoreRef.current || !hasMore || !resolvedSpaceId) return
      const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
      const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
      const clientHeight = document.documentElement.clientHeight || window.innerHeight
      if (scrollTop + clientHeight >= scrollHeight - LOAD_MORE_THRESHOLD) {
        refreshPictures(resolvedSpaceId, currentPageRef.current + 1, searchKeyword, true)
      }
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [refreshPictures, hasMore, resolvedSpaceId, searchKeyword, pagination])

  const handleSearch = useCallback(() => {
    if (resolvedSpaceId) {
      refreshPictures(resolvedSpaceId, 1, searchKeyword)
    }
  }, [resolvedSpaceId, refreshPictures, searchKeyword])

  const handleSearchReset = useCallback(() => {
    setSearchKeyword('')
    if (resolvedSpaceId) {
      refreshPictures(resolvedSpaceId, 1, '')
    }
  }, [resolvedSpaceId, refreshPictures])

  return {
    pictures,
    pictureLoading,
    picturePage,
    pictureTotal,
    searchKeyword,
    setSearchKeyword,
    hasMore,
    loadingMore,
    handleSearch,
    handleSearchReset,
    refreshPictures,
  }
}

/**
 * 批量选择 + 批量删除
 */
export function useBatchSelection({ spaces = [], spaceId, searchKeyword, refreshPictures, refreshSpaces, message, onAfterDelete }) {
  const [batchMode, setBatchMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const resolvedSpaceId = spaceId ?? spaces[0]?.id

  const toggleBatchMode = useCallback(() => {
    setBatchMode(prev => {
      if (prev) setSelectedIds([])
      return !prev
    })
  }, [])

  const toggleSelect = useCallback((pictureId) => {
    setSelectedIds(prev =>
      prev.includes(pictureId) ? prev.filter(id => id !== pictureId) : [...prev, pictureId]
    )
  }, [])

  const handleBatchDelete = useCallback(async () => {
    if (selectedIds.length === 0) {
      message.warning('请先选择要删除的图片')
      return
    }
    try {
      await deletePicture(selectedIds)
      message.success('删除成功')
      setSelectedIds([])
      setBatchMode(false)
      if (resolvedSpaceId) {
        refreshPictures(resolvedSpaceId, 1, searchKeyword)
        refreshSpaces?.()
        onAfterDelete?.()
      }
    } catch (error) {
      if (isCanceledError(error)) return
      message.error(error.message || '批量删除失败')
    }
  }, [selectedIds, resolvedSpaceId, refreshPictures, searchKeyword, refreshSpaces, onAfterDelete, message])

  return {
    batchMode,
    selectedIds,
    setSelectedIds,
    setBatchMode,
    toggleBatchMode,
    toggleSelect,
    handleBatchDelete,
  }
}

/**
 * 图片编辑 + 上传 + AI标注
 */
export function usePictureEditUpload({
  selectedIds,
  setSelectedIds,
  setBatchMode,
  pictures,
  spaces = [],
  spaceId,
  picturePage = 1,
  searchKeyword,
  refreshPictures,
  refreshSpaces,
  message,
  modal,
  navigate,
  isMobile,
  Form,
}) {
  const [showEditPicture, setShowEditPicture] = useState(false)
  const [editPictureLoading, setEditPictureLoading] = useState(false)
  const [editPictureForm] = Form.useForm()
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [showImageEditor, setShowImageEditor] = useState(false)
  const resolvedSpaceId = spaceId ?? spaces[0]?.id

  const handleEditPictureOpen = useCallback(() => {
    if (selectedIds.length === 0) {
      message.warning('请先选择图片')
      return
    }
    if (selectedIds.length > 1) {
      message.warning('一次只能编辑一张图片')
      return
    }
    if (isMobile) {
      const pic = pictures.find(p => p.id === selectedIds[0])
      navigate('/mobile/picture/edit', {
        state: {
          pictureId: selectedIds[0],
          pictureUrl: pic?.url,
          pictureName: pic?.pictureName,
          introduction: pic?.introduction,
        }
      })
      return
    }
    editPictureForm.resetFields()
    setShowEditPicture(true)
    getPictureEditMessage(selectedIds[0]).then(result => {
      if (result) {
        editPictureForm.setFieldsValue({
          pictureName: result.pictureName || '',
          introduction: result.introduction || '',
          tags: Array.isArray(result.tags) ? result.tags : [],
        })
      }
    }).catch(error => { console.error('[getPictureEditMessage]', error) })
  }, [selectedIds, pictures, isMobile, navigate, message, editPictureForm])

  const handleUploadSuccess = useCallback(() => {
    setShowUploadModal(false)
    if (resolvedSpaceId) {
      refreshPictures(resolvedSpaceId, 1, searchKeyword)
      refreshSpaces?.()
    }
  }, [resolvedSpaceId, refreshPictures, searchKeyword, refreshSpaces])

  const handleEditPictureSubmit = useCallback(async (values) => {
    setEditPictureLoading(true)
    try {
      await updatePicture({
        id: selectedIds[0],
        pictureName: values.pictureName || undefined,
        introduction: values.introduction || undefined,
        tags: values.tags || undefined,
      })
      message.success('编辑成功')
      setShowEditPicture(false)
      editPictureForm.resetFields()
      setSelectedIds?.([])
      setBatchMode?.(false)
      if (resolvedSpaceId) {
        refreshPictures(resolvedSpaceId, picturePage, searchKeyword)
        refreshSpaces?.()
      }
    } catch (error) {
      if (isCanceledError(error)) return
      message.error(error.message || '编辑失败')
    } finally {
      setEditPictureLoading(false)
    }
  }, [selectedIds, resolvedSpaceId, picturePage, searchKeyword, refreshPictures, refreshSpaces, setSelectedIds, setBatchMode, message, editPictureForm])

  const handleAiTag = useCallback(async () => {
    try {
      await submitAiTag(selectedIds[0])
      setShowEditPicture(false)
      modal.info({
        title: 'AI正在执行',
        content: 'AI正在后台识别图片信息，完成后将自动填充，请稍后重新打开编辑查看',
        okText: '知道了',
      })
    } catch (e) {
      message.error(e.message || 'AI识别提交失败')
    }
  }, [selectedIds, modal, message])

  return {
    showEditPicture,
    setShowEditPicture,
    editPictureLoading,
    editPictureForm,
    showUploadModal,
    setShowUploadModal,
    showImageEditor,
    setShowImageEditor,
    handleEditPictureOpen,
    handleUploadSuccess,
    handleEditPictureSubmit,
    handleAiTag,
  }
}
