import { useState, useCallback, useRef, useEffect } from 'react'
import ReactCrop, { makeAspectCrop, centerCrop, convertToPixelCrop } from 'react-image-crop'
import 'react-image-crop/dist/ReactCrop.css'
import { Slider, Button, Space, Spin, Tag, App } from 'antd'
import {
  ZoomInOutlined,
  ZoomOutOutlined,
  RotateLeftOutlined,
  RotateRightOutlined,
  UndoOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import './ImageCropper.css'

const MIN_CROP = 50

function normalizeRotation(deg) {
  let r = deg % 360
  if (r < 0) r += 360
  return r
}

function loadImageInfo(imageUrl) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight })
    img.onerror = reject
    img.src = imageUrl
  })
}

function adaptCropForRotation(cropPixels, rotation, imgW, imgH) {
  const { x, y, width, height } = cropPixels
  switch (normalizeRotation(rotation)) {
    case 0:   return { x, y, width, height }
    case 90:  return { x: y, y: imgW - x - width, width: height, height: width }
    case 180: return { x: imgW - x - width, y: imgH - y - height, width, height }
    case 270: return { x: imgH - y - height, y: x, width: height, height: width }
    default:  return { x, y, width, height }
  }
}

export default function ImageCropper({ imageUrl, onSave, onCancel }) {
  const { message } = App.useApp()
  const containerRef = useRef(null)
  const [imgElement, setImgElement] = useState(null)
  const imgRef = useCallback((node) => { if (node) setImgElement(node) }, [])
  const [crop, setCrop] = useState()
  const [completedCrop, setCompletedCrop] = useState(null)
  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [naturalSize, setNaturalSize] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    loadImageInfo(imageUrl).then((size) => {
      if (cancelled) return
      setNaturalSize(size)

      setCrop(
        centerCrop(
          makeAspectCrop({ unit: '%', width: 80 }, size.width / size.height, size.width, size.height),
          size.width,
          size.height
        )
      )
      setCompletedCrop(null)
      setLoading(false)
    }).catch(() => {
      if (!cancelled) {
        setLoading(false)
        message.error('图片加载失败')
      }
    })

    return () => { cancelled = true }
  }, [imageUrl, message])

  const onImageLoad = useCallback(() => {}, [])

  const handleZoomIn  = useCallback(() => setZoom((prev) => Math.min(prev + 0.5, 5)), [])
  const handleZoomOut = useCallback(() => setZoom((prev) => Math.max(prev - 0.5, 0.5)), [])
  const handleRotateLeft  = useCallback(() => setRotation((prev) => (prev - 90) % 360), [])
  const handleRotateRight = useCallback(() => setRotation((prev) => (prev + 90) % 360), [])

  const handleReset = useCallback(() => {
    setZoom(1)
    setRotation(0)
  }, [])

  const getPixelCrop = useCallback(() => {
    if (!naturalSize) return null
    const effectiveCrop = completedCrop || crop
    if (!effectiveCrop) return null
    const isSwapped = normalizeRotation(rotation) === 90 || normalizeRotation(rotation) === 270
    const renderedW = imgElement
      ? (isSwapped ? imgElement.clientHeight : imgElement.clientWidth)
      : (isSwapped ? naturalSize.height : naturalSize.width)
    const renderedH = imgElement
      ? (isSwapped ? imgElement.clientWidth : imgElement.clientHeight)
      : (isSwapped ? naturalSize.width : naturalSize.height)
    return convertToPixelCrop(effectiveCrop, renderedW, renderedH)
  }, [completedCrop, crop, naturalSize, rotation, imgElement])

  const handleSave = useCallback(async () => {
    const pixelCrop = getPixelCrop()
    if (!pixelCrop || !naturalSize) {
      message.warning('请先加载图片')
      return
    }
    if (pixelCrop.width < MIN_CROP || pixelCrop.height < MIN_CROP) {
      message.warning('请先调整裁剪区域')
      return
    }

    const img = imgElement
    const isSwapped = normalizeRotation(rotation) === 90 || normalizeRotation(rotation) === 270
    const renderedW = img
      ? (isSwapped ? img.clientHeight : img.clientWidth)
      : (isSwapped ? naturalSize.height : naturalSize.width)
    const renderedH = img
      ? (isSwapped ? img.clientWidth : img.clientHeight)
      : (isSwapped ? naturalSize.width : naturalSize.height)
    const scaleX = (isSwapped ? naturalSize.height : naturalSize.width) / renderedW
    const scaleY = (isSwapped ? naturalSize.width : naturalSize.height) / renderedH

    const naturalCrop = {
      x: pixelCrop.x * scaleX,
      y: pixelCrop.y * scaleY,
      width: pixelCrop.width * scaleX,
      height: pixelCrop.height * scaleY,
    }

    const adapted = adaptCropForRotation(naturalCrop, rotation, naturalSize.width, naturalSize.height)

    setSaving(true)
    try {
      await onSave?.({
        x: Math.round(adapted.x),
        y: Math.round(adapted.y),
        width: Math.round(adapted.width),
        height: Math.round(adapted.height),
        rotation: normalizeRotation(rotation),
      })
      message.success('图片裁剪成功')
    } catch (error) {
      message.error(error.message || '图片处理失败')
    } finally {
      setSaving(false)
    }
  }, [getPixelCrop, rotation, naturalSize, onSave, message, imgElement])

  if (!imageUrl) return null

  const normRot = normalizeRotation(rotation)
  const pixelCrop = getPixelCrop()
  const zoomPct = Math.round(zoom * 100)

  const imgStyle = naturalSize
    ? {
        display: 'block',
        maxWidth: `${zoomPct}%`,
        maxHeight: `${zoomPct}%`,
        width: 'auto',
        height: 'auto',
        transition: 'max-width 0.2s ease, max-height 0.2s ease',
      }
    : {
        display: 'block',
        maxWidth: '100%',
        maxHeight: '100%',
        transition: 'max-width 0.2s ease, max-height 0.2s ease',
      }

  return (
    <div className="image-cropper-wrapper">
      <div className="image-cropper-main" ref={containerRef}>
        {loading && (
          <div className="image-cropper-loading">
            <Spin indicator={<LoadingOutlined spin />} size="large" />
          </div>
        )}
        <div
          className="image-cropper-rotate-wrapper"
          style={{
            transform: `rotate(${normRot}deg)`,
            transformOrigin: 'center center',
            transition: 'transform 0.35s ease',
            display: loading ? 'none' : 'inline-block',
          }}
        >
          <ReactCrop
            crop={crop}
            onChange={(c) => setCrop(c)}
            onComplete={(c) => setCompletedCrop(c)}
            minWidth={MIN_CROP}
            minHeight={MIN_CROP}
            keepSelection
            ruleOfThirds
            className="react-image-crop-container"
          >
            <img
              ref={imgRef}
              src={imageUrl}
              onLoad={onImageLoad}
              alt=""
              style={imgStyle}
            />
          </ReactCrop>
        </div>
      </div>

      <div className="image-cropper-controls">
        <div className="image-cropper-controls-row">
          <Space size="small">
            <Button icon={<ZoomOutOutlined />} size="small" onClick={handleZoomOut} title="缩小" disabled={loading || zoom <= 0.5} />
            <div className="image-cropper-slider">
              <Slider
                min={0.5}
                max={5}
                step={0.1}
                value={zoom}
                onChange={setZoom}
                disabled={loading}
                tooltip={{ formatter: (v) => `${Math.round(v * 100)}%` }}
              />
            </div>
            <Button icon={<ZoomInOutlined />} size="small" onClick={handleZoomIn} title="放大" disabled={loading || zoom >= 5} />
          </Space>
          {pixelCrop && (
            <span className="crop-size-info">
              {Math.round(pixelCrop.width)} × {Math.round(pixelCrop.height)}
            </span>
          )}
        </div>

        <div className="image-cropper-controls-row">
          <Space size="small">
            <Button icon={<RotateLeftOutlined />} size="small" onClick={handleRotateLeft} title="左旋90°" disabled={loading}>左旋</Button>
            <Button icon={<RotateRightOutlined />} size="small" onClick={handleRotateRight} title="右旋90°" disabled={loading}>右旋</Button>
            <Button icon={<UndoOutlined />} size="small" onClick={handleReset} title="重置" disabled={loading || (zoom === 1 && rotation === 0)}>重置</Button>
          </Space>
          {normRot !== 0 && (
            <Tag color="blue" className="crop-rotation-tag">旋转 {normRot}°</Tag>
          )}
        </div>
      </div>

      <div className="image-cropper-footer">
        <Button onClick={onCancel} disabled={saving}>取消</Button>
        <Button type="primary" onClick={handleSave} loading={saving} disabled={loading}>
          确认裁剪
        </Button>
      </div>
    </div>
  )
}
