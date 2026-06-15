import { useState, useEffect, useCallback, useRef } from 'react'
import { App, Modal, Button, Tabs, Pagination, Spin, Empty } from 'antd'
import { CheckOutlined, LeftOutlined, TeamOutlined } from '@ant-design/icons'
import { listSpace, spaceListPicture } from '../../api'

function SpacePickerModal({ open, onClose, onConfirm, currentImageCount, existingImageIds = [] }) {
  const { message: msg } = App.useApp()
  const [activeTab, setActiveTab] = useState('private')
  const [spaceId, setSpaceId] = useState(null)
  const [images, setImages] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [selectedItems, setSelectedItems] = useState([])
  const selectedIds = selectedItems.map(it => it.id)
  const prevIdsRef = useRef('')
  const prevPageRef = useRef(1)
  const [teamSpaces, setTeamSpaces] = useState([])
  const [teamSpacesLoading, setTeamSpacesLoading] = useState(false)
  const [teamSpaceId, setTeamSpaceId] = useState(null)
  const [teamSpaceImages, setTeamSpaceImages] = useState([])
  const [teamSpaceImageTotal, setTeamSpaceImageTotal] = useState(0)
  const [teamSpaceImagePage, setTeamSpaceImagePage] = useState(1)
  const [teamSpaceImageLoading, setTeamSpaceImageLoading] = useState(false)
  const [teamSpaceView, setTeamSpaceView] = useState('list')
  // 标记当前请求是否已过期，防止快速切换团队空间时旧请求覆盖新数据
  const fetchSeqRef = useRef(0)

  useEffect(() => {
    if (open) {
      setActiveTab('private')
      setSelectedItems([])
      setPage(1)
      setSpaceId(null)
      setImages([])
      setTotal(0)
      prevIdsRef.current = undefined
      prevPageRef.current = undefined
      setTeamSpaces([])
      setTeamSpacesLoading(false)
      setTeamSpaceId(null)
      setTeamSpaceImages([])
      setTeamSpaceImageTotal(0)
      setTeamSpaceImagePage(1)
      setTeamSpaceView('list')
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
      const result = await spaceListPicture({ spaceId: sid, pictureIds: ids, current: p, pageSize: 20 })
      const list = (result?.records ?? []).map(img => ({
        ...img,
        flag: (ids || []).includes(img.id) ? false : true
      }))
      setImages(list)
      setTotal(result?.total ?? list.length)
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
    if (selectedItems.find(it => it.id === img.id)) {
      setSelectedItems(prev => prev.filter(it => it.id !== img.id))
      return
    }
    if (img.flag === false) {
      msg.warning('该图片已添加，请勿重复选择')
      return
    }
    if (currentImageCount + selectedItems.length >= 15) {
      msg.warning(`最多只能选择15张图片（已选择${currentImageCount}张）`)
      return
    }
    setSelectedItems(prev => [...prev, img])
  }, [currentImageCount, selectedItems, msg])

  const handleConfirm = useCallback(() => {
    if (selectedItems.length === 0) {
      msg.warning('请先选择图片')
      return
    }
    onConfirm(selectedItems)
    setSelectedItems([])
    setPage(1)
    onClose()
  }, [selectedItems, onConfirm, msg, onClose])

  const fetchTeamSpaces = useCallback(async () => {
    setTeamSpacesLoading(true)
    try {
      const result = await listSpace(1)
      const list = Array.isArray(result) ? result : []
      setTeamSpaces(list)
      if (list.length > 0) {
        setTeamSpaceId(list[0].id)
      }
    } catch {
      setTeamSpaces([])
    } finally {
      setTeamSpacesLoading(false)
    }
  }, [])

  const fetchTeamSpaceImages = useCallback(async (p, ids) => {
    if (!teamSpaceId) return
    const seq = ++fetchSeqRef.current
    setTeamSpaceImageLoading(true)
    try {
      const result = await spaceListPicture({ spaceId: teamSpaceId, pictureIds: ids, current: p, pageSize: 20 })
      // 快速切换团队空间时，旧请求可能晚于新请求返回，检查序号避免覆盖新数据
      if (seq !== fetchSeqRef.current) return
      const list = (result?.records ?? []).map(img => ({
        ...img,
        flag: (ids || []).includes(img.id) ? false : true
      }))
      setTeamSpaceImages(list)
      setTeamSpaceImageTotal(result?.total ?? list.length)
    } catch {
      if (seq !== fetchSeqRef.current) return
      setTeamSpaceImages([])
    } finally {
      if (seq === fetchSeqRef.current) setTeamSpaceImageLoading(false)
    }
  }, [teamSpaceId])

  useEffect(() => {
    if (teamSpaceId && teamSpaceView === 'images') {
      fetchTeamSpaceImages(teamSpaceImagePage, existingImageIds)
    }
  }, [teamSpaceId, teamSpaceImagePage, teamSpaceView, existingImageIds, fetchTeamSpaceImages])

  useEffect(() => {
    if (open && activeTab === 'team') fetchTeamSpaces()
  }, [open, activeTab, fetchTeamSpaces])

  const handleTeamSpaceSelect = useCallback((id) => {
    setTeamSpaceId(id)
    setTeamSpaceImagePage(1)
    setSelectedItems([])
    setTeamSpaceView('images')
  }, [])

  const handleTeamSpaceBack = useCallback(() => {
    setTeamSpaceView('list')
    setTeamSpaceId(null)
    setTeamSpaceImages([])
    setTeamSpaceImageTotal(0)
    setTeamSpaceImagePage(1)
    setSelectedItems([])
  }, [])

  const handleTeamSpaceImageToggle = useCallback((img) => {
    if (selectedItems.find(it => it.id === img.id)) {
      setSelectedItems(prev => prev.filter(it => it.id !== img.id))
      return
    }
    if (img.flag === false) {
      msg.warning('该图片已添加，请勿重复选择')
      return
    }
    if (currentImageCount + selectedItems.length >= 15) {
      msg.warning(`最多只能选择15张图片（已选择${currentImageCount}张）`)
      return
    }
    setSelectedItems(prev => [...prev, img])
  }, [currentImageCount, selectedItems, msg])

  const handleTeamSpaceConfirm = useCallback(() => {
    if (selectedItems.length === 0) {
      msg.warning('请先选择图片')
      return
    }
    onConfirm(selectedItems)
    setSelectedItems([])
    setTeamSpaceImagePage(1)
    onClose()
  }, [selectedItems, onConfirm, msg, onClose])

  const handleTabChange = (key) => {
    setActiveTab(key)
    setSelectedItems([])
    setPage(1)
    if (key === 'team') {
      setTeamSpaceView('list')
      setTeamSpaceId(null)
      setTeamSpaceImages([])
      setTeamSpaceImageTotal(0)
      setTeamSpaceImagePage(1)
    }
  }

  return (
    <Modal
      open={open}
      onCancel={() => { setSelectedItems([]); setPage(1); onClose() }}
      title="从空间中获取"
      className="space-picker-modal"
      zIndex={1050}
      footer={[
        <Button key="cancel" onClick={() => { setSelectedItems([]); setPage(1); onClose() }}>
          取消
        </Button>,
        <Button
          key="confirm"
          type="primary"
          icon={<CheckOutlined />}
          disabled={selectedIds.length === 0}
          onClick={activeTab === 'team' ? handleTeamSpaceConfirm : handleConfirm}
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
                    <Spin spinning={loading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
                      <div className="space-image-grid">
                        {images.map((img) => {
                          const orderIndex = selectedIds.indexOf(img.id)
                          const isSelected = orderIndex !== -1
                          const isInCarousel = img.flag === false
                          return (
                            <div
                              key={img.id}
                              className={`space-image-item${isSelected ? ' space-image-selected' : ''}${isInCarousel ? ' space-image-in-carousel' : ''}`}
                              onClick={() => toggleImage(img)}
                            >
                              <img src={img.url} alt={img.pictureName || '图片'} className="space-image-thumb" />
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
                        {currentImageCount > 0 || selectedIds.length > 0 ? `已选 ${currentImageCount + selectedIds.length} 张，还可选 ${Math.max(0, 15 - currentImageCount - selectedIds.length)} 张` : `最多可选 15 张`}
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
                <Spin spinning={teamSpacesLoading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
                  {teamSpaces.length === 0 ? (
                    <Empty description="暂未加入团队空间" style={{ padding: '60px 0' }} />
                  ) : teamSpaceView === 'list' ? (
                    <div className="team-space-list-view">
                      {teamSpaces.map((sp) => (
                        <div
                          key={sp.id}
                          className="team-space-list-card"
                          onClick={() => handleTeamSpaceSelect(sp.id)}
                        >
                          <TeamOutlined className="team-space-card-icon" />
                          <div className="team-space-card-info">
                            <span className="team-space-card-name">{sp.name}</span>
                            {sp.introduction && (
                              <span className="team-space-card-intro" title={sp.introduction}>{sp.introduction}</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <>
                      <div className="team-space-image-header">
                        <Button
                          type="link"
                          icon={<LeftOutlined />}
                          onClick={handleTeamSpaceBack}
                          className="team-space-back-btn"
                        >
                          返回空间列表
                        </Button>
                        <span className="team-space-image-title">
                          {teamSpaces.find(s => s.id === teamSpaceId)?.name || '团队空间'}
                        </span>
                      </div>
                      {!teamSpaceId ? (
                        <Empty description="请选择一个团队空间" style={{ padding: '60px 0' }} />
                      ) : (
                        <>
                          <Spin spinning={teamSpaceImageLoading} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden' }}>
                            <div className="space-image-grid">
                              {teamSpaceImages.map((img) => {
                                const orderIndex = selectedIds.indexOf(img.id)
                                const isSelected = orderIndex !== -1
                                const isInCarousel = img.flag === false
                                return (
                                  <div
                                    key={img.id}
                                    className={`space-image-item${isSelected ? ' space-image-selected' : ''}${isInCarousel ? ' space-image-in-carousel' : ''}`}
                                    onClick={() => handleTeamSpaceImageToggle(img)}
                                  >
                                    <img src={img.url} alt={img.pictureName || '图片'} className="space-image-thumb" />
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
                          </Spin>
                          {teamSpaceImages.length === 0 && !teamSpaceImageLoading && (
                            <Empty description="暂无图片" style={{ padding: '40px 0' }} />
                          )}
                          <div className="space-image-footer space-picker-footer">
                            <Pagination
                              current={teamSpaceImagePage}
                              total={teamSpaceImageTotal}
                              pageSize={20}
                              size="small"
                              showSizeChanger={false}
                              showTotal={(t) => `共 ${t} 张`}
                              onChange={(p) => setTeamSpaceImagePage(p)}
                            />
                            <span className="space-picker-limit-hint">
                              {currentImageCount > 0 || selectedIds.length > 0 ? `已选 ${currentImageCount + selectedIds.length} 张，还可选 ${Math.max(0, 15 - currentImageCount - selectedIds.length)} 张` : `最多可选 15 张`}
                            </span>
                          </div>
                        </>
                      )}
                    </>
                  )}
                </Spin>
              </div>
            ),
          },
        ]}
      />
    </Modal>
  )
}

export default SpacePickerModal
