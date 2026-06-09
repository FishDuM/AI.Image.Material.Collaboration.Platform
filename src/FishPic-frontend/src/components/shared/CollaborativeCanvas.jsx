import { useState, useRef, useCallback, useEffect, useMemo } from 'react'
import { Modal, Button, Spin, App, Avatar, Tooltip, Badge, Select } from 'antd'
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

/**
 * 协同编辑画布组件
 * 支持：缩放/旋转实时同步、裁剪、编辑互斥锁、操作历史撤销
 */
export default function CollaborativeCanvas({ open, imageUrl, pictureId, spaceId, onSuccess, onClose }) {
  const { message } = App.useApp()
  const [scale, setScale] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true)
  const [onlineUsers, setOnlineUsers] = useState([])

  // 编辑权状态
  const [lockedBy, setLockedBy] = useState(null)
  const [lockedNickname, setLockedNickname] = useState('')
  const [editRequests, setEditRequests] = useState([])
  const [hasRequested, setHasRequested] = useState(false)
  const [showRequestModal, setShowRequestModal] = useState(false)
  const [selectedRequester, setSelectedRequester] = useState(null)

  // 裁剪状态
  const [cropMode, setCropMode] = useState(false)
  const [cropData, setCropData] = useState(null) // { x, y, w, h } 原图像素坐标

  // 操作历史栈
  const [history, setHistory] = useState([])

  const proxyUrlRef = useRef('')
  const containerRef = useRef(null)
  const myUserIdRef = useRef(null)
  const cropperRef = useRef(null)
  const pendingLockRef = useRef(false)

  const isMyLock = lockedBy != null && lockedBy == myUserIdRef.current
  const isOtherLock = lockedBy != null && lockedBy != myUserIdRef.current
  const isEditable = lockedBy === null || isMyLock

  const displayRotation = ((rotation % 360) + 360) % 360

  const proxyUrl = useMemo(() => {
    if (!imageUrl) return ''
    return imageUrl.replace(/^https?:\/\/[^/]+/, '/cos-proxy')
  }, [imageUrl])

  // 裁剪 clip-path（原图坐标 → 百分比）
  const cropClipPath = useMemo(() => {
    if (!cropData) return 'none'
    const imgEl = containerRef.current?.querySelector('.collab-image')
    if (!imgEl || !imgEl.naturalWidth) return 'none'
    const nw = imgEl.naturalWidth, nh = imgEl.naturalHeight
    const top = (cropData.y / nh * 100)
    const left = (cropData.x / nw * 100)
    const bottom = ((nh - cropData.y - cropData.h) / nh * 100)
    const right = ((nw - cropData.x - cropData.w) / nw * 100)
    return `inset(${top}% ${right}% ${bottom}% ${left}%)`
  }, [cropData])

  // ---- WebSocket 消息处理 ----
  const handleMessage = useCallback((data) => {
    switch (data.type) {
      case 'transform':
        if (data.pictureId == pictureId) {
          setScale(data.scale)
          setRotation(data.rotation)
          setCropData(data.crop || null)
        }
        break
      case 'lock':
        if (data.pictureId == pictureId) {
          setLockedBy(data.userId)
          setLockedNickname(data.nickname || '')
          if (myUserIdRef.current && data.userId != myUserIdRef.current) {
            message.info(`${data.nickname || '用户'} 开始编辑`)
          }
        }
        break
      case 'lock-denied':
        if (data.pictureId == pictureId) {
          setLockedBy(data.userId)
          setLockedNickname(data.nickname || '')
          message.warning(`${data.nickname || '用户'} 正在编辑，请申请编辑权`)
        }
        break
      case 'lock-transfer':
        if (data.pictureId == pictureId) {
          setLockedBy(data.toUserId)
          setLockedNickname(data.toNickname || '')
          setEditRequests(prev => prev.filter(r => r.userId != data.toUserId))
          if (data.toUserId == myUserIdRef.current) {
            message.success('你已获得编辑权')
            setHasRequested(false)
          } else if (data.fromUserId == myUserIdRef.current) {
            message.info(`编辑权已转移给 ${data.toNickname || '用户'}`)
          } else {
            message.info(`编辑权已转移给 ${data.toNickname || '用户'}`)
          }
        }
        break
      case 'unlock':
        if (data.pictureId == pictureId) {
          setLockedBy(null); setLockedNickname('')
          setEditRequests([]); setHasRequested(false)
        }
        break
      case 'request-edit':
        if (data.pictureId == pictureId && isMyLock) {
          setEditRequests(prev => {
            if (prev.some(r => r.userId == data.userId)) return prev
            return [...prev, { userId: data.userId, nickname: data.nickname, avatar: data.avatar }]
          })
          message.info(`${data.nickname || '用户'} 申请编辑权`)
          setShowRequestModal(true)
        }
        break
      case 'edit-denied':
        if (data.pictureId == pictureId) {
          setHasRequested(false)
          message.warning('编辑申请被拒绝')
        }
        break
      case 'presence':
        setOnlineUsers(data.users || [])
        if (data.users) {
          const me = data.users.find(u => u.userId == myUserIdRef.current)
          if (me) myNicknameRef.current = me.nickname || ''
        }
        break
      case 'join':
        setOnlineUsers(prev => {
          if (prev.some(u => u.userId === data.userId)) return prev
          return [...prev, data]
        })
        break
      case 'leave':
        setOnlineUsers(prev => prev.filter(u => u.userId !== data.userId))
        setEditRequests(prev => prev.filter(r => r.userId != data.userId))
        break
      default: break
    }
  }, [pictureId, message, isMyLock])

  const myNicknameRef = useRef('')
  const sendMsgRef = useRef(null)

  // WebSocket 就绪时发送 lock
  const handleWsReady = useCallback(() => {
    if (pendingLockRef.current) {
      pendingLockRef.current = false
      sendMsgRef.current?.({ type: 'lock', pictureId })
    }
  }, [pictureId])

  const { sendMessage } = useCollabWebSocket(
    open ? spaceId : null,
    handleMessage,
    () => { setOnlineUsers([]); setEditRequests([]) },
    handleWsReady
  )
  sendMsgRef.current = sendMessage

  // 打开时重置状态
  useEffect(() => {
    if (open) {
      setScale(1); setRotation(0); setOnlineUsers([])
      setLoading(true); setLockedBy(null); setLockedNickname('')
      setEditRequests([]); setHasRequested(false); setShowRequestModal(false)
      setHistory([]); setCropMode(false); setCropData(null)
      proxyUrlRef.current = proxyUrl
      pendingLockRef.current = true

      try {
        const token = getToken()
        if (token) {
          const payload = JSON.parse(atob(token.split('.')[1]))
          myUserIdRef.current = Number(payload.userId || payload.sub)
        }
      } catch {}

      const timer = setTimeout(() => {
        if (pendingLockRef.current) {
          pendingLockRef.current = false
          sendMessage({ type: 'lock', pictureId })
        }
      }, 500)
      return () => clearTimeout(timer)
    }
  }, [open, proxyUrl]) // eslint-disable-line react-hooks/exhaustive-deps

  // 关闭时释放锁
  useEffect(() => {
    return () => {
      if (open) sendMessage({ type: 'unlock', pictureId })
    }
  }, [open]) // eslint-disable-line react-hooks/exhaustive-deps

  // ---- 操作 ----

  const emitTransform = useCallback((newScale, newRotation, crop) => {
    setHistory(prev => [...prev, { scale, rotation, cropData }])
    const msg = { type: 'transform', pictureId, scale: newScale, rotation: newRotation }
    if (crop) msg.crop = crop
    sendMessage(msg)
  }, [sendMessage, pictureId, scale, rotation, cropData]) // eslint-disable-line react-hooks/exhaustive-deps

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

  // 裁剪模式
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

  // 编辑权申请
  const handleRequestEdit = () => {
    sendMessage({ type: 'request-edit', pictureId })
    setHasRequested(true)
    message.info('已发送编辑申请，等待审批')
  }
  const handleApprove = () => {
    if (!selectedRequester) return
    sendMessage({ type: 'approve', pictureId, targetUserId: selectedRequester })
    setSelectedRequester(null); setShowRequestModal(false)
  }
  const handleDeny = () => {
    if (!selectedRequester) return
    sendMessage({ type: 'deny', pictureId, targetUserId: selectedRequester })
    setSelectedRequester(null)
    if (editRequests.length <= 1) setShowRequestModal(false)
  }
  const handleReleaseLock = () => {
    sendMessage({ type: 'unlock', pictureId })
    setLockedBy(null); setLockedNickname('')
  }

  // ---- 保存 ----
  const handleSave = async () => {
    setSaving(true)
    try {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      await new Promise((resolve, reject) => { img.onload = resolve; img.onerror = reject; img.src = proxyUrlRef.current })

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

      // 裁剪：从变换后的 canvas 提取裁剪区域
      let finalCanvas = canvas
      if (cropData) {
        const sx = Math.round(cropData.x * scale)
        const sy = Math.round(cropData.y * scale)
        const sw = Math.round(cropData.w * scale)
        const sh = Math.round(cropData.h * scale)
        const cropped = document.createElement('canvas')
        cropped.width = sw; cropped.height = sh
        const cctx = cropped.getContext('2d')
        cctx.drawImage(canvas, sx, sy, sw, sh, 0, 0, sw, sh)
        finalCanvas = cropped
      }

      const blob = await new Promise(r => finalCanvas.toBlob(r, 'image/webp'))
      if (!blob) { message.error('生成图片失败'); return }

      const file = new File([blob], 'collab-edit.webp', { type: 'image/webp' })
      await replacePictureFile(file, pictureId)
      message.success('保存成功')
      onSuccess?.()
      onClose()
    } catch (e) {
      message.error(e?.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  // ---- 渲染 ----
  const renderStatusBar = () => {
    if (isMyLock) return <div className="collab-status-bar collab-status-editing">✏️ 你正在编辑</div>
    if (isOtherLock) return <div className="collab-status-bar collab-status-viewing">👁️ {lockedNickname} 正在编辑（仅查看）</div>
    return null
  }

  const displayUsers = onlineUsers.slice(0, 5)
  const extraCount = onlineUsers.length - 5

  return (
    <>
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
              {isOtherLock && !hasRequested && (
                <Button type="primary" ghost icon={<LockOutlined />} onClick={handleRequestEdit}>申请编辑</Button>
              )}
              {isOtherLock && hasRequested && (
                <Button disabled>已申请，等待审批...</Button>
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
            <div className="collab-loading"><Spin description="加载图片中..." /></div>
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
                onLoad={() => setLoading(false)}
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
          {isMyLock && editRequests.length > 0 && !cropMode && (
            <div className="collab-toolbar-group">
              <Badge count={editRequests.length} size="small">
                <Button icon={<LockOutlined />} size="small" onClick={() => setShowRequestModal(true)}>审批</Button>
              </Badge>
            </div>
          )}
        </div>
      </Modal>

      {/* 编辑申请审批弹窗 */}
      <Modal
        title="编辑权申请"
        open={showRequestModal}
        onCancel={() => { setShowRequestModal(false); setSelectedRequester(null) }}
        width={400}
        destroyOnHidden
        footer={null}
      >
        {editRequests.length === 0 ? (
          <p style={{ color: '#999', textAlign: 'center' }}>暂无申请</p>
        ) : (
          <div>
            <p>以下用户申请编辑权：</p>
            <Select
              placeholder="选择申请人"
              style={{ width: '100%', marginBottom: 16 }}
              value={selectedRequester}
              onChange={setSelectedRequester}
              options={editRequests.map(r => ({
                value: r.userId,
                label: (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Avatar size={20} src={r.avatar} icon={<UserOutlined />} />
                    <span>{r.nickname || `用户${r.userId}`}</span>
                  </div>
                )
              }))}
            />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button danger onClick={handleDeny} disabled={!selectedRequester}>拒绝</Button>
              <Button type="primary" onClick={handleApprove} disabled={!selectedRequester}>同意</Button>
            </div>
          </div>
        )}
      </Modal>
    </>
  )
}
