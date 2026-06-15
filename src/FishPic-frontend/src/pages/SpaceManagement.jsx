import { useEffect, useState, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Button, Modal, Form, Input, InputNumber, Select, Switch, Popconfirm, Space, Tag, Typography, Card } from 'antd'
import { EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons'
import { adminListSpace, adminUpdateSpace, adminDeleteSpace, adminSetSpaceStatus } from '../api'
import { AuthContext } from '../context/AuthContext'
import './SpaceManagement.css'

const { Title } = Typography

const TYPE_MAP = { 0: '私人空间', 1: '团队空间' }
const TYPE_COLOR = { 0: 'blue', 1: 'green' }
const LEVEL_MAP = { 0: '普通', 1: 'VIP', 2: 'SVIP' }
const LEVEL_COLOR = { 0: 'default', 1: 'gold', 2: 'red' }

function SpaceManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo, authLoading } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [searchName, setSearchName] = useState('')
  const [searchNameApplied, setSearchNameApplied] = useState('')
  const [searchType, setSearchType] = useState(undefined)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingRecord, setEditingRecord] = useState(null)
  const [editForm] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (authLoading) return
    if (!userInfo || !userInfo?.permissions?.includes('system:team:manage')) {
      message.error('无权访问，正在跳转...')
      setTimeout(() => navigate('/404', { replace: true }), 500)
    }
  }, [userInfo, authLoading, navigate, message])

  useEffect(() => {
    if (!userInfo?.permissions?.includes('system:team:manage')) return
    let ignore = false
    const fetchData = async () => {
      setLoading(true)
      try {
        const params = { current, pageSize }
        if (searchNameApplied) params.name = searchNameApplied
        if (searchType !== undefined && searchType !== null) params.type = searchType
        const result = await adminListSpace(params)
        if (!ignore) {
          setData(result?.records || [])
          setTotal(result?.total || 0)
        }
      } catch (err) {
        if (!ignore) message.error(err.message || '获取空间列表失败')
      } finally {
        if (!ignore) setLoading(false)
      }
    }
    fetchData()
    return () => { ignore = true }
  }, [current, pageSize, searchNameApplied, searchType, userInfo, message, refreshKey])

  const handleSearch = () => {
    setSearchNameApplied(searchName)
    setCurrent(1)
  }

  const handleReset = () => {
    setSearchName('')
    setSearchNameApplied('')
    setSearchType(undefined)
    setCurrent(1)
  }

  const handleTableChange = (pagination) => {
    setCurrent(pagination.current)
    setPageSize(pagination.pageSize)
  }

  const handleEdit = (record) => {
    setEditingRecord(record)
    editForm.setFieldsValue({
      name: record.name,
      introduction: record.introduction,
      storageSize: record.storageSize,
      level: record.level,
    })
    setEditModalOpen(true)
  }

  const handleEditOk = async () => {
    try {
      const values = await editForm.validateFields()
      setSubmitting(true)
      await adminUpdateSpace({ id: editingRecord.id, ...values })
      message.success('修改成功')
      setEditModalOpen(false)
      setRefreshKey(k => k + 1)
    } catch (err) {
      if (err !== 'cancelled') message.error(err.message || '修改失败')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (id) => {
    try {
      await adminDeleteSpace(id)
      message.success('删除成功')
      setRefreshKey(k => k + 1)
    } catch (err) {
      message.error(err.message || '删除失败')
    }
  }

  const handleStatusChange = async (id, checked) => {
    try {
      await adminSetSpaceStatus(id, checked ? 1 : 0)
      message.success(checked ? '已启用' : '已禁用')
      setRefreshKey(k => k + 1)
    } catch (err) {
      message.error(err.message || '操作失败')
    }
  }

  const formatStorageSize = (bytes) => {
    if (!bytes) return '-'
    if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB'
    if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB'
    if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return bytes + ' B'
  }

  if (!userInfo || !userInfo?.permissions?.includes('system:team:manage')) {
    return (
      <main className="space-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '空间名称', dataIndex: 'name', key: 'name', ellipsis: true },
    {
      title: '类型', dataIndex: 'type', key: 'type', width: 110,
      render: (t) => <Tag color={TYPE_COLOR[t]}>{TYPE_MAP[t] || t}</Tag>,
    },
    { title: '创建者', dataIndex: 'userName', key: 'userName', width: 120 },
    {
      title: '存储用量', key: 'storage', width: 140,
      render: (_, r) => `${formatStorageSize(r.size)} / ${formatStorageSize(r.storageSize)}`,
    },
    {
      title: '等级', dataIndex: 'level', key: 'level', width: 80,
      render: (l) => <Tag color={LEVEL_COLOR[l]}>{LEVEL_MAP[l] || l}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (s, record) => (
        <Switch
          size="small"
          checked={s !== 0}
          onChange={(checked) => handleStatusChange(record.id, checked)}
        />
      ),
    },
    {
      title: '操作', key: 'actions', width: 150,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定删除该空间？"
            description="删除后不可恢复"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <main className="space-management-container">
      <div className="space-management-header">
        <Title level={2}>空间管理</Title>
        <p className="header-subtitle">管理系统所有空间配置和资源</p>
      </div>

      <Card variant="borderless" className="space-content-card">
        <div className="space-filter-bar">
          <Space wrap>
            <Input
              placeholder="搜索空间名称"
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              onPressEnter={handleSearch}
              style={{ width: 200 }}
              allowClear
            />
            <Select
              placeholder="空间类型"
              value={searchType}
              onChange={(v) => setSearchType(v)}
              allowClear
              style={{ width: 140 }}
              options={[
                { label: '私人空间', value: 0 },
                { label: '团队空间', value: 1 },
              ]}
            />
            <Button type="primary" onClick={handleSearch}>搜索</Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
          </Space>
        </div>

        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          onChange={handleTableChange}
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
          scroll={{ x: 900 }}
        />
      </Card>

      <Modal
        title="编辑空间"
        open={editModalOpen}
        onOk={handleEditOk}
        onCancel={() => setEditModalOpen(false)}
        confirmLoading={submitting}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="name" label="空间名称" rules={[{ required: true, message: '请输入空间名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="introduction" label="空间介绍">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="storageSize" label="存储空间大小(Byte)">
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="level" label="空间等级">
            <Select
              options={[
                { label: '普通', value: 0 },
                { label: 'VIP', value: 1 },
                { label: 'SVIP', value: 2 },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </main>
  )
}

export default SpaceManagement
