import { useState, useEffect, useCallback, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Tag, Input, Button, Space, Spin, Empty, Popconfirm, Modal, Image, Checkbox, Pagination } from 'antd'
import { PlusOutlined, ReloadOutlined, TagOutlined, PictureOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import api, { getAdminPictureList } from '../api'
import { PAGINATION_LOCALE } from '../utils/constants'
import './SystemManagement.css'

const { Title } = Typography

function SystemManagement() {
  const { message: antMessage } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [typeList, setTypeList] = useState([])
  const [newTag, setNewTag] = useState('')
  const [adding, setAdding] = useState(false)
  const [marqueeUrls, setMarqueeUrls] = useState([])
  const [marqueeLoading, setMarqueeLoading] = useState(false)
  const [marqueeAdding, setMarqueeAdding] = useState(false)
  const [pickerVisible, setPickerVisible] = useState(false)
  const [pickerPictures, setPickerPictures] = useState([])
  const [pickerLoading, setPickerLoading] = useState(false)
  const [pickerPagination, setPickerPagination] = useState({ current: 1, total: 0 })
  const [pickerSelectedIds, setPickerSelectedIds] = useState([])

  useEffect(() => {
    if (!userInfo || userInfo.role !== 'admin') {
      antMessage.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
      return
    }
  }, [navigate, userInfo, antMessage])

  const fetchTypeList = useCallback(async () => {
    setLoading(true)
    try {
      const result = await api.get('/system/list')
      if (Array.isArray(result)) {
        setTypeList(result)
      }
    } catch (err) {
      antMessage.error(err.message || '获取标签列表失败')
    } finally {
      setLoading(false)
    }
  }, [antMessage])

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (userInfo && userInfo.role === 'admin') {
      fetchTypeList()
    }
  }, [userInfo, fetchTypeList])
  /* eslint-enable react-hooks/set-state-in-effect */

  const fetchMarquee = useCallback(async () => {
    setMarqueeLoading(true)
    try {
      const result = await api.get('/system/marquee')
      if (Array.isArray(result)) {
        setMarqueeUrls(result)
      }
    } catch {
      setMarqueeUrls([])
    } finally {
      setMarqueeLoading(false)
    }
  }, [])

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (userInfo && userInfo.role === 'admin') {
      fetchMarquee()
    }
  }, [userInfo, fetchMarquee])
  /* eslint-enable react-hooks/set-state-in-effect */

  const handleOpenPicker = () => {
    setPickerSelectedIds([])
    setPickerVisible(true)
    fetchFeaturedPictures(1)
  }

  const fetchFeaturedPictures = async (page) => {
    setPickerLoading(true)
    try {
      const result = await getAdminPictureList(page, 20, 4)
      const { records, total } = result
      setPickerPictures(records || [])
      setPickerPagination({ current: page, total: total || 0 })
    } catch (err) {
      antMessage.error(err.message || '获取精选图片失败')
    } finally {
      setPickerLoading(false)
    }
  }

  const handlePickerPageChange = (page) => {
    fetchFeaturedPictures(page)
  }

  const handlePickerToggle = (id) => {
    setPickerSelectedIds(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    )
  }

  const handlePickerSubmit = async () => {
    if (pickerSelectedIds.length === 0) {
      antMessage.warning('请先选择图片')
      return
    }
    setMarqueeAdding(true)
    try {
      await api.post('/system/addMarquee', { pictureId: pickerSelectedIds })
      antMessage.success(`成功添加 ${pickerSelectedIds.length} 张轮播图图片`)
      setPickerVisible(false)
      fetchMarquee()
    } catch (err) {
      antMessage.error(err.message || '添加轮播图图片失败')
    } finally {
      setMarqueeAdding(false)
    }
  }

  const handleAddTag = async () => {
    const tag = newTag.trim()
    if (!tag) {
      antMessage.warning('请输入标签名称')
      return
    }
    if (typeList.includes(tag)) {
      antMessage.warning('该标签已存在')
      return
    }
    setAdding(true)
    try {
      await api.post('/system/addList', { value: [tag] })
      antMessage.success(`标签「${tag}」添加成功`)
      setNewTag('')
      fetchTypeList()
    } catch (err) {
      antMessage.error(err.message || '添加标签失败')
    } finally {
      setAdding(false)
    }
  }

  const handleDeleteTag = async (tag) => {
    try {
      await api.post('/system/deleteType', { value: tag })
      antMessage.success(`标签「${tag}」已删除`)
      fetchTypeList()
    } catch (err) {
      antMessage.error(err.message || '删除标签失败')
    }
  }

  const handleDeleteMarquee = async (url) => {
    try {
      await api.post('/system/deleteMarquee', { url })
      antMessage.success('跑马灯图片已删除')
      fetchMarquee()
    } catch (err) {
      antMessage.error(err.message || '删除跑马灯图片失败')
    }
  }

  if (!userInfo || userInfo.role !== 'admin') {
    return (
      <main className="system-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  return (
    <main className="system-management-container">
      <div className="system-management-header">
        <Title level={2}>系统管理</Title>
        <p className="header-subtitle">管理系统配置和基础设置</p>
      </div>

      <Card
        className="system-section-card"
        title={
          <Space>
            <TagOutlined />
            <span>帖子标签管理</span>
          </Space>
        }
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchTypeList}
            loading={loading}
          >
            刷新
          </Button>
        }
        variant="borderless"
      >
        <div className="tag-add-area">
          <Input
            placeholder="输入新标签名称"
            value={newTag}
            onChange={(e) => setNewTag(e.target.value)}
            onPressEnter={handleAddTag}
            disabled={adding}
            style={{ maxWidth: 320 }}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddTag}
            loading={adding}
          >
            添加标签
          </Button>
        </div>

        <div className="tag-list-area">
          {loading ? (
            <div className="tag-loading">
              <Spin size="large" />
            </div>
          ) : typeList.length === 0 ? (
            <Empty description="暂无标签，请添加" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <div className="tag-grid">
              {typeList.map((tag, index) => (
                <div key={`${tag}-${index}`} className="system-tag-wrapper">
                  <Tag
                    className="system-tag"
                    icon={<TagOutlined />}
                    color={
                      ['magenta', 'orange', 'volcano', 'gold', 'lime', 'green', 'cyan', 'geekblue', 'purple', 'default'][
                        index % 10
                      ]
                    }
                  >
                    {tag}
                  </Tag>
                  <Popconfirm
                    title="确认删除该标签？"
                    description={`删除后标签「${tag}」将被移除`}
                    onConfirm={() => handleDeleteTag(tag)}
                    okText="确认"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      className="system-tag-delete-btn"
                    />
                  </Popconfirm>
                </div>
              ))}
            </div>
          )}
        </div>
      </Card>

      <Card
        className="system-section-card"
        title={
          <Space>
            <PictureOutlined />
            <span>轮播图管理</span>
          </Space>
        }
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchMarquee}
            loading={marqueeLoading}
          >
            刷新
          </Button>
        }
        variant="borderless"
      >
        <div className="tag-add-area">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenPicker}
            loading={marqueeAdding}
          >
            添加轮播图图片
          </Button>
        </div>

        <div className="marquee-list-area">
          {marqueeLoading ? (
            <div className="tag-loading">
              <Spin size="large" />
            </div>
          ) : marqueeUrls.length === 0 ? (
            <Empty description="暂无跑马灯图片，请添加" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <div className="marquee-grid">
              {marqueeUrls.map((url, index) => (
                <div key={`${url}-${index}`} className="marquee-item">
                  <img
                    src={url}
                    alt={`跑马灯 ${index + 1}`}
                    className="marquee-thumb"
                  />
                  <div className="marquee-index">{index + 1}</div>
                  <Popconfirm
                    title="确认删除该图片？"
                    description="删除后该图片将从跑马灯中移除"
                    onConfirm={() => handleDeleteMarquee(url)}
                    okText="确认"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      type="primary"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      className="marquee-delete-btn"
                    />
                  </Popconfirm>
                </div>
              ))}
            </div>
          )}
        </div>
      </Card>

      <Modal
        title="选择精选图片"
        open={pickerVisible}
        onCancel={() => setPickerVisible(false)}
        width={800}
        footer={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: 'var(--text-tertiary)' }}>
              已选 {pickerSelectedIds.length} 张
            </span>
            <Space>
              <Button onClick={() => setPickerVisible(false)}>取消</Button>
              <Button
                type="primary"
                onClick={handlePickerSubmit}
                loading={marqueeAdding}
                disabled={pickerSelectedIds.length === 0}
              >
                确认添加
              </Button>
            </Space>
          </div>
        }
      >
        <Spin spinning={pickerLoading}>
          {pickerPictures.length === 0 ? (
            <Empty description="暂无精选图片" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, padding: '8px 0' }}>
                {pickerPictures.map((pic) => (
                  <div
                    key={pic.id}
                    onClick={() => handlePickerToggle(pic.id)}
                    style={{
                      position: 'relative',
                      cursor: 'pointer',
                      borderRadius: 8,
                      overflow: 'hidden',
                      border: pickerSelectedIds.includes(pic.id)
                        ? '2px solid var(--accent)'
                        : '2px solid var(--border-secondary, #f0f0f0)',
                      transition: 'border-color 0.2s',
                    }}
                  >
                    <Image
                      src={pic.url}
                      alt={`图片 ${pic.id}`}
                      width="100%"
                      height={140}
                      style={{ objectFit: 'cover', display: 'block' }}
                      preview={{ cover: <EyeOutlined /> }}
                      fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTQwIiBoZWlnaHQ9IjE0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTQwIiBoZWlnaHQ9IjE0MCIgZmlsbD0iIzIxMjEyMSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iYXJpYWwiIGZvbnQtc2l6ZT0iMTIiIGZpbGw9IiM2YjZiNmIiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5Mb2FkaW5nLi4uPC90ZXh0Pjwvc3ZnPg=="
                    />
                    <Checkbox
                      checked={pickerSelectedIds.includes(pic.id)}
                      style={{
                        position: 'absolute',
                        top: 8,
                        left: 8,
                      }}
                      onClick={(e) => e.stopPropagation()}
                      onChange={() => handlePickerToggle(pic.id)}
                    />
                    <div style={{
                      position: 'absolute',
                      bottom: 0,
                      left: 0,
                      right: 0,
                      padding: '4px 6px',
                      background: 'linear-gradient(transparent, rgba(0,0,0,0.6))',
                      color: '#fff',
                      fontSize: 12,
                      textAlign: 'center',
                    }}>
                      ID: {pic.id}
                    </div>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                <Pagination
                  current={pickerPagination.current}
                  total={pickerPagination.total}
                  pageSize={20}
                  onChange={handlePickerPageChange}
                  showSizeChanger={false}
                  showQuickJumper
                  showTotal={(total) => `共 ${total} 条`}
                  locale={PAGINATION_LOCALE}
                />
              </div>
            </>
          )}
        </Spin>
      </Modal>
    </main>
  )
}

export default SystemManagement
