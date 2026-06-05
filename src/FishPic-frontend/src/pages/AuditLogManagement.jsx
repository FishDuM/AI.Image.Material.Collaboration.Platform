import { useEffect, useState, useCallback, useContext, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Button, Input, Select, Space, Tag, Typography, Card } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import api from '../api'
import './AuditLogManagement.css'

const { Title } = Typography

const RESULT_MAP = { 0: '失败', 1: '成功' }
const RESULT_COLOR = { 0: 'red', 1: 'green' }

const OPERATION_OPTIONS = [
  { label: '全部', value: '' },
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '用户禁用', value: 'USER_DISABLE' },
  { label: '角色变更', value: 'ROLE_CHANGE' },
  { label: '图片审核', value: 'PICTURE_REVIEW' },
  { label: '帖子审核', value: 'POST_REVIEW' },
  { label: '帖子删除', value: 'POST_DELETE' },
  { label: '评论审核', value: 'COMMENT_REVIEW' },
  { label: '评论删除', value: 'COMMENT_DELETE' },
  { label: '空间更新', value: 'SPACE_UPDATE' },
  { label: '空间删除', value: 'SPACE_DELETE' },
  { label: '空间状态变更', value: 'SPACE_STATUS' },
]

function AuditLogManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const hasFetchedRef = useRef(false)
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [searchUsername, setSearchUsername] = useState('')
  const [filterOperation, setFilterOperation] = useState('')
  const [filterResult, setFilterResult] = useState(undefined)

  useEffect(() => {
    if (!userInfo || !userInfo?.permissions?.includes('system:log:manage')) {
      message.error('无权访问，正在跳转...')
      setTimeout(() => navigate('/404', { replace: true }), 500)
    }
  }, [userInfo, navigate, message])

  const fetchData = useCallback(async (page = current, size = pageSize) => {
    setLoading(true)
    try {
      const params = { current: page, pageSize: size }
      if (searchUsername) params.username = searchUsername
      if (filterOperation) params.operation = filterOperation
      if (filterResult !== undefined) params.result = filterResult
      const result = await api.post('/system/audit-log/list', params)
      setData(result?.records || [])
      setTotal(result?.total || 0)
    } catch (err) {
      message.error(err.message || '获取审计日志失败')
    } finally {
      setLoading(false)
    }
  }, [current, pageSize, searchUsername, filterOperation, filterResult, message])

  useEffect(() => {
    if (userInfo?.permissions?.includes('system:log:manage') && !hasFetchedRef.current) {
      hasFetchedRef.current = true
      fetchData()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleSearch = () => {
    setCurrent(1)
    fetchData(1, pageSize)
  }

  const handleReset = () => {
    setSearchUsername('')
    setFilterOperation('')
    setFilterResult(undefined)
    setCurrent(1)
    fetchData(1, pageSize)
  }

  const handleTableChange = (pagination) => {
    const { current: c, pageSize: s } = pagination
    setCurrent(c)
    setPageSize(s)
    fetchData(c, s)
  }

  const formatTime = (t) => {
    if (!t) return '-'
    return new Date(t).toLocaleString('zh-CN')
  }

  if (!userInfo || !userInfo?.permissions?.includes('system:log:manage')) {
    return (
      <main className="audit-log-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '操作类型', dataIndex: 'operation', key: 'operation', width: 130 },
    { title: '模块', dataIndex: 'module', key: 'module', width: 120 },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
    {
      title: '结果', dataIndex: 'result', key: 'result', width: 80,
      render: (r) => <Tag color={RESULT_COLOR[r]}>{RESULT_MAP[r] || r}</Tag>,
    },
    { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
    {
      title: '时间', dataIndex: 'createTime', key: 'createTime', width: 180,
      render: (t) => formatTime(t),
    },
  ]

  return (
    <main className="audit-log-container">
      <div className="audit-log-header">
        <Title level={2}>审计日志</Title>
        <p className="header-subtitle">查看系统操作审计记录</p>
      </div>

      <Card variant="borderless" className="audit-log-card">
        <div className="audit-log-filter-bar">
          <Space wrap>
            <Input
              placeholder="搜索用户名"
              value={searchUsername}
              onChange={(e) => setSearchUsername(e.target.value)}
              onPressEnter={handleSearch}
              style={{ width: 160 }}
              allowClear
            />
            <Select
              placeholder="操作类型"
              value={filterOperation || undefined}
              onChange={(v) => setFilterOperation(v || '')}
              allowClear
              style={{ width: 150 }}
              options={OPERATION_OPTIONS.filter((o) => o.value)}
            />
            <Select
              placeholder="结果"
              value={filterResult}
              onChange={(v) => setFilterResult(v)}
              allowClear
              style={{ width: 100 }}
              options={[
                { label: '成功', value: 1 },
                { label: '失败', value: 0 },
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
          scroll={{ x: 1000 }}
        />
      </Card>
    </main>
  )
}

export default AuditLogManagement
