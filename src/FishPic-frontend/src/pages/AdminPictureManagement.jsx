import { useState, useEffect, useCallback, useRef } from 'react'
import { App as AntApp, Table, Card, Typography, Button, Image, Tag, Space, Select, Modal, Popconfirm } from 'antd'
import { PictureOutlined, EyeOutlined, EyeInvisibleOutlined, ReloadOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { getAdminPictureList, reviewPicture } from '../api'
import './AdminPictureManagement.css'

const { Title } = Typography

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

const STATUS_OPTIONS = [
  { value: -1, label: '全部' },
  { value: 1, label: '正常' },
  { value: 2, label: '待审核' },
  { value: 0, label: '禁用' },
]

const STATUS_MAP = {
  0: { color: 'red', text: '禁用', icon: <EyeInvisibleOutlined /> },
  1: { color: 'green', text: '正常', icon: <EyeOutlined /> },
  2: { color: 'orange', text: '待审核', icon: <PictureOutlined /> },
}

function AdminPictureManagement() {
  const { message } = AntApp.useApp()
  const [loading, setLoading] = useState(false)
  const [pictures, setPictures] = useState([])
  const [statusFilter, setStatusFilter] = useState(-1)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const hasFetchedRef = useRef(false)

  const fetchPictureList = useCallback(async (current, pageSize, status) => {
    setLoading(true)
    try {
      const result = await getAdminPictureList(current, pageSize)
      let { records, total } = result
      if (records && status !== -1) {
        records = records.filter(r => r.status === status)
        total = records.length
      }
      setPictures(records || [])
      setPagination(prev => ({
        ...prev,
        current,
        pageSize,
        total: total || 0,
      }))
    } catch (error) {
      console.error('获取图片列表失败:', error)
      message.error(error.message || '获取图片列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchPictureList(1, 20, statusFilter)
  }, [fetchPictureList, statusFilter])

  const handleTableChange = (pag) => {
    fetchPictureList(pag.current, pag.pageSize, statusFilter)
  }

  const handleRefresh = () => {
    fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
  }

  const handleStatusFilterChange = (value) => {
    setStatusFilter(value)
    setPagination(prev => ({ ...prev, current: 1 }))
    fetchPictureList(1, pagination.pageSize, value)
  }

  const handleReview = async (pictureId, status) => {
    try {
      await reviewPicture(pictureId, status)
      const statusText = status === 1 ? '通过' : '拒绝'
      message.success(`图片已${statusText}`)
      fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '审核操作失败')
    }
  }

  const handleBatchReview = (status) => {
    const pendingIds = pictures
      .filter(p => p.status === 2)
      .map(p => p.id)
    if (pendingIds.length === 0) {
      message.warning('没有待审核的图片')
      return
    }
    const statusText = status === 1 ? '通过' : '拒绝'
    Modal.confirm({
      title: `批量${statusText}`,
      content: `确定要将 ${pendingIds.length} 张待审核图片${statusText}吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          for (const id of pendingIds) {
            await reviewPicture(id, status)
          }
          message.success(`已批量${statusText} ${pendingIds.length} 张图片`)
          fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
        } catch (error) {
          message.error(error.message || '批量审核失败')
        }
      },
    })
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: '图片预览',
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
          preview={{ cover: <EyeOutlined /> }}
          fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAiIGhlaWdodD0iODAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjgwIiBoZWlnaHQ9IjgwIiBmaWxsPSIjMjEyMTIxIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIxMiIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPkxvYWRpbmcuLi48L3RleHQ+PC9zdmc+"
        />
      ),
    },
    {
      title: '图片地址',
      dataIndex: 'url',
      key: 'url-text',
      ellipsis: true,
      render: (url) => (
        <a href={url} target="_blank" rel="noopener noreferrer" style={{ fontSize: 12 }}>
          {url}
        </a>
      ),
    },
    {
      title: '尺寸',
      key: 'size',
      width: 120,
      render: (_, record) => {
        if (record.width && record.height) {
          return <Tag>{record.width} × {record.height}</Tag>
        }
        return <Tag>未知</Tag>
      },
    },
    {
      title: '文件大小',
      dataIndex: 'size',
      key: 'file-size',
      width: 100,
      render: (size) => {
        if (!size) return '-'
        const bytes = parseInt(size, 10)
        if (isNaN(bytes)) return size
        if (bytes < 1024) return `${bytes} B`
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => {
        const info = STATUS_MAP[status] || { color: 'default', text: '未知' }
        return <Tag color={info.color} icon={info.icon}>{info.text}</Tag>
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (createTime) => {
        if (!createTime) return '-'
        const date = new Date(createTime)
        return date.toLocaleString('zh-CN', {
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
      width: 150,
      fixed: 'right',
      render: (_, record) => {
        if (record.status === 2) {
          return (
            <Space size="small">
              <Popconfirm
                title="确认通过"
                description="确定通过该图片审核？"
                onConfirm={() => handleReview(record.id, 1)}
                okText="通过"
                cancelText="取消"
                okButtonProps={{ danger: false }}
              >
                <Button
                  type="primary"
                  size="small"
                  icon={<CheckOutlined />}
                >
                  通过
                </Button>
              </Popconfirm>
              <Popconfirm
                title="确认拒绝"
                description="确定拒绝该图片审核？"
                onConfirm={() => handleReview(record.id, 0)}
                okText="拒绝"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button
                  danger
                  size="small"
                  icon={<CloseOutlined />}
                >
                  拒绝
                </Button>
              </Popconfirm>
            </Space>
          )
        }
        if (record.status === 1) {
          return (
            <Space size="small">
              <Popconfirm
                title="确认禁用"
                description="确定禁用该图片？"
                onConfirm={() => handleReview(record.id, 0)}
                okText="确定"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button
                  danger
                  size="small"
                  icon={<CloseOutlined />}
                >
                  禁用
                </Button>
              </Popconfirm>
            </Space>
          )
        }
        if (record.status === 0) {
          return (
            <Space size="small">
              <Popconfirm
                title="确认启用"
                description="确定启用该图片？"
                onConfirm={() => handleReview(record.id, 1)}
                okText="确定"
                cancelText="取消"
              >
                <Button
                  type="primary"
                  size="small"
                  icon={<CheckOutlined />}
                >
                  启用
                </Button>
              </Popconfirm>
            </Space>
          )
        }
        return <span style={{ color: 'var(--text-tertiary)' }}>—</span>
      },
    },
  ]

  return (
    <main className="admin-picture-container">
      <div className="admin-picture-header">
        <Title level={2}>
          <PictureOutlined style={{ marginRight: 8 }} />
          图片管理
        </Title>
        <p className="header-subtitle">管理系统中所有图片资源</p>
      </div>
      <Card variant="borderless" className="admin-picture-card">
        <div className="admin-picture-toolbar">
          <Space wrap>
            <Select
              value={statusFilter}
              onChange={handleStatusFilterChange}
              placeholder="全部"
              style={{ width: 120 }}
              options={STATUS_OPTIONS}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={handleRefresh}
            >
              刷新
            </Button>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              onClick={() => handleBatchReview(1)}
            >
              批量通过
            </Button>
            <Button
              danger
              icon={<CloseOutlined />}
              onClick={() => handleBatchReview(0)}
            >
              批量拒绝
            </Button>
          </Space>
          <span className="admin-picture-total">
            共 {pagination.total} 张图片
          </span>
        </div>
        <Table
          columns={columns}
          dataSource={pictures}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            locale: PAGINATION_LOCALE,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={handleTableChange}
          scroll={{ x: 1050 }}
        />
      </Card>
    </main>
  )
}

export default AdminPictureManagement
