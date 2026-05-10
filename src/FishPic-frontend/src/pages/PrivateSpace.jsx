import { useState, useEffect, useCallback, useMemo } from 'react'
import { App as AntApp, Typography, Button, Modal, Form, Input, Pagination, Masonry, Image as AntImage, Spin, Empty, Popconfirm, Progress, Popover } from 'antd'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, ArrowUpOutlined, CrownOutlined, CloudServerOutlined, CheckCircleFilled } from '@ant-design/icons'
import { updateSpace, listSpace, spaceListPicture, deletePicture } from '../api'
import './PrivateSpace.css'

const { Title } = Typography

const PAGE_SIZE = 20

const PAGINATION_LOCALE = {
  items_per_page: '条/页',
  jump_to: '跳至',
  jump_to_confirm: '确定',
  page: '页',
  prev_page: '上一页',
  next_page: '下一页',
  prev_5: '向前 5 页',
  next_5: '向后 5 页',
  prev_3: '向前 3 页',
  next_3: '向后 3 页',
  page_size: '页码',
}

const storageStrokeColor = {
  '0%': '#108ee9',
  '100%': '#87d068',
}

const formatStorage = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const val = bytes / Math.pow(1024, i)
  return `${val.toFixed(i > 2 ? 1 : (i > 1 ? 2 : 0))} ${units[i]}`
}

const LEVEL_MAP = {
  0: { label: '普通', className: 'level-normal', cardClass: 'storage-card-normal' },
  1: { label: 'VIP', className: 'level-vip', cardClass: 'storage-card-vip' },
  2: { label: 'SVIP', className: 'level-svip', cardClass: 'storage-card-svip' },
}

const UPGRADE_PLANS = [
  {
    key: 'vip',
    name: 'VIP',
    price: '¥9.9',
    period: '/月',
    level: 1,
    features: ['5 GB 专属存储空间', '支持上传单张 5 MB 图片', '优先审核通过', '专属客服通道'],
  },
  {
    key: 'svip',
    name: 'SVIP',
    price: '¥19.9',
    period: '/月',
    level: 2,
    hot: true,
    features: ['10 GB 专属存储空间', '支持上传单张 5 MB 图片', '极速审核通过', '专属客服通道', '优先体验新功能'],
  },
]

const ADDON_PLANS = [
  { key: 'addon-1g', name: '+1 GB', size: '1 GB', price: '¥1.0', period: '/月' },
  { key: 'addon-5g', name: '+5 GB', size: '5 GB', price: '¥3.9', period: '/月' },
  { key: 'addon-10g', name: '+10 GB', size: '10 GB', price: '¥6.9', period: '/月' },
]

