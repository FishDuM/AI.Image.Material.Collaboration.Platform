import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Card, Typography, Button, Tag, Space, Select, Popconfirm } from 'antd'
import { CommentOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, DeleteOutlined } from '@ant-design/icons'
import { getAdminCommentList, reviewComment, adminDeleteComment } from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import './AdminCommentManagement.css'

const { Title } = Typography

const STATUS_OPTIONS = [
  { value: undefined, label: '全部' },
  { value: 2, label: '待审核' },
  { value: 1, label: '正常' },
  { value: 0, label: '禁用' },
]

const STATUS_MAP = {
  0: { color: 'red', text: '已禁用' },
  1: { color: 'green', text: '正常' },
  2: { color: 'orange', text: '待审核' },
}

function AdminCommentManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [statusFilter, setStatusFilter] = useState(undefined)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const hasFetchedRef = useRef(false)

  const fetchList = useCallback(async (current, pageSize, status) => {
    setLoading(true)
    try {
      const params = { current, pageSize }
      if (status !== undefined && status !== null) params.status = status
      const result = await getAdminCommentList(params)
      setData(result?.records || [])
      setPagination(prev => ({ ...prev, current, pageSize, total: result?.total || 0 }))
    } catch (error) {
      message.error(error.message || '获取评论列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (!userInfo || !userInfo?.permissions?.includes('comment:list')) {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => navigate('/404', { replace: true }), 500)
      return
    }
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchList(1, 20, statusFilter)
  }, [fetchList, statusFilter, userInfo, navigate, message])

  const handleTableChange = (pag) => {
    fetchList(pag.current, pag.pageSize, statusFilter)
  }

  const handleStatusFilterChange = (value) => {
    setStatusFilter(value)
    setPagination(prev => ({ ...prev, current: 1 }))
    fetchList(1, pagination.pageSize, value)
  }

  const handleReview = async (id, status) => {
    try {
      await reviewComment(id, status)
      message.success(status === 1 ? '评论已通过' : '评论已拒绝')
      fetchList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '审核操作失败')
    }
  }

  const handleDelete = async (id) => {
    try {
      await adminDeleteComment(id)
      message.success('评论已删除')
      fetchList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '删除失败')
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '所属帖子', dataIndex: 'postTitle', key: 'postTitle', width: 180, ellipsis: true,
      render: (title, record) => {
        if (!title) return <span style={{ color: 'var(--text-tertiary)' }}>—</span>
        return (
          <Button type="link" size="small" style={{ padding: 0 }}
            onClick={() => window.open(`/community/post/${record.postId}`, '_blank')}>
            {title}
          </Button>
        )
      },
    },
    {
      title: '评论内容', dataIndex: 'content', key: 'content', ellipsis: true,
      render: (text) => text || <span style={{ color: 'var(--text-tertiary)' }}>—</span>,
    },
    { title: '评论人', dataIndex: 'username', key: 'username', width: 120 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (status) => {
        const info = STATUS_MAP[status] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '回复数', key: 'replyCount', width: 80,
      render: (_, record) => record.replies?.length || 0,
    },
    {
      title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180,
      render: (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '-',
    },
    {
      title: '操作', key: 'action', width: 200, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.status === 2 && (
            <>
              <Popconfirm title="确认通过该评论？" onConfirm={() => handleReview(record.id, 1)} okText="通过" cancelText="取消">
                <Button type="primary" size="small" icon={<CheckOutlined />}>通过</Button>
              </Popconfirm>
              <Popconfirm title="确认拒绝该评论？" onConfirm={() => handleReview(record.id, 0)} okText="拒绝" cancelText="取消" okButtonProps={{ danger: true }}>
                <Button danger size="small" icon={<CloseOutlined />}>拒绝</Button>
              </Popconfirm>
            </>
          )}
          {record.status === 1 && (
            <Popconfirm title="确认禁用该评论？" onConfirm={() => handleReview(record.id, 0)} okText="确定" cancelText="取消" okButtonProps={{ danger: true }}>
              <Button danger size="small" icon={<CloseOutlined />}>禁用</Button>
            </Popconfirm>
          )}
          {record.status === 0 && (
            <Popconfirm title="确认启用该评论？" onConfirm={() => handleReview(record.id, 1)} okText="确定" cancelText="取消">
              <Button type="primary" size="small" icon={<CheckOutlined />}>启用</Button>
            </Popconfirm>
          )}
          <Popconfirm title="确定删除该评论？" description="删除后不可恢复，子回复也将一并删除" onConfirm={() => handleDelete(record.id)} okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
            <Button size="small" icon={<DeleteOutlined />} style={{ color: '#999' }} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <main className="admin-comment-container">
      <div className="admin-comment-header">
        <Title level={2}>
          <CommentOutlined style={{ marginRight: 8 }} />
          评论审核
        </Title>
        <p className="header-subtitle">管理系统中所有评论</p>
      </div>
      <Card variant="borderless" className="admin-comment-card">
        <div className="admin-comment-toolbar">
          <Space wrap>
            <Select value={statusFilter} onChange={handleStatusFilterChange} style={{ width: 120 }} options={STATUS_OPTIONS} />
            <Button icon={<ReloadOutlined />} onClick={() => fetchList(pagination.current, pagination.pageSize, statusFilter)}>刷新</Button>
          </Space>
          <span className="admin-comment-total">共 {pagination.total} 条评论</span>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showQuickJumper: true, showTotal: (total) => `共 ${total} 条`, pageSizeOptions: ['10', '20', '50', '100'] }}
          onChange={handleTableChange}
          scroll={{ x: 1100 }}
        />
      </Card>
    </main>
  )
}

export default AdminCommentManagement
