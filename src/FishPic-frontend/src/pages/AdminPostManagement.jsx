import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Card, Typography, Button, Tag, Space, Select, Popconfirm } from 'antd'
import { MessageOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons'
import { getAdminPostList, reviewPost, adminDeletePost, getPost } from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import { PAGINATION_LOCALE } from '../utils/constants'
import PostDetailModal from '../components/PostDetailModal'
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

function AdminPostManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [statusFilter, setStatusFilter] = useState(undefined)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const hasFetchedRef = useRef(false)
  const [detailPost, setDetailPost] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailImageIndex, setDetailImageIndex] = useState(0)
  const [showDetail, setShowDetail] = useState(false)

  const handleViewDetail = useCallback(async (postId) => {
    setShowDetail(true)
    setDetailLoading(true)
    setDetailImageIndex(0)
    try {
      const result = await getPost(postId)
      setDetailPost(result)
    } catch (error) {
      message.error(error.message || '获取帖子详情失败')
      setShowDetail(false)
    } finally {
      setDetailLoading(false)
    }
  }, [message])

  const fetchList = useCallback(async (current, pageSize, status) => {
    setLoading(true)
    try {
      const params = { current, pageSize }
      if (status !== undefined && status !== null) params.status = status
      const result = await getAdminPostList(params)
      setData(result?.records || [])
      setPagination(prev => ({ ...prev, current, pageSize, total: result?.total || 0 }))
    } catch (error) {
      message.error(error.message || '获取帖子列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (!userInfo || userInfo.role !== 'admin') {
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
      await reviewPost(id, status)
      message.success(status === 1 ? '帖子已通过' : '帖子已拒绝')
      fetchList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '审核操作失败')
    }
  }

  const handleDelete = async (id) => {
    try {
      await adminDeletePost(id)
      message.success('帖子已删除')
      fetchList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '删除失败')
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '标题', dataIndex: 'title', key: 'title', width: 200, ellipsis: true,
      render: (title, record) => {
        if (!title) return <span style={{ color: 'var(--text-tertiary)' }}>—</span>
        return (
          <Button type="link" size="small" style={{ padding: 0 }} onClick={() => handleViewDetail(record.id)}>
            {title}
          </Button>
        )
      },
    },
    {
      title: '作者', dataIndex: 'username', key: 'username', width: 120,
    },
    {
      title: '点赞', dataIndex: 'likesNum', key: 'likesNum', width: 80,
      render: (num) => num ?? 0,
    },
    {
      title: '评论', dataIndex: 'commentNum', key: 'commentNum', width: 80,
      render: (num) => num ?? 0,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (status) => {
        const info = STATUS_MAP[status] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180,
      render: (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '-',
    },
    {
      title: '操作', key: 'action', width: 260, fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewDetail(record.id)}>查看</Button>
          {record.status === 2 && (
            <>
              <Popconfirm title="确认通过该帖子？" onConfirm={() => handleReview(record.id, 1)} okText="通过" cancelText="取消">
                <Button type="primary" size="small" icon={<CheckOutlined />}>通过</Button>
              </Popconfirm>
              <Popconfirm title="确认拒绝该帖子？" onConfirm={() => handleReview(record.id, 0)} okText="拒绝" cancelText="取消" okButtonProps={{ danger: true }}>
                <Button danger size="small" icon={<CloseOutlined />}>拒绝</Button>
              </Popconfirm>
            </>
          )}
          {record.status === 1 && (
            <Popconfirm title="确认禁用该帖子？" onConfirm={() => handleReview(record.id, 0)} okText="确定" cancelText="取消" okButtonProps={{ danger: true }}>
              <Button danger size="small" icon={<CloseOutlined />}>禁用</Button>
            </Popconfirm>
          )}
          {record.status === 0 && (
            <Popconfirm title="确认启用该帖子？" onConfirm={() => handleReview(record.id, 1)} okText="确定" cancelText="取消">
              <Button type="primary" size="small" icon={<CheckOutlined />}>启用</Button>
            </Popconfirm>
          )}
          <Popconfirm title="确定删除该帖子？" description="删除后不可恢复" onConfirm={() => handleDelete(record.id)} okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
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
          <MessageOutlined style={{ marginRight: 8 }} />
          帖子审核
        </Title>
        <p className="header-subtitle">管理系统中所有帖子</p>
      </div>
      <Card variant="borderless" className="admin-comment-card">
        <div className="admin-comment-toolbar">
          <Space wrap>
            <Select value={statusFilter} onChange={handleStatusFilterChange} style={{ width: 120 }} options={STATUS_OPTIONS} />
            <Button icon={<ReloadOutlined />} onClick={() => fetchList(pagination.current, pagination.pageSize, statusFilter)}>刷新</Button>
          </Space>
          <span className="admin-comment-total">共 {pagination.total} 条帖子</span>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{ ...pagination, showSizeChanger: true, showQuickJumper: true, showTotal: (total) => `共 ${total} 条`, pageSizeOptions: ['10', '20', '50', '100'], locale: PAGINATION_LOCALE }}
          onChange={handleTableChange}
          scroll={{ x: 1200 }}
        />
      </Card>

      {showDetail && (
        <PostDetailModal
          open={showDetail}
          onClose={() => { setShowDetail(false); setDetailPost(null) }}
          loading={detailLoading}
          postDetail={detailPost}
          detailImageIndex={detailImageIndex}
          onImageIndexChange={setDetailImageIndex}
          currentUsername={userInfo?.username}
        />
      )}
    </main>
  )
}

export default AdminPostManagement
