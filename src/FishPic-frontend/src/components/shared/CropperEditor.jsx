import { useRef, useEffect, useCallback, forwardRef, useImperativeHandle, useState } from 'react'
import { Button, Space } from 'antd'
import { RotateLeftOutlined, RotateRightOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

const CropperEditor = forwardRef(function CropperEditor(
  { src, aspectRatio = NaN, onReady },
  ref,
) {
  const imgRef = useRef(null)
  const cropperRef = useRef(null)
  const [ready, setReady] = useState(false)
  const [loadError, setLoadError] = useState(false)

  const destroyCropper = useCallback(() => {
    cropperRef.current?.destroy()
    cropperRef.current = null
    setReady(false)
  }, [])

  useImperativeHandle(ref, () => ({
    getCropper: () => cropperRef.current,
  }))

  useEffect(() => {
    if (!src) {
      destroyCropper()
      setLoadError(false)
      return
    }

    const img = imgRef.current
    if (!img) return

    setLoadError(false)

    const init = () => {
      destroyCropper()
      cropperRef.current = new Cropper(img, {
        viewMode: 1,
        autoCropArea: 1,
        dragMode: 'crop',
        background: false,
        aspectRatio,
      })
      setReady(true)
      onReady?.()
    }

    const handleError = () => {
      setLoadError(true)
      setReady(false)
    }

    if (img.complete) {
      if (img.naturalWidth > 0) {
        init()
      } else {
        handleError()
      }
    } else {
      img.addEventListener('load', init)
      img.addEventListener('error', handleError)
    }

    return () => {
      img.removeEventListener('load', init)
      img.removeEventListener('error', handleError)
      destroyCropper()
    }
  }, [src, aspectRatio, destroyCropper, onReady])

  const handleRotate = (degree) => {
    const cropper = cropperRef.current
    if (!cropper) return
    const { x, y, width, height } = cropper.getData()
    cropper.rotate(degree)
    cropper.setData({ x, y, width, height })
  }

  return (
    <>
      <div className="image-cropper-container">
        {src ? (
          loadError ? (
            <div className="image-cropper-loading" style={{ color: '#ff4d4f' }}>图片加载失败</div>
          ) : (
            <img ref={imgRef} src={src} alt="裁剪预览" />
          )
        ) : (
          <div className="image-cropper-loading">加载中...</div>
        )}
      </div>
      {src && ready && (
        <div className="image-cropper-toolbar">
          <Space>
            <Button icon={<ZoomOutOutlined />} onClick={() => cropperRef.current?.zoom(-0.1)} size="small" title="缩小" />
            <Button icon={<ZoomInOutlined />} onClick={() => cropperRef.current?.zoom(0.1)} size="small" title="放大" />
            <Button icon={<RotateLeftOutlined />} onClick={() => handleRotate(-90)} size="small" title="左旋转" />
            <Button icon={<RotateRightOutlined />} onClick={() => handleRotate(90)} size="small" title="右旋转" />
          </Space>
        </div>
      )}
    </>
  )
})

export default CropperEditor
