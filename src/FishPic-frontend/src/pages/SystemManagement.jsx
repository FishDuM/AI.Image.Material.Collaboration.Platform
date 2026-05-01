import { useState, useEffect, useCallback, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Tag, Input, Button, Space, Spin, Empty, Popconfirm } from 'antd'
import { PlusOutlined, ReloadOutlined, TagOutlined, PictureOutlined, DeleteOutlined } from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import api from '../api'
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
  const [marqueeIds, setMarqueeIds] = useState('')
  const [marqueeLoading, setMarqueeLoading] = useState(false)
  const [marqueeAdding, setMarqueeAdding] = useState(false)

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

  const handleAddMarquee = async () => {
    const raw = marqueeIds.trim()
    if (!raw) {
      antMessage.warning('请输入图片ID')
      return
    }
    const ids = raw.split(/[,，\s]+/).filter(Boolean)
    if (ids.length === 0) {
      antMessage.warning('请输入有效的图片ID')
      return
    }
    setMarqueeAdding(true)
    try {
      await api.post('/system/addMarquee', { pictureId: ids })
      antMessage.success(`成功添加 ${ids.length} 张跑马灯图片`)
      setMarqueeIds('')
      fetchMarquee()
    } catch (err) {
      antMessage.error(err.message || '添加跑马灯图片失败')
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
                      ['magenta', 'red', 'volcano', 'orange', 'gold', 'lime', 'green', 'cyan', 'blue', 'geekblue', 'purple'][
                        index % 11
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
            <span>跑马灯图片管理</span>
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
          <Input
            placeholder="输入图片ID（多个用逗号分隔，如：1,2,3）"
            value={marqueeIds}
            onChange={(e) => setMarqueeIds(e.target.value)}
            onPressEnter={handleAddMarquee}
            disabled={marqueeAdding}
            style={{ maxWidth: 420 }}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddMarquee}
            loading={marqueeAdding}
          >
            添加跑马灯图片
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
    </main>
  )
}

export default SystemManagement
