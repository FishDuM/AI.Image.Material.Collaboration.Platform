import { useState, useEffect, useCallback, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Typography, Button, Image, Tag, Space, Select, Popconfirm, Card } from 'antd'
import { PictureOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, StopOutlined } from '@ant-design/icons'
import { getAdminPictureList, reviewPicture } from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import { PAGINATION_LOCALE } from '../utils/constants'
import './AdminPictureManagement.css'

const { Title } = Typography

const STATUS_OPTIONS = [
  { value: 2, label: '待审核' },
  { value: 1, label: '已通过' },
  { value: 0, label: '已禁用' },
  { value: 4, label: '已精选' },
  { value: 5, label: '精选审核' },
]

const STATUS_MAP = {
  0: { color: 'red', text: '已禁用' },
  1: { color: 'green', text: '已通过' },
  2: { color: 'orange', text: '待审核' },
}

const SELECTED_MAP = {
  0: { color: 'default', text: '普通' },
  1: { color: 'gold', text: '已精选' },
  2: { color: 'orange', text: '申请中' },
}

function AdminPictureManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [pictures, setPictures] = useState([])
  const [statusFilter, setStatusFilter] = useState(2)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })

  const fetchPictureList = useCallback(async (current, pageSize, status) => {
    setLoading(true)
    try {
      const result = await getAdminPictureList(current, pageSize, status)
      const records = Array.isArray(result) ? result : (result?.records || [])
      const total = result?.total || (Array.isArray(result) ? result.length : 0)
      setPictures(records)
      setPagination(prev => ({ ...prev, current, pageSize, total }))
    } catch (error) {
      message.error(error.message || '获取图片列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (!userInfo || !userInfo?.permissions?.includes('system:log:manage')) {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => navigate('/404', { replace: true }), 500)
      return
    }
    fetchPictureList(1, pagination.pageSize, statusFilter)
  }, [statusFilter, userInfo, navigate, message, fetchPictureList, pagination.pageSize])

  const refreshCurrentPage = () => {
    fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
  }

  const handleStatusReview = async (pictureId, status) => {
    try {
      await reviewPicture(pictureId, status, null)
      message.success(status === 1 ? '图片已通过' : '图片已禁用')
      refreshCurrentPage()
    } catch (error) {
      message.error(error.message || '操作失败')
    }
  }

  const handleSelectedReview = async (pictureId, selected) => {
    try {
      await reviewPicture(pictureId, null, selected)
      message.success(selected === 1 ? '已设为精选' : '已取消精选')
      refreshCurrentPage()
    } catch (error) {
      message.error(error.message || '操作失败')
    }
  }

  const renderActions = (record) => {
    const isPendingReview = record.status === 2
    const isFeaturedPending = record.isSelected === 2
    const isPassed = record.status === 1
    const isDisabled = record.status === 0
    const isSelected = record.isSelected === 1

    if (!isPendingReview && !isFeaturedPending && !isPassed && !isDisabled && !isSelected) {
      return <span style={{ color: 'var(--text-tertiary)' }}>-</span>
    }

    return (
      <Space size="small" wrap>
        {isPendingReview && (
          <>
            <Popconfirm
              title="通过图片审核"
              description="确认将这张图片设为已通过？"
              onConfirm={() => handleStatusReview(record.id, 1)}
              okText="通过"
              cancelText="取消"
            >
              <Button type="primary" size="small" icon={<CheckOutlined />}>
                通过
              </Button>
            </Popconfirm>
            <Popconfirm
              title="拒绝图片审核"
              description="确认将这张图片设为禁用？"
              onConfirm={() => handleStatusReview(record.id, 0)}
              okText="拒绝"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger size="small" icon={<CloseOutlined />}>
                拒绝
              </Button>
            </Popconfirm>
          </>
        )}
        {isFeaturedPending && (
          <>
            <Popconfirm
              title="通过精选申请"
              description="确认将这张图片设为精选？"
              onConfirm={() => handleSelectedReview(record.id, 1)}
              okText="精选"
              cancelText="取消"
            >
              <Button type="primary" size="small" icon={<CheckOutlined />}>
                精选
              </Button>
            </Popconfirm>
            <Popconfirm
              title="拒绝精选申请"
              description="确认拒绝这张图片的精选申请？"
              onConfirm={() => handleSelectedReview(record.id, 0)}
              okText="拒绝"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button danger size="small" icon={<CloseOutlined />}>
                拒绝
              </Button>
            </Popconfirm>
          </>
        )}
        {isPassed && !isPendingReview && (
          <Popconfirm
            title="禁用图片"
            description="确认禁用这张图片？"
            onConfirm={() => handleStatusReview(record.id, 0)}
            okText="禁用"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button danger size="small" icon={<StopOutlined />}>
              禁用
            </Button>
          </Popconfirm>
        )}
        {isDisabled && (
          <Popconfirm
            title="恢复图片"
            description="确认将这张图片恢复为已通过？"
            onConfirm={() => handleStatusReview(record.id, 1)}
            okText="恢复"
            cancelText="取消"
          >
            <Button type="primary" size="small" icon={<CheckOutlined />}>
              恢复
            </Button>
          </Popconfirm>
        )}
        {isSelected && !isFeaturedPending && (
          <Popconfirm
            title="取消精选"
            description="确认取消这张图片的精选状态？"
            onConfirm={() => handleSelectedReview(record.id, 0)}
            okText="取消精选"
            cancelText="关闭"
          >
            <Button size="small" icon={<CloseOutlined />}>
              取消精选
            </Button>
          </Popconfirm>
        )}
      </Space>
    )
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '图片',
      dataIndex: 'url',
      key: 'url',
      width: 120,
      render: (url) => (
        <Image
          src={url}
          alt="图片"
          width={80}
          height={80}
          style={{ objectFit: 'cover', borderRadius: 8 }}
          fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAiIGhlaWdodD0iODAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZyI+PHJlY3Qgd2lkdGg9IjgwIiBoZWlnaHQ9IjgwIiBmaWxsPSIjMjEyMTIxIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIxMiIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPkxvYWRpbmcuLi48L3RleHQ+PC9zdmc+"
        />
      ),
    },
    {
      title: '尺寸',
      key: 'size',
      width: 120,
      render: (_, record) => {
        if (record.width && record.height) {
          return <Tag>{record.width} x {record.height}</Tag>
        }
        return <Tag>未知</Tag>
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => {
        const info = STATUS_MAP[value] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '精选状态',
      dataIndex: 'isSelected',
      key: 'isSelected',
      width: 110,
      render: (value) => {
        const info = SELECTED_MAP[value] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 100,
      render: (value) => value || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (createTime) => {
        if (!createTime) return '-'
        return new Date(createTime).toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false,
        })
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      fixed: 'right',
      render: (_, record) => renderActions(record),
    },
  ]

  return (
    <main className="admin-picture-container">
      <div className="admin-picture-header">
        <Title level={2}>
          <PictureOutlined style={{ marginRight: 8 }} />
          图片审核
        </Title>
        <p className="header-subtitle">审核普通图片与精选申请</p>
      </div>
      <Card variant="borderless" className="admin-picture-card">
        <div className="admin-picture-toolbar">
          <Space wrap>
            <Select
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value)
                setPagination(prev => ({ ...prev, current: 1 }))
              }}
              style={{ width: 140 }}
              options={STATUS_OPTIONS}
            />
            <Button icon={<ReloadOutlined />} onClick={refreshCurrentPage}>
              刷新
            </Button>
          </Space>
          <span className="admin-picture-total">
            共 {pagination.total} 张
          </span>
        </div>
        <Table
          columns={columns}
          dataSource={pictures}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
            locale: PAGINATION_LOCALE,
          }}
          onChange={(page) => fetchPictureList(page.current, page.pageSize, statusFilter)}
          scroll={{ x: 980 }}
        />
      </Card>
    </main>
  )
}

export default AdminPictureManagement
