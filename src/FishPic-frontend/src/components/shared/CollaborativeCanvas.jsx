import { useState, useRef, useCallback, useEffect, useMemo } from 'react'
import { Modal, Button, Spin, App, Avatar, Tooltip } from 'antd'
import {
  ZoomInOutlined, ZoomOutOutlined,
  RotateLeftOutlined, RotateRightOutlined,
  SaveOutlined, UserOutlined, UndoOutlined,
  LockOutlined, UnlockOutlined, ScissorOutlined,
  CheckOutlined, CloseOutlined
} from '@ant-design/icons'
import { useCollabWebSocket } from '../../hooks/useCollabWebSocket'
import { replacePictureFile } from '../../api'
import { getToken } from '../../utils/storage'
import CropperEditor from './CropperEditor'
import './CollaborativeCanvas.css'

const sameId = (left, right) => String(left) === String(right)
const hasId = (value) => value != null && value !== ''

export default function CollaborativeCanvas({ open, imageUrl, pictureId, spaceId, updatedAt, onSuccess, onClose, onFileReplaced }) {
  const { message } = App.useApp()
  const [scale, setScale] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true)
  const [onlineUsers, setOnlineUsers] = useState([])
  const [reloadTick, setReloadTick] = useState(0)
  const [myUserId, setMyUserId] = useState(null)
  const [imageNaturalSize, setImageNaturalSize] = useState(null)

  const [lockedBy, setLockedBy] = useState(null)
  const [lockedNickname, setLockedNickname] = useState('')

  const [cropMode, setCropMode] = useState(false)
  const [cropData, setCropData] = useState(null) // { x, y, w, h } 原图像素坐标

  const [history, setHistory] = useState([])

  const proxyUrlRef = useRef('')
  const containerRef = useRef(null)
  const myUserIdRef = useRef(null)
  const myNicknameRef = useRef('')
  const cropperRef = useRef(null)
  const pendingLockRef = useRef(false)
  const wasMyLockRef = useRef(false)
  const lockedByRef = useRef(null)
  const onCloseRef = useRef(onClose)
  const onFileReplacedRef = useRef(onFileReplaced)
  useEffect(() => { onCloseRef.current = onClose }, [onClose])
  useEffect(() => { onFileReplacedRef.current = onFileReplaced }, [onFileReplaced])

  const isMyLock = lockedBy != null && hasId(myUserId) && sameId(lockedBy, myUserId)
  const isOtherLock = lockedBy != null && (!hasId(myUserId) || !sameId(lockedBy, myUserId))
  const isEditable = isMyLock
  const isMyLockRef = useRef(false)
  useEffect(() => { isMyLockRef.current = isMyLock }, [isMyLock])

  const displayRotation = ((rotation % 360) + 360) % 360

  const proxyUrl = useMemo(() => {
    if (!imageUrl) return ''
    const base = imageUrl.replace(/^https?:\/\/[^/]+/, '/cos-proxy')
    const v = updatedAt || 0
    return `${base}?v=${v + reloadTick}`
  }, [imageUrl, updatedAt, reloadTick])

  // 裁剪 clip-path（原图坐标 → 百分比）
  const cropClipPath = useMemo(() => {
    if (!cropData) return 'none'
    if (!imageNaturalSize?.width || !imageNaturalSize?.height) return 'none'
    const nw = imageNaturalSize.width, nh = imageNaturalSize.height
    const top = (cropData.y / nh * 100)
    const left = (cropData.x / nw * 100)
    const bottom = ((nh - cropData.y - cropData.h) / nh * 100)
    const right = ((nw - cropData.x - cropData.w) / nw * 100)
    return `inset(${top}% ${right}% ${bottom}% ${left}%)`
  }, [cropData, imageNaturalSize])

  const handleMessage = useCallback((data) => {
    switch (data.type) {
      case 'transform':
        if (sameId(data.pictureId, pictureId)) {
          setScale(data.scale)
          setRotation(data.rotation)
          setCropData(data.crop || null)
        }
        break
      case 'lock':
        if (sameId(data.pictureId, pictureId)) {
          const prevLockedBy = lockedByRef.current
          setLockedBy(data.userId)
          setLockedNickname(data.nickname || '')
          lockedByRef.current = data.userId
          pendingLockRef.current = false
          // 仅当锁定者发生变化时才提示（避免重连 resync 重复弹）
          if (hasId(myUserIdRef.current) && !sameId(data.userId, myUserIdRef.current) && !sameId(data.userId, prevLockedBy)) {
            message.info(`${data.nickname || '用户'} 开始编辑`)
          }
          if (hasId(myUserIdRef.current) && sameId(data.userId, myUserIdRef.current)) {
            wasMyLockRef.current = true
          }
        }
        break
      case 'lock-denied':
        if (sameId(data.pictureId, pictureId)) {
          setLockedBy(data.userId)
          setLockedNickname(data.nickname || '')
          lockedByRef.current = data.userId
          pendingLockRef.current = false
          wasMyLockRef.current = false
          message.warning(`${data.nickname || '用户'} 正在编辑，当前仅查看`)
        }
        break
      case 'unlock':
        if (sameId(data.pictureId, pictureId)) {
          setLockedBy(null); setLockedNickname('')
          lockedByRef.current = null
          wasMyLockRef.current = false
        }
        break
      case 'presence':
        setOnlineUsers(data.users || [])
        if (data.users) {
          const me = data.users.find(u => sameId(u.userId, myUserIdRef.current))
          if (me) myNicknameRef.current = me.nickname || ''
        }
        break
      case 'join':
        setOnlineUsers(prev => {
          if (prev.some(u => sameId(u.userId, data.userId))) return prev
          return [...prev, data]
        })
        break
      case 'leave':
        setOnlineUsers(prev => prev.filter(u => !sameId(u.userId, data.userId)))
        break
      case 'file-replaced':
        if (sameId(data.pictureId, pictureId)) {
          const isMyReplace = hasId(myUserIdRef.current) && sameId(data.userId, myUserIdRef.current)
          if (isMyReplace) {
            // 保存者自己：重新加载图片
            setScale(1); setRotation(0); setCropData(null)
            setHistory([])
            setReloadTick(prev => prev + 1)
          } else {
            // 其他查看者：关闭弹窗并刷新列表
            const fromName = data.fromNickname || '其他用户'
            message.info(`图片已被 ${fromName} 更新`)
            onFileReplacedRef.current?.()
            onCloseRef.current?.()
          }
        }
        break
      default: break
    }
  }, [pictureId, message])

  const sendMsgRef = useRef(null)

  const handleWsReady = useCallback(() => {
    if (pendingLockRef.current) {
      pendingLockRef.current = false
      sendMsgRef.current?.({ type: 'lock', pictureId })
    }
  }, [pictureId])

  const { sendMessage, connected } = useCollabWebSocket(
    open ? spaceId : null,
    handleMessage,
    () => { setOnlineUsers([]) },
    handleWsReady
  )
  useEffect(() => { sendMsgRef.current = sendMessage }, [sendMessage])

  // 连接就绪后自动发送待发的 lock 请求（解决首次连接慢导致 lock 丢失的问题）
  useEffect(() => {
    if (connected && pendingLockRef.current) {
      pendingLockRef.current = false
      sendMessage({ type: 'lock', pictureId })
    }
  }, [connected, sendMessage, pictureId])

  useEffect(() => {
    if (open) {
      setScale(1); setRotation(0); setOnlineUsers([])
      setLoading(true); setLockedBy(null); setLockedNickname('')
      setHistory([]); setCropMode(false); setCropData(null)
      proxyUrlRef.current = proxyUrl
      pendingLockRef.current = true
      lockedByRef.current = null

      try {
        const token = getToken()
        if (token) {
          const payload = JSON.parse(atob(token.split('.')[1]))
          const userId = Number(payload.userId || payload.sub)
          myUserIdRef.current = userId
          setMyUserId(userId)
        }
      } catch {
        myUserIdRef.current = null
        setMyUserId(null)
      }
    }
  }, [open, proxyUrl])

  useEffect(() => {
    return () => {
      if (open && wasMyLockRef.current) {
        sendMsgRef.current?.({ type: 'unlock', pictureId })
        wasMyLockRef.current = false
      }
    }
  }, [open, pictureId])

  const scaleRef = useRef(scale)
  const rotationRef = useRef(rotation)
  const cropDataRef = useRef(cropData)
  useEffect(() => { scaleRef.current = scale; rotationRef.current = rotation; cropDataRef.current = cropData }, [scale, rotation, cropData])

  const emitTransform = useCallback((newScale, newRotation, crop) => {
    setHistory(prev => [...prev, { scale: scaleRef.current, rotation: rotationRef.current, cropData: cropDataRef.current }])
    const msg = { type: 'transform', pictureId, scale: newScale, rotation: newRotation }
    if (crop) msg.crop = crop
    sendMessage(msg)
  }, [sendMessage, pictureId])

  const handleUndo = useCallback(() => {
    setHistory(prev => {
      if (prev.length === 0) return prev
      const last = prev[prev.length - 1]
      setScale(last.scale); setRotation(last.rotation); setCropData(last.cropData)
      const msg = { type: 'transform', pictureId, scale: last.scale, rotation: last.rotation }
      if (last.cropData) msg.crop = last.cropData
      sendMessage(msg)
      return prev.slice(0, -1)
    })
  }, [sendMessage, pictureId])

  const handleZoomIn = () => { if (!isEditable || cropMode) return; const n = +(scale + 0.1).toFixed(1); setScale(n); emitTransform(n, rotation, cropData) }
  const handleZoomOut = () => { if (!isEditable || cropMode) return; const n = +(Math.max(0.1, scale - 0.1)).toFixed(1); setScale(n); emitTransform(n, rotation, cropData) }
  const handleRotateLeft = () => { if (!isEditable || cropMode) return; const n = rotation - 90; setRotation(n); emitTransform(scale, n, cropData) }
  const handleRotateRight = () => { if (!isEditable || cropMode) return; const n = rotation + 90; setRotation(n); emitTransform(scale, n, cropData) }

  const handleEnterCrop = () => { if (!isEditable) return; setCropMode(true) }

  const handleCropConfirm = () => {
    const cropper = cropperRef.current?.getCropper()
    if (!cropper) return
    const data = cropper.getData(true)
    const crop = { x: Math.round(data.x), y: Math.round(data.y), w: Math.round(data.width), h: Math.round(data.height) }
    setCropData(crop)
    setCropMode(false)
    emitTransform(scale, rotation, crop)
  }

  const handleCropCancel = () => { setCropMode(false) }

  const handleClearCrop = () => {
    setCropData(null)
    setCropMode(false)
    emitTransform(scale, rotation, null)
  }

  const handleReleaseLock = () => {
    sendMessage({ type: 'unlock', pictureId })
    wasMyLockRef.current = false
    setLockedBy(null); setLockedNickname('')
  }
  const handleAcquireLock = () => {
    pendingLockRef.current = true
    sendMessage({ type: 'lock', pictureId })
  }

  const handleSave = async () => {
    if (!isMyLockRef.current) {
      message.warning('只有当前编辑者可以保存')
      return
    }
    setSaving(true)
    let blobUrl = null
    try {
      // 通过 fetch + blob URL 加载图片，避免 canvas 跨域污染
      // 优先走代理，失败则用直链（需 COS 配置了 CORS）
      let imgSrc = imageUrl
      try {
        const res = await fetch(proxyUrlRef.current)
        if (res.ok) {
          const blob = await res.blob()
          blobUrl = URL.createObjectURL(blob)
          imgSrc = blobUrl
        }
      } catch {
        // 代理不可用，回退到直链
      }

      const img = new Image()
      img.crossOrigin = 'anonymous'
      await new Promise((resolve, reject) => { img.onload = resolve; img.onerror = reject; img.src = imgSrc })

      const radians = (rotation * Math.PI) / 180
      const cos = Math.abs(Math.cos(radians)), sin = Math.abs(Math.sin(radians))
      const rotW = Math.ceil(img.width * cos + img.height * sin)
      const rotH = Math.ceil(img.width * sin + img.height * cos)

      const canvasW = Math.ceil(rotW * scale)
      const canvasH = Math.ceil(rotH * scale)

      const canvas = document.createElement('canvas')
      canvas.width = canvasW; canvas.height = canvasH
      const ctx = canvas.getContext('2d')
      ctx.translate(canvasW / 2, canvasH / 2)
      ctx.rotate(radians); ctx.scale(scale, scale)
      ctx.drawImage(img, -img.width / 2, -img.height / 2)

      let finalCanvas
      if (cropData) {
        // 1. 在原图上裁剪
        const cropped = document.createElement('canvas')
        cropped.width = cropData.w; cropped.height = cropData.h
        const cctx = cropped.getContext('2d')
        cctx.drawImage(img, cropData.x, cropData.y, cropData.w, cropData.h, 0, 0, cropData.w, cropData.h)
        // 2. 对裁剪结果做旋转+缩放
        const radians = (rotation * Math.PI) / 180
        const cos = Math.abs(Math.cos(radians)), sin = Math.abs(Math.sin(radians))
        const rotW = Math.ceil(cropData.w * cos + cropData.h * sin)
        const rotH = Math.ceil(cropData.w * sin + cropData.h * cos)
        const canvas = document.createElement('canvas')
        canvas.width = Math.ceil(rotW * scale); canvas.height = Math.ceil(rotH * scale)
        const ctx = canvas.getContext('2d')
        ctx.translate(canvas.width / 2, canvas.height / 2)
        ctx.rotate(radians); ctx.scale(scale, scale)
        ctx.drawImage(cropped, -cropData.w / 2, -cropData.h / 2)
        finalCanvas = canvas
      } else {
        finalCanvas = canvas
      }

      const blob = await new Promise(r => finalCanvas.toBlob(r, 'image/webp'))
      if (!blob) { message.error('生成图片失败'); return }

      const file = new File([blob], 'collab-edit.webp', { type: 'image/webp' })
      const result = await replacePictureFile(file, pictureId, { collab: true })
      message.success('保存成功')
      onSuccess?.(result)
      onClose()
    } catch (e) {
      message.error(e?.message || '保存失败')
    } finally {
      if (blobUrl) URL.revokeObjectURL(blobUrl)
      setSaving(false)
    }
  }

  const renderStatusBar = () => {
    if (isMyLock) return <div className="collab-status-bar collab-status-editing">✏️ 你正在编辑</div>
    if (isOtherLock) return <div className="collab-status-bar collab-status-viewing">👁️ {lockedNickname} 正在编辑（仅查看）</div>
    return <div className="collab-status-bar collab-status-viewing">正在获取编辑权，当前仅查看</div>
  }

  const displayUsers = onlineUsers.slice(0, 5)
  const extraCount = onlineUsers.length - 5

  return (
    <Modal
        title="协同编辑"
        open={open}
        onCancel={onClose}
        width={720}
        destroyOnHidden
        footer={
          <div className="collab-footer">
            <div className="collab-users">
              {displayUsers.map(u => (
                <Tooltip title={u.nickname} key={u.userId}>
                  <Avatar size={28} src={u.avatar} icon={<UserOutlined />} />
                </Tooltip>
              ))}
              {extraCount > 0 && (
                <Avatar size={28} style={{ backgroundColor: '#f0f0f0', color: '#999', fontSize: 12 }}>+{extraCount}</Avatar>
              )}
              {onlineUsers.length > 0 && (
                <span className="collab-users-count">{onlineUsers.length} 人在线</span>
              )}
            </div>
            <div className="collab-actions">
              {isMyLock && (
                <Button icon={<UnlockOutlined />} onClick={handleReleaseLock}>释放编辑权</Button>
              )}
              {!lockedBy && (
                <Button type="primary" ghost icon={<LockOutlined />} onClick={handleAcquireLock}>获取编辑权</Button>
              )}
              <Button onClick={onClose}>取消</Button>
              {isEditable && (
                <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>保存</Button>
              )}
            </div>
          </div>
        }
      >
        {renderStatusBar()}
        <div className="collab-canvas-container" ref={containerRef}>
          {loading && !cropMode && (
            <div className="collab-loading"><Spin>加载图片中...</Spin></div>
          )}

          {cropMode ? (
            <CropperEditor ref={cropperRef} src={proxyUrl} />
          ) : (
            <div className="collab-image-viewport">
              <img
                src={proxyUrl}
                alt="协同编辑"
                className="collab-image"
                style={{
                  transform: `scale(${scale}) rotate(${rotation}deg)`,
                  clipPath: cropClipPath,
                }}
                onLoad={(event) => {
                  setLoading(false)
                  setImageNaturalSize({
                    width: event.currentTarget.naturalWidth,
                    height: event.currentTarget.naturalHeight,
                  })
                }}
                onError={() => { setLoading(false); message.error('图片加载失败') }}
                draggable={false}
              />
            </div>
          )}
        </div>

        <div className="collab-toolbar">
          {isEditable && (
            <div className="collab-toolbar-group">
              <Button icon={<UndoOutlined />} onClick={handleUndo} size="small" title="撤销" disabled={history.length === 0 || cropMode} />
            </div>
          )}
          <div className="collab-toolbar-group">
            <Button icon={<ZoomOutOutlined />} onClick={handleZoomOut} size="small" title="缩小" disabled={!isEditable || cropMode} />
            <span className="collab-scale-text">{Math.round(scale * 100)}%</span>
            <Button icon={<ZoomInOutlined />} onClick={handleZoomIn} size="small" title="放大" disabled={!isEditable || cropMode} />
          </div>
          <div className="collab-toolbar-group">
            <Button icon={<RotateLeftOutlined />} onClick={handleRotateLeft} size="small" title="左旋转" disabled={!isEditable || cropMode} />
            <span className="collab-rotation-text">{displayRotation}°</span>
            <Button icon={<RotateRightOutlined />} onClick={handleRotateRight} size="small" title="右旋转" disabled={!isEditable || cropMode} />
          </div>
          {isEditable && (
            <div className="collab-toolbar-group">
              {cropMode ? (
                <>
                  <Button icon={<CloseOutlined />} onClick={handleCropCancel} size="small">取消</Button>
                  <Button type="primary" icon={<CheckOutlined />} onClick={handleCropConfirm} size="small">确认</Button>
                </>
              ) : (
                <Button
                  icon={<ScissorOutlined />}
                  onClick={cropData ? handleClearCrop : handleEnterCrop}
                  size="small"
                  title={cropData ? '清除裁剪' : '裁剪'}
                  type={cropData ? 'primary' : 'default'}
                >
                  {cropData ? '清除裁剪' : '裁剪'}
                </Button>
              )}
            </div>
          )}
        </div>
    </Modal>
  )
}