function PrivateSpace() {
  const { message } = AntApp.useApp()
  const [spaces, setSpaces] = useState([])
  const [showEdit, setShowEdit] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)
  const [editForm] = Form.useForm()

  const [pictures, setPictures] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureTotal, setPictureTotal] = useState(0)
  const [pictureLoading, setPictureLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')

  const [batchMode, setBatchMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState([])
  const [showUpgrade, setShowUpgrade] = useState(false)
  const [selectedPlan, setSelectedPlan] = useState(null)

  const fetchSpaces = useCallback(async () => {
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
    } catch {
      setSpaces([])
    }
  }, [])

  const fetchPictures = useCallback(async (spaceId, page, keyword) => {
    setPictureLoading(true)
    setPicturePage(page)
    try {
      const params = {
        spaceId,
        current: page,
        pageSize: PAGE_SIZE,
      }
      if (keyword && keyword.trim()) {
        params.keyword = keyword.trim()
      }
      const result = await spaceListPicture(params)
      const list = Array.isArray(result?.records) ? result.records : []
      const total = typeof result?.total === 'number' ? result.total : list.length
      setPictures(list)
      setPictureTotal(total)
    } catch {
      setPictures([])
    } finally {
      setPictureLoading(false)
    }
  }, [])

  useEffect(() => {
    const init = async () => { await fetchSpaces() }
    init()
  }, [fetchSpaces])

  useEffect(() => {
    const load = async () => {
      if (spaces.length > 0 && spaces[0].id) {
        await fetchPictures(spaces[0].id, 1)
      }
    }
    load()
  }, [spaces, fetchPictures])

  const spaceInfo = useMemo(() => {
    if (!spaces.length) return null
    const s = spaces[0]
    const sizeBytes = parseFloat(s.size) || 0
    const storageBytes = parseFloat(s.storageSize) || 0
    const percent = storageBytes > 0 ? Math.min(100, Math.round((sizeBytes / storageBytes) * 100)) : 0
    return { ...s, percent, usedText: formatStorage(sizeBytes), totalText: formatStorage(storageBytes) }
  }, [spaces])

  const handlePageChange = useCallback((page) => {
    if (spaces.length > 0 && spaces[0].id) {
      fetchPictures(spaces[0].id, page, searchKeyword)
    }
  }, [spaces, fetchPictures, searchKeyword])

  const handleSearch = useCallback(() => {
    if (spaces.length > 0 && spaces[0].id) {
      fetchPictures(spaces[0].id, 1, searchKeyword)
    }
  }, [spaces, fetchPictures, searchKeyword])

  const handleSearchReset = useCallback(() => {
    setSearchKeyword('')
    if (spaces.length > 0 && spaces[0].id) {
      fetchPictures(spaces[0].id, 1, '')
    }
  }, [spaces, fetchPictures])

  const handleDeletePicture = useCallback(async (pictureId) => {
    try {
      const res = await deletePicture([pictureId])
      message.success(res?.message || '删除成功')
      if (spaces.length > 0 && spaces[0].id) {
        await fetchPictures(spaces[0].id, picturePage, searchKeyword)
      }
    } catch (error) {
      message.error(error.message || '删除失败')
    }
  }, [spaces, fetchPictures, picturePage, searchKeyword, message])

  const toggleBatchMode = useCallback(() => {
    setBatchMode((prev) => {
      if (prev) setSelectedIds([])
      return !prev
    })
  }, [])

  const toggleSelect = useCallback((pictureId) => {
    setSelectedIds((prev) =>
      prev.includes(pictureId) ? prev.filter((id) => id !== pictureId) : [...prev, pictureId]
    )
  }, [])

  const handleBatchDelete = useCallback(async () => {
    if (selectedIds.length === 0) {
      message.warning('请先选择要删除的图片')
      return
    }
    try {
      const res = await deletePicture(selectedIds)
      message.success(res?.message || '删除成功')
      setSelectedIds([])
      setBatchMode(false)
      if (spaces.length > 0 && spaces[0].id) {
        await fetchPictures(spaces[0].id, picturePage, searchKeyword)
      }
    } catch (error) {
      message.error(error.message || '批量删除失败')
    }
  }, [selectedIds, spaces, fetchPictures, picturePage, searchKeyword, message])

  const masonryItems = useMemo(() => pictureListToMasonry(pictures), [pictures])

  const handleEditOpen = () => {
    if (spaces.length > 0) {
      editForm.setFieldsValue({ name: spaces[0].name, introduction: spaces[0].introduction })
      setShowEdit(true)
    }
  }

  const handleUpdate = async (values) => {
    setUpdateLoading(true)
    try {
      await updateSpace({
        id: spaces[0].id,
        name: values.name,
        introduction: values.introduction || '',
      })
      message.success('修改成功')
      setShowEdit(false)
      editForm.resetFields()
      fetchSpaces()
    } catch (error) {
      message.error(error.message || '修改失败')
    } finally {
      setUpdateLoading(false)
    }
  }

  return (
    <main className="private-space-container">
      <div className="private-space-header">
        <div className="private-space-header-left">
          <Title level={2}>
            私人空间{spaces.length > 0 && ` - ${spaces[0].name}`}
          </Title>
          <p className="header-subtitle">
            {spaces.length > 0 && spaces[0].introduction ? spaces[0].introduction : '你的专属私密存储空间'}
          </p>
        </div>
        {spaceInfo && (
          <div className="private-space-header-right">
            <Popover
              content={
                <div className={`storage-card ${LEVEL_MAP[spaceInfo.level]?.cardClass || ''}`}>
                  <div className="storage-card-title">空间详情</div>
                  <div className="storage-card-row">
                    <span className="storage-card-label">空间等级</span>
                    <span className={`storage-card-value ${LEVEL_MAP[spaceInfo.level]?.className || ''}`}>{LEVEL_MAP[spaceInfo.level]?.label || '-'}</span>
                  </div>
                  <div className="storage-card-row">
                    <span className="storage-card-label">占用比例</span>
                    <span className="storage-card-value">{spaceInfo.percent}%</span>
                  </div>
                  <div className="storage-card-row">
                    <span className="storage-card-label">已占用空间</span>
                    <span className="storage-card-value">{spaceInfo.usedText}</span>
                  </div>
                  <div className="storage-card-row">
                    <span className="storage-card-label">总空间</span>
                    <span className="storage-card-value">{spaceInfo.totalText}</span>
                  </div>
                </div>
              }
              trigger="hover"
              placement="bottom"
            >
              <Progress
                type="circle"
                percent={spaceInfo.percent}
                strokeColor={storageStrokeColor}
                size={72}
                className="storage-progress"
                format={() => (
                  <div className="level-center">
                    {LEVEL_MAP[spaceInfo.level] && (
                      <span className={`level-text ${LEVEL_MAP[spaceInfo.level].className}`}>{LEVEL_MAP[spaceInfo.level].label}</span>
                    )}
                    <span className="level-percent">{spaceInfo.percent}%</span>
                  </div>
                )}
              />
            </Popover>
            <Button onClick={handleEditOpen}>
              修改空间
            </Button>
          </div>
        )}
      </div>

      {spaces.length === 0 && (
        <Empty description="暂无私人空间" style={{ marginTop: 80 }} />
      )}

      {spaces.length > 0 && (
        <div className="private-space-search-bar">
          <Input
            className="private-space-search-input"
            placeholder="搜索图片..."
            prefix={<SearchOutlined />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            搜索
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleSearchReset}>
            重置
          </Button>
          <Button
            icon={<DeleteOutlined />}
            onClick={toggleBatchMode}
            type={batchMode ? 'primary' : 'default'}
            danger={batchMode}
          >
            {batchMode ? '退出批量' : '批量选择'}
          </Button>
          <Button icon={<ArrowUpOutlined />} className="private-space-upgrade-btn" onClick={() => setShowUpgrade(true)}>
            升级空间
          </Button>
        </div>
      )}

      {spaces.length > 0 && (
        <div className="private-space-masonry-section">
          {pictureLoading && (
            <div className="private-space-loading">
              <Spin />
            </div>
          )}
          {!pictureLoading && masonryItems.length === 0 && (
            <Empty description="暂无图片" style={{ marginTop: 60 }} />
          )}
          {!pictureLoading && masonryItems.length > 0 && (
            <Masonry
              columns={{ xs: 2, sm: 3, md: 4, lg: 5 }}
              gutter={[12, 12]}
              fresh
              items={masonryItems}
              itemRender={(item) => {
                const isSelected = selectedIds.includes(item.data.id)
                return (
                  <div
                    className={`private-space-masonry-item ${batchMode ? 'batch-mode' : ''}`}
                    onClick={batchMode ? () => toggleSelect(item.data.id) : undefined}
                  >
                    <AntImage
                      src={item.data.url}
                      alt=""
                      preview={false}
                      className="private-space-masonry-image"
                    />
                    {batchMode ? (
                      <div className="private-space-masonry-select">
                        <div className={`private-space-masonry-checkbox ${isSelected ? 'checked' : ''}`}>
                          {isSelected && <CheckOutlined />}
                        </div>
                      </div>
                    ) : (
                      <div className="private-space-masonry-actions">
                        <Popconfirm
                          title="确认删除"
                          description="确定要删除这张图片吗？"
                          onConfirm={() => handleDeletePicture(item.data.id)}
                          okText="删除"
                          cancelText="取消"
                          okButtonProps={{ danger: true }}
                        >
                          <Button
                            type="primary"
                            danger
                            size="small"
                            icon={<DeleteOutlined />}
                          >
                            删除
                          </Button>
                        </Popconfirm>
                      </div>
                    )}
                  </div>
                )
              }}
            />
          )}
          {batchMode && (
            <div className="private-space-batch-bar">
              <span className="private-space-batch-count">
                已选择 <strong>{selectedIds.length}</strong> 张图片
              </span>
              <div className="private-space-batch-actions">
                <Button
                  icon={<CloseOutlined />}
                  onClick={toggleBatchMode}
                >
                  取消
                </Button>
                <Popconfirm
                  title="确认删除"
                  description={`确定要删除选中的 ${selectedIds.length} 张图片吗？`}
                  onConfirm={handleBatchDelete}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true, disabled: selectedIds.length === 0 }}
                >
                  <Button
                    type="primary"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={selectedIds.length === 0}
                  >
                    删除选中
                  </Button>
                </Popconfirm>
              </div>
            </div>
          )}
          {pictureTotal > PAGE_SIZE && (
            <div className="private-space-pagination">
              <Pagination
                current={picturePage}
                total={pictureTotal}
                pageSize={PAGE_SIZE}
                onChange={handlePageChange}
                showSizeChanger={false}
                showQuickJumper
                locale={PAGINATION_LOCALE}
              />
            </div>
          )}
        </div>
      )}

      <Modal
        title="修改空间"
        open={showEdit}
        onCancel={() => { setShowEdit(false); editForm.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowEdit(false); editForm.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => editForm.submit()} loading={updateLoading}>
              保存
            </Button>
          </div>
        }
        closable={false}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="空间名称"
            rules={[
              { required: true, message: '请输入空间名称' },
              { max: 20, message: '空间名称不超过 20 个字符' },
            ]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="introduction"
            label="空间介绍"
          >
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>

      {showUpgrade && (
      <div className="upgrade-overlay" onClick={() => { setShowUpgrade(false); setSelectedPlan(null) }}>
      <div className="upgrade-content" onClick={(e) => e.stopPropagation()}>
          <div className="upgrade-header">
            <h2 className="upgrade-title">升级空间</h2>
            <p className="upgrade-subtitle">解锁更多存储，享受专属特权</p>
          </div>

          <div className="upgrade-section">
            <div className="upgrade-section-title">
              <CrownOutlined className="upgrade-section-icon" />
              <span>会员套餐</span>
            </div>
            <div className="upgrade-plan-grid">
              {UPGRADE_PLANS.map((plan) => (
                <div
                  key={plan.key}
                  className={`upgrade-plan-card ${plan.hot ? 'upgrade-plan-hot' : ''} ${selectedPlan === plan.key ? 'upgrade-plan-selected' : ''} level-${plan.key}`}
                  onClick={() => setSelectedPlan(plan.key)}
                >
                  {plan.hot && <div className="upgrade-hot-badge">推荐</div>}
                  <div className="upgrade-plan-name">{plan.name}</div>
                  <div className="upgrade-plan-price">
                    <span className="upgrade-price-amount">{plan.price}</span>
                    <span className="upgrade-price-period">{plan.period}</span>
                  </div>
                  <div className="upgrade-plan-features">
                    {plan.features.map((f, i) => (
                      <div key={i} className="upgrade-feature-item">
                        <CheckCircleFilled className="upgrade-feature-check" />
                        <span>{f}</span>
                      </div>
                    ))}
                  </div>
                  <Button
                    type={selectedPlan === plan.key ? 'primary' : 'default'}
                    block
                    className="upgrade-plan-btn"
                  >
                    {selectedPlan === plan.key ? '已选择' : '选择套餐'}
                  </Button>
                </div>
              ))}
            </div>
          </div>

          <div className="upgrade-section">
            <div className="upgrade-section-title">
              <CloudServerOutlined className="upgrade-section-icon" />
              <span>空间增量包</span>
            </div>
            <div className="upgrade-addon-grid">
              {ADDON_PLANS.map((addon) => (
                <div
                  key={addon.key}
                  className={`upgrade-addon-card ${selectedPlan === addon.key ? 'upgrade-addon-selected' : ''}`}
                  onClick={() => setSelectedPlan(addon.key)}
                >
                  <div className="upgrade-addon-name">{addon.name}</div>
                  <div className="upgrade-addon-size">{addon.size}</div>
                  <div className="upgrade-addon-price">
                    <span className="upgrade-price-amount">{addon.price}</span>
                    <span className="upgrade-price-period">{addon.period}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="upgrade-footer">
            <Button size="large" onClick={() => { setShowUpgrade(false); setSelectedPlan(null) }}>
              取消
            </Button>
            <Button
              type="primary"
              size="large"
              disabled={!selectedPlan}
              onClick={() => {
                message.success('升级申请已提交，等待审核')
                setShowUpgrade(false)
                setSelectedPlan(null)
              }}
            >
              确认升级
            </Button>
          </div>
        </div>
      </div>
      )}
    </main>
  )
}

function pictureListToMasonry(pictures) {
  return pictures.map((pic) => ({
    key: `pic-${pic.id}`,
    data: pic,
  }))
}

export default PrivateSpace
