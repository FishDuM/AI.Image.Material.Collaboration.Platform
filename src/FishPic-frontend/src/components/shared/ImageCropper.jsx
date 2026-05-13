import { useState, useCallback } from 'react'
import Cropper from 'react-easy-crop'
import { Slider, Button, Space, App } from 'antd'
import {
  ZoomInOutlined,
  ZoomOutOutlined,
  RotateLeftOutlined,
  RotateRightOutlined,
  UndoOutlined,
} from '@ant-design/icons'
import './ImageCropper.css'

function normalizeRotation(deg) {
  let r = deg % 360
  if (r < 0) r += 360
  return r
}

export default function ImageCropper({ imageUrl, onSave, onCancel }) {
  const { message } = App.useApp()
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [croppedAreaPixels, setCroppedAreaPixels] = useState(null)
  const [saving, setSaving] = useState(false)

  const onCropComplete = useCallback((_, croppedAreaPixels) => {
    setCroppedAreaPixels(croppedAreaPixels)
  }, [])

  const handleZoomIn = useCallback(() => {
    setZoom((prev) => Math.min(prev + 0.5, 5))
  }, [])

  const handleZoomOut = useCallback(() => {
    setZoom((prev) => Math.max(prev - 0.5, 0.5))
  }, [])

  const handleRotateLeft = useCallback(() => {
    setRotation((prev) => (prev - 90) % 360)
  }, [])

  const handleRotateRight = useCallback(() => {
    setRotation((prev) => (prev + 90) % 360)
  }, [])

  const handleReset = useCallback(() => {
    setCrop({ x: 0, y: 0 })
    setZoom(1)
    setRotation(0)
  }, [])

  const handleSave = useCallback(async () => {
    if (!croppedAreaPixels) {
      message.warning('请先调整裁剪区域')
      return
    }
    setSaving(true)
    try {
      onSave?.({
        x: croppedAreaPixels.x,
        y: croppedAreaPixels.y,
        width: croppedAreaPixels.width,
        height: croppedAreaPixels.height,
        rotation: normalizeRotation(rotation),
      })
    } catch (error) {
      message.error(error.message || '图片处理失败')
    } finally {
      setSaving(false)
    }
  }, [croppedAreaPixels, rotation, onSave, message])

  if (!imageUrl) return null

  return (
    <div className="image-cropper-wrapper">
      <div className="image-cropper-main">
        <Cropper
          image={imageUrl}
          crop={crop}
          zoom={zoom}
          rotation={rotation}
          aspect={4 / 3}
          onCropChange={setCrop}
          onCropComplete={onCropComplete}
          onZoomChange={setZoom}
          zoomWithScroll
          cropShape="rect"
          showGrid
          style={{
            containerStyle: { background: '#1a1a1a' },
            cropAreaStyle: { border: '2px solid #1677ff', boxShadow: '0 0 0 9999em rgba(0, 0, 0, 0.5)' },
          }}
        />
      </div>

      <div className="image-cropper-controls">
        <div className="image-cropper-controls-row">
          <Space size="small">
            <Button icon={<ZoomOutOutlined />} size="small" onClick={handleZoomOut} title="缩小" />
            <div className="image-cropper-slider">
              <Slider
                min={0.5}
                max={5}
                step={0.1}
                value={zoom}
                onChange={setZoom}
                tooltip={{ formatter: (v) => `${Math.round(v * 100)}%` }}
              />
            </div>
            <Button icon={<ZoomInOutlined />} size="small" onClick={handleZoomIn} title="放大" />
          </Space>
        </div>

        <div className="image-cropper-controls-row">
          <Space size="small">
            <Button icon={<RotateLeftOutlined />} size="small" onClick={handleRotateLeft} title="左旋90°">
              左旋
            </Button>
            <Button icon={<RotateRightOutlined />} size="small" onClick={handleRotateRight} title="右旋90°">
              右旋
            </Button>
            <Button icon={<UndoOutlined />} size="small" onClick={handleReset} title="重置">
              重置
            </Button>
          </Space>
        </div>
      </div>

      <div className="image-cropper-footer">
        <Button onClick={onCancel}>取消</Button>
        <Button type="primary" onClick={handleSave} loading={saving}>
          确认裁剪
        </Button>
      </div>
    </div>
  )
}