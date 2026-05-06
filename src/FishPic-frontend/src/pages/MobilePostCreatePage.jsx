import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import CreateEditPostModal from '../components/CreateEditPostModal'

export default function MobilePostCreatePage() {
  const navigate = useNavigate()

  const handleClose = useCallback(() => {
    navigate(-1)
  }, [navigate])

  const handleSuccess = useCallback(() => {
    navigate('/', { replace: true })
  }, [navigate])

  return (
    <CreateEditPostModal
      mode="page"
      open={true}
      onClose={handleClose}
      onSuccess={handleSuccess}
    />
  )
}
