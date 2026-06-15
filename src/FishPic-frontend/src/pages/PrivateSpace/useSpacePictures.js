import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { Form } from 'antd'
import { spaceListPicture, deletePicture, updatePicture, getPictureEditMessage, submitAiTag } from '../../api'
import { useFetchWithCleanup } from '../../hooks/useRequestUtils'
import { PAGE_SIZE, LOAD_MORE_THRESHOLD } from '../../utils/constants'
import { logError } from '../../utils/logger'

export function useSpacePictures({ spaces, pageSize = PAGE_SIZE, refreshSpaces, message, modal, navigate, isMobile, userInfo, systemTags }) {
  const [pictures, setPictures] = useState([])
  const [pictureLoading, setPictureLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const currentPageRef = useRef(1)
  const loadingMoreRef = useRef(false)

  const [batchMode, setBatchMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])

  const [showEditPicture, setShowEditPicture] = useState(false)
  const [editPictureLoading, setEditPictureLoading] = useState(false)
  const [editPictureForm] = Form.useForm()
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [showImageEditor, setShowImageEditor] = useState(false)

  const { createSignal } = useFetchWithCleanup()

  const fetchPictures = useCallback(async (spaceId, page, keyword, append = false, signal) => {
    if (append) {
      if (loadingMoreRef.current) return
      loadingMoreRef.current = true
      setLoadingMore(true)
    } else {
      setPictureLoading(true)
    }
    try {
      const params = {
        spaceId,
        current: page,
        pageSize,
      }
      if (keyword && keyword.trim()) {
        params.keyword = keyword.trim()
      }
      const result = await spaceListPicture(params, signal ? { signal } : {})
      const list = Array.isArray(result?.records) ? result.records : []
      if (append) {
        setPictures(prev => {
          const existIds = new Set(prev.map(p => p.id))
          const unique = list.filter(p => !existIds.has(p.id))
          return unique.length > 0 ? [...prev, ...unique] : prev
        })
      } else {
        setPictures(list)
      }
      const totalPages = result.pages ?? Math.ceil((result.total || 0) / pageSize)
      currentPageRef.current = page
      setHasMore(page < totalPages)
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      if (!append) {
        setPictures([])
      }
    } finally {
      setPictureLoading(false)
      setLoadingMore(false)
      loadingMoreRef.current = false
    }
  }, [pageSize])

  const doFetchPictures = useCallback((spaceId, page, keyword, append = false) => {
    const signal = createSignal()
    fetchPictures(spaceId, page, keyword, append, signal)
  }, [fetchPictures, createSignal])

  useEffect(() => {
    const load = async () => {
      if (spaces.length > 0 && spaces[0].id) {
        doFetchPictures(spaces[0].id, 1)
      }
    }
    load()
  }, [spaces, doFetchPictures])

  useEffect(() => {
    const handleScroll = () => {
      if (loadingMoreRef.current || !hasMore || !spaces.length || !spaces[0]?.id) return
      const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
      const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
      const clientHeight = document.documentElement.clientHeight || window.innerHeight
      if (scrollTop + clientHeight >= scrollHeight - LOAD_MORE_THRESHOLD) {
        doFetchPictures(spaces[0].id, currentPageRef.current + 1, searchKeyword, true)
      }
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [doFetchPictures, hasMore, spaces, searchKeyword])

  const handleSearch = useCallback(() => {
    if (spaces.length > 0 && spaces[0].id) {
      doFetchPictures(spaces[0].id, 1, searchKeyword)
    }
  }, [spaces, doFetchPictures, searchKeyword])

  const handleSearchReset = useCallback(() => {
    setSearchKeyword('')
    if (spaces.length > 0 && spaces[0].id) {
      doFetchPictures(spaces[0].id, 1, '')
    }
  }, [spaces, doFetchPictures])

  const toggleBatchMode = useCallback(() => {
    setBatchMode((prev) => {
      if (prev) setSelectedIds([])
      return !prev
    })
  }, [])

  const toggleSelect = useCallback((pictureId) => {
    setSelectedIds((prev) =>
      prev.includes(pictureId) ? prev.filter((id) => id !== pictureId) : [...prev, pictureId]
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
      if (spaces.length > 0 && spaces[0].id) {
        doFetchPictures(spaces[0].id, 1, searchKeyword)
        refreshSpaces()
      }
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '批量删除失败')
    }
  }, [selectedIds, spaces, doFetchPictures, searchKeyword, refreshSpaces, message])

  const handleEditPictureOpen = () => {
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
    }).catch((error) => { logError('getPictureEditMessage', error) })
  }

  const handleUploadSuccess = useCallback(() => {
    setShowUploadModal(false)
    if (spaces.length > 0 && spaces[0].id) {
      doFetchPictures(spaces[0].id, 1, searchKeyword)
      refreshSpaces()
    }
  }, [spaces, doFetchPictures, searchKeyword, refreshSpaces])

  const handleEditPictureSubmit = async (values) => {
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
      setSelectedIds([])
      setBatchMode(false)
      if (spaces.length > 0 && spaces[0].id) {
        doFetchPictures(spaces[0].id, 1, searchKeyword)
      }
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '编辑失败')
    } finally {
      setEditPictureLoading(false)
    }
  }

  const masonryItems = useMemo(() => pictures.map((pic) => ({
    key: `pic-${pic.id}`,
    data: pic,
  })), [pictures])

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
    pictures,
    pictureLoading,
    searchKeyword,
    setSearchKeyword,
    hasMore,
    loadingMore,
    batchMode,
    selectedIds,
    showEditPicture,
    setShowEditPicture,
    editPictureLoading,
    editPictureForm,
    showUploadModal,
    setShowUploadModal,
    showImageEditor,
    setShowImageEditor,
    masonryItems,
    handleSearch,
    handleSearchReset,
    toggleBatchMode,
    toggleSelect,
    handleBatchDelete,
    handleEditPictureOpen,
    handleUploadSuccess,
    handleEditPictureSubmit,
    handleAiTag,
    refreshPictures: doFetchPictures,
  }
}
