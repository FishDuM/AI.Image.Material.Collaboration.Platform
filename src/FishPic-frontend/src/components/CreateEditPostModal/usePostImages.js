import { useState, useEffect, useMemo, useRef } from 'react'

const usePostImages = ({ open, editPostDetail }) => {
  const [uploadedImages, setUploadedImages] = useState([])
  const [imageId, setImageId] = useState([])
  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const [showUploadSlide, setShowUploadSlide] = useState(false)

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
    } else if (open) {
      setUploadedImages([])
      setImageId([])
      setCurrentImageIndex(0)
      setShowUploadSlide(false)
    }
  }, [open, editPostDetail])

  const resetImages = () => {
    setUploadedImages([])
    setImageId([])
    setCurrentImageIndex(0)
    setShowUploadSlide(false)
  }

  return {
    uploadedImages,
    setUploadedImages,
    imageId,
    setImageId,
    currentImageIndex,
    setCurrentImageIndex,
    showUploadSlide,
    setShowUploadSlide,
    existingImageIds,
    uploadedImagesRef,
    resetImages,
  }
}

export default usePostImages
