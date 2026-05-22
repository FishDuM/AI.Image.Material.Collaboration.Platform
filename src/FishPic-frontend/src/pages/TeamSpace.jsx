import { useState, useEffect, useCallback, useContext, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Typography, Button, Empty, Modal, Form, Input, Spin, Avatar, Progress, Tooltip, Tag } from 'antd'
import { TeamOutlined, EditOutlined, PlusOutlined, PictureOutlined, CloudServerOutlined, UserOutlined } from '@ant-design/icons'
import { createSpace, updateSpace, listSpace } from '../api'
import { ThemeContext } from '../context/ThemeContext'
import { useFetchWithCleanup } from '../hooks/useRequestUtils'
import { LEVEL_MAP } from '../utils/constants'
import './TeamSpace.css'

const { Title, Text } = Typography

const formatSize = (bytes) => {
  if (!bytes || bytes <= 0) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function TeamSpace() {
  const { message } = AntApp.useApp()
  const { isDarkMode } = useContext(ThemeContext)
  const navigate = useNavigate()
  const [spaces, setSpaces] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [showEdit, setShowEdit] = useState(false)
  const [editTarget, setEditTarget] = useState(null)
  const [createLoading, setCreateLoading] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()

  const { createSignal } = useFetchWithCleanup()

  const fetchSpaces = useCallback(async () => {
    setLoading(true)
    try {
      const signal = createSignal()
      const result = await listSpace(1, { signal })
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
      setShowCreate(list.length === 0)
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      setSpaces([])
      setShowCreate(true)
    } finally {
      setLoading(false)
    }
  }, [createSignal])

  useEffect(() => {
    fetchSpaces()
  }, [fetchSpaces])

  const handleCreate = async (values) => {
    setCreateLoading(true)
    try {
      await createSpace({
        name: values.name,
        type: 1,
        introduction: values.introduction || '',
      })
      message.success('团队空间创建成功')
      form.resetFields()
      fetchSpaces()
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '创建失败')
    } finally {
      setCreateLoading(false)
    }
  }

  const handleEditOpen = (space) => {
    setEditTarget(space)
    editForm.setFieldsValue({ name: space.name, introduction: space.introduction || '' })
    setShowEdit(true)
  }

  const listRef = useRef(null)

  useEffect(() => {
    const container = listRef.current
    if (!container) return
    const handler = (e) => {
      const item = e.target.closest('.ts-list-item')
      if (!item || item.scrollWidth <= item.clientWidth) return
      e.preventDefault()
      item.scrollLeft += e.deltaY
    }
    container.addEventListener('wheel', handler, { passive: false })
    return () => container.removeEventListener('wheel', handler)
  }, [])

  const handleUpdate = async (values) => {
    setUpdateLoading(true)
    try {
      await updateSpace({
        id: editTarget.id,
        name: values.name,
        introduction: values.introduction || '',
      })
      message.success('修改成功')
      setShowEdit(false)
      setEditTarget(null)
      editForm.resetFields()
      fetchSpaces()
    } catch (error) {
      if (error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED') return
      message.error(error.message || '修改失败')
    } finally {
      setUpdateLoading(false)
    }
  }

  const renderSpaceRow = (space) => {
    const sizeNum = Number(space.size) || 0
    const storageNum = Number(space.storageSize) || 0
    const usedPercent = storageNum > 0
      ? Math.min((sizeNum / storageNum) * 100, 100).toFixed(1)
      : 0
    const levelInfo = LEVEL_MAP[space.level] || LEVEL_MAP[0]
    const members = space.teamMembers || []

    return (
      <div key={space.id} className="ts-list-item" onClick={() => navigate(`/team-space/${space.id}`)}>
        <div className="ts-list-inner">
          <div className="ts-list-left">
            <div className="ts-list-name-row">
              <Text strong className="ts-list-name">{space.name}</Text>
              <Tag color={levelInfo.color} variant="filled" className="ts-level-tag">
                {levelInfo.label}
              </Tag>
            </div>
            {space.introduction && (
              <Text type="secondary" className="ts-list-intro" ellipsis={{ tooltip: space.introduction }}>
                {space.introduction}
              </Text>
            )}
          </div>

          <div className="ts-list-center">
            <div className="ts-list-stat">
              <PictureOutlined className="ts-list-stat-icon" />
              <Text type="secondary" className="ts-list-stat-label">图片</Text>
              <Text strong className="ts-list-stat-value">{space.pictureCount ?? 0}</Text>
            </div>

            <div className="ts-list-divider" />

            <div className="ts-list-stat">
              <CloudServerOutlined className="ts-list-stat-icon" />
              <Text type="secondary" className="ts-list-stat-label">存储</Text>
              <Text type="secondary" className="ts-list-stat-value">
                {formatSize(space.size || 0)} / {formatSize(space.storageSize || 0)}
              </Text>
            </div>

            <div className="ts-list-divider" />

            <div className="ts-list-stat">
              <UserOutlined className="ts-list-stat-icon" />
              <Text type="secondary" className="ts-list-stat-label">创建人</Text>
              <Avatar size={18} src={space.userAvatar} icon={<UserOutlined />} />
              <Text className="ts-list-stat-value">{space.userName || '未知'}</Text>
            </div>

            {members.length > 0 && (
              <>
                <div className="ts-list-divider" />
                <div className="ts-list-stat">
                  <TeamOutlined className="ts-list-stat-icon" />
                  <Text type="secondary" className="ts-list-stat-label">成员</Text>
                  <Avatar.Group
                    max={{ count: 10, style: { backgroundColor: isDarkMode ? '#434343' : '#f0f0f0', color: isDarkMode ? 'rgba(255,255,255,0.65)' : '#999' } }}
                    size={22}
                  >
                    {members.map((m) => (
                      <Tooltip title={m.nickname || '成员'} key={m.id}>
                        <Avatar size={22} src={m.avatar} icon={<UserOutlined />} />
                      </Tooltip>
                    ))}
                  </Avatar.Group>
                </div>
              </>
            )}
          </div>

          <div className="ts-list-right">
            <div className="ts-list-storage-bar">
              <Progress
                percent={Number(usedPercent)}
                strokeColor={Number(usedPercent) > 80 ? '#D70015' : '#3A3A3A'}
                showInfo={false}
                size="small"
              />
            </div>
            <Tooltip title="编辑空间">
              <Button type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); handleEditOpen(space) }} />
            </Tooltip>
          </div>
        </div>
      </div>
    )
  }

  return (
    <main className="team-space-container">
      <div className="team-space-header">
        <div className="team-space-header-left">
          <div className="ts-title-row">
            <Title level={3} style={{ margin: 0 }}>团队空间列表</Title>
            {spaces.length > 0 && (
              <Tag color="green" variant="filled">{spaces.length} 个空间</Tag>
            )}
          </div>
          <Text type="secondary">管理和协作你的团队图片资源</Text>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setShowCreate(true)}
        >
          创建空间
        </Button>
      </div>

      <Spin spinning={loading}>
        {!loading && spaces.length > 0 && (
          <div className="ts-list" ref={listRef}>
            <div className="ts-list-header">
              <div className="ts-list-header-left">空间信息</div>
              <div className="ts-list-header-center">详情</div>
              <div className="ts-list-header-right">存储 / 操作</div>
            </div>
            {spaces.map(renderSpaceRow)}
          </div>
        )}
        {!loading && spaces.length === 0 && (
          <div className="empty-state-wrapper">
            <Empty description="暂无团队空间，创建一个吧">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowCreate(true)}>
                创建团队空间
              </Button>
            </Empty>
          </div>
        )}
      </Spin>

      <Modal
        title="编辑空间"
        open={showEdit}
        onCancel={() => { setShowEdit(false); setEditTarget(null); editForm.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowEdit(false); setEditTarget(null); editForm.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => editForm.submit()} loading={updateLoading}>
              保存
            </Button>
          </div>
        }
        closable={false}
      >
        <Form form={editForm} layout="vertical" onFinish={handleUpdate} style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="空间名称"
            rules={[{ required: true, message: '请输入空间名称' }, { max: 20, message: '不超过20个字符' }]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item name="introduction" label="空间介绍">
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="创建团队空间"
        open={showCreate}
        onCancel={() => { setShowCreate(false); form.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowCreate(false); form.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => form.submit()} loading={createLoading}>
              创建
            </Button>
          </div>
        }
        destroyOnHidden
        closable={false}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate} style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="团队名称"
            rules={[{ required: true, message: '请输入团队名称' }, { max: 20, message: '不超过20个字符' }]}
          >
            <Input placeholder="请输入团队名称" maxLength={20} />
          </Form.Item>
          <Form.Item name="introduction" label="空间介绍">
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </main>
  )
}

export default TeamSpace