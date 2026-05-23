import { useState, useEffect, useCallback, useContext } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getPost } from '../api'
import { AuthContext } from '../context/AuthContext'
import PostDetailModal from '../components/PostDetailModal'
import CreateEditPostModal from '../components/CreateEditPostModal'

export default function MobilePostDetailPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [postDetail, setPostDetail] = useState(null)
  const [detailImageIndex, setDetailImageIndex] = useState(0)
  const [showEditModal, setShowEditModal] = useState(false)

  const fetchDetail = useCallback(async () => {
    if (!postId) return
    setLoading(true)
    try {
      const result = await getPost(postId)
      setPostDetail(result)
    } catch {
      setPostDetail(null)
    } finally {
      setLoading(false)
    }
  }, [postId])

  useEffect(() => {
    fetchDetail()
  }, [fetchDetail])

  const handleClose = useCallback(() => {
    navigate(-1)
  }, [navigate])

  const handleEdit = useCallback(() => {
    setShowEditModal(true)
  }, [])

  const handleEditClose = useCallback(() => {
    setShowEditModal(false)
  }, [])

  const handleEditSuccess = useCallback(() => {
    setShowEditModal(false)
    fetchDetail()
  }, [fetchDetail])

  const handleCommentCountChange = useCallback((delta) => {
    setPostDetail((prev) => prev ? { ...prev, commentNum: (prev.commentNum || 0) + delta } : prev)
  }, [])

  if (showEditModal && postDetail) {
    return (
      <CreateEditPostModal
        mode="page"
        open={true}
        onClose={handleEditClose}
        editPostDetail={postDetail}
        onSuccess={handleEditSuccess}
      />
    )
  }

  return (
    <PostDetailModal
      mode="page"
      open={true}
      onClose={handleClose}
      loading={loading}
      postDetail={postDetail}
      detailImageIndex={detailImageIndex}
      onImageIndexChange={setDetailImageIndex}
      currentUsername={userInfo?.username}
      onEdit={handleEdit}
      onCommentCountChange={handleCommentCountChange}
    />
  )
}
