import { useState, useEffect, useCallback, useRef } from 'react'
import { App, Modal, Button, Tabs, Pagination, Spin, Empty } from 'antd'
import { CheckOutlined } from '@ant-design/icons'
import { listSpace, postPictureList } from '../../api'

function SpacePickerModal({ open, onClose, onConfirm, currentImageCount, existingImageIds = [] }) {
  const { message: msg } = App.useApp()
  const [activeTab, setActiveTab] = useState('private')
  const [spaceId, setSpaceId] = useState(null)
  const [images, setImages] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const prevIdsRef = useRef('')
  const prevPageRef = useRef(1)

  useEffect(() => {
    if (open) {
      setActiveTab('private')
      setSelectedIds([])
      setPage(1)
      setSpaceId(null)
      setImages([])
      setTotal(0)
      prevIdsRef.current = undefined
      prevPageRef.current = undefined
    } else {
      setSpaceId(null)
      setImages([])
      setTotal(0)
    }
  }, [open])

  useEffect(() => {
    const loadSpaceId = async () => {
      try {
        const result = await listSpace(0)
        const list = Array.isArray(result) ? result : []
        if (list.length > 0 && list[0].id) {
          setSpaceId(list[0].id)
        } else {
          setSpaceId(null)
        }
      } catch {
        setSpaceId(null)
      }
    }
    if (open && activeTab === 'private') loadSpaceId()
  }, [open, activeTab])

  const fetchImages = useCallback(async (p, sid, ids) => {
    if (!sid) return
    setLoading(true)
    try {
      const result = await postPictureList({ spaceId: sid, pictureIds: ids, current: p, pageSize: 20 })
      const list = Array.isArray(result) ? result : []
      setImages(list)
      setTotal(list.length)
    } catch {
      setImages([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!spaceId || activeTab !== 'private' || !open) return
    const idsKey = (existingImageIds || []).join(',')
    if (idsKey === prevIdsRef.current && page === prevPageRef.current) return
    prevIdsRef.current = idsKey
    prevPageRef.current = page
    fetchImages(page, spaceId, existingImageIds)
  }, [spaceId, page, activeTab, open, existingImageIds, fetchImages])

  const toggleImage = useCallback((img) => {
    if (selectedIds.includes(img.id)) {
      setSelectedIds(prev => prev.filter(id => id !== img.id))
      return
    }
    if (img.flag === false) {
      msg.warning('该图片已添加，请勿重复选择')
      return
    }
    if (currentImageCount + selectedIds.length >= 15) {
      msg.warning(`最多只能选择15张图片（已选择${currentImageCount}张）`)
      return
    }
    setSelectedIds(prev => [...prev, img.id])
  }, [currentImageCount, selectedIds, msg])

  const handleConfirm = useCallback(() => {
    if (selectedIds.length === 0) {
      msg.warning('请先选择图片')
      return
    }
    const imageMap = new Map(images.map(img => [img.id, img]))
    const selected = selectedIds.map(id => imageMap.get(id)).filter(Boolean)
    onConfirm(selected)
    setSelectedIds([])
    setPage(1)
    onClose()
  }, [selectedIds, images, onConfirm, msg, onClose])

  const handleTabChange = (key) => {
    setActiveTab(key)
    setSelectedIds([])
    setPage(1)
  }

  return (
    <Modal
      open={open}
      onCancel={() => { setSelectedIds([]); setPage(1); onClose() }}
      title="从空间中获取"
      width={640}
      className="space-picker-modal"
      footer={[
        <Button key="cancel" onClick={() => { setSelectedIds([]); setPage(1); onClose() }}>
          取消
        </Button>,
        <Button
          key="confirm"
          type="primary"
          icon={<CheckOutlined />}
          disabled={selectedIds.length === 0}
          onClick={handleConfirm}
        >
          确认选择 ({selectedIds.length})
        </Button>,
      ]}
    >
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        className="space-picker-tabs"
        items={[
          {
            key: 'private',
            label: '私人空间',
            children: (
              <div className="space-picker-tab-content">
                {!spaceId ? (
                  <Empty description="暂无私人空间" style={{ padding: '60px 0' }} />
                ) : (
                  <>
                    <Spin spinning={loading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                      <div className="space-image-grid space-picker-grid">
                        {images.map((img) => {
                          const orderIndex = selectedIds.indexOf(img.id)
                          const isSelected = orderIndex !== -1
                          const isInCarousel = img.flag === false
                          return (
                            <div
                              key={img.id}
                              className={`space-image-item ${isSelected ? 'space-image-selected' : ''} ${isInCarousel ? 'space-image-in-carousel' : ''}`}
                              onClick={() => toggleImage(img)}
                            >
                              <img src={img.url} alt="" className="space-image-thumb" />
                              <div className="space-image-check">
                                {isSelected ? (
                                  <span className="space-order-badge">{orderIndex + 1}</span>
                                ) : isInCarousel ? (
                                  <span className="space-order-exists">已有</span>
                                ) : (
                                  <span className="space-order-empty" />
                                )}
                              </div>
                            </div>
                          )
                        })}
                      </div>
                      {images.length === 0 && !loading && (
                        <Empty description="暂无图片" style={{ padding: '40px 0' }} />
                      )}
                    </Spin>
                    <div className="space-image-footer space-picker-footer">
                      <Pagination
                        current={page}
                        total={total}
                        pageSize={20}
                        size="small"
                        showSizeChanger={false}
                        showTotal={(t) => `共 ${t} 张`}
                        onChange={(p) => setPage(p)}
                      />
                      <span className="space-picker-limit-hint">
                        {currentImageCount > 0 ? `已选 ${currentImageCount} 张，还可选 ${Math.max(0, 15 - currentImageCount)} 张` : `最多可选 15 张`}
                      </span>
                    </div>
                  </>
                )}
              </div>
            ),
          },
          {
            key: 'team',
            label: '团队空间',
            children: (
              <div className="space-picker-tab-content">
                <Empty description="团队空间功能开发中..." style={{ padding: '60px 0' }} />
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}

export default SpacePickerModal
