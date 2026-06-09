import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Card, Typography, Button, Image, Tag, Space, Select, Popconfirm } from 'antd'
import { PictureOutlined, EyeOutlined, EyeInvisibleOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, StarOutlined, StarFilled } from '@ant-design/icons'
import { getAdminPictureList, reviewPicture } from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import { PAGINATION_LOCALE } from '../utils/constants'
import './AdminPictureManagement.css'

const { Title } = Typography

const STATUS_OPTIONS = [
  { value: 3, label: '全部' },
  { value: 1, label: '正常' },
  { value: 2, label: '待审核' },
  { value: 0, label: '禁用' },
  { value: 4, label: '精选' },
]

const STATUS_MAP = {
  0: { color: 'orange', text: '禁用', icon: <EyeInvisibleOutlined /> },
  1: { color: 'green', text: '正常', icon: <EyeOutlined /> },
  2: { color: 'orange', text: '待审核', icon: <PictureOutlined /> },
}

function AdminPictureManagement() {
  const { message, modal } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [pictures, setPictures] = useState([])
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [statusFilter, setStatusFilter] = useState(3)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const hasFetchedRef = useRef(false)

  const fetchPictureList = useCallback(async (current, pageSize, status) => {
    setLoading(true)
    try {
      const result = await getAdminPictureList(current, pageSize, status)
      const records = Array.isArray(result) ? result : (result?.records || [])
      const total = result?.total || (Array.isArray(result) ? result.length : 0)
      setPictures(records)
      setPagination(prev => ({
        ...prev,
        current,
        pageSize,
        total,
      }))
      setSelectedRowKeys([])
    } catch (error) {
      message.error(error.message || '获取图片列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (!userInfo || !userInfo?.permissions?.includes('system:user:manage')) {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
      return
    }
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchPictureList(1, 20, statusFilter)
  }, [fetchPictureList, statusFilter, userInfo, navigate, message])

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

  const handleReview = async (pictureId, status, selected) => {
    try {
      await reviewPicture(pictureId, status, selected)
      const statusText = status === 1 ? '通过' : '拒绝'
      message.success(`图片已${statusText}`)
      fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '审核操作失败')
    }
  }

  const handleBatchReview = (status) => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择图片')
      return
    }
    const statusText = status === 1 ? '通过' : '拒绝'
    modal.confirm({
      title: `批量${statusText}`,
      content: `确定要将选中的 ${selectedRowKeys.length} 张图片${statusText}吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          for (const id of selectedRowKeys) {
            const record = pictures.find(p => p.id === id)
            await reviewPicture(id, status, record?.isSelected)
          }
          message.success(`已批量${statusText} ${selectedRowKeys.length} 张图片`)
          fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
        } catch (error) {
          message.error(error.message || '批量审核失败')
        }
      },
    })
  }

  const handleBatchFeatured = (selectedValue) => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择图片')
      return
    }
    const actionText = selectedValue === 1 ? '精选' : '取消精选'
    modal.confirm({
      title: `批量${actionText}`,
      content: `确定要将选中的 ${selectedRowKeys.length} 张图片${actionText}吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          for (const id of selectedRowKeys) {
            const record = pictures.find(p => p.id === id)
            await reviewPicture(id, record?.status, selectedValue)
          }
          message.success(`已批量${actionText} ${selectedRowKeys.length} 张图片`)
          fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
        } catch (error) {
          message.error(error.message || '批量操作失败')
        }
      },
    })
  }

  const handleToggleFeatured = async (record, selectedValue) => {
    try {
      await reviewPicture(record.id, record.status, selectedValue)
      const actionText = selectedValue === 1 ? '精选' : '取消精选'
      message.success(`已${actionText}`)
      fetchPictureList(pagination.current, pagination.pageSize, statusFilter)
    } catch (error) {
      message.error(error.message || '操作失败')
    }
  }

  const rowSelection = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys),
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
      width: 220,
      fixed: 'right',
      render: (_, record) => {
        if (record.status === 2) {
          return (
            <Space size="small">
              <Popconfirm
                title="确认通过"
                description="确定通过该图片审核？"
                onConfirm={() => handleReview(record.id, 1, record.isSelected)}
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
                onConfirm={() => handleReview(record.id, 0, record.isSelected)}
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
                onConfirm={() => handleReview(record.id, 0, record.isSelected)}
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
              {record.isSelected === 0 ? (
                <Popconfirm
                  title="确认精选"
                  description="确定精选该图片？"
                  onConfirm={() => handleToggleFeatured(record, 1)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button
                    size="small"
                    icon={<StarOutlined />}
                    style={{ color: '#d4a017', borderColor: '#d4a017' }}
                  >
                    精选
                  </Button>
                </Popconfirm>
              ) : (
                <Popconfirm
                  title="确认取消精选"
                  description="确定取消精选该图片？"
                  onConfirm={() => handleToggleFeatured(record, 0)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button
                    size="small"
                    icon={<StarFilled />}
                    style={{ color: '#d4a017', borderColor: '#d4a017' }}
                  >
                    取消精选
                  </Button>
                </Popconfirm>
              )}
            </Space>
          )
        }
        if (record.status === 0) {
          return (
            <Space size="small">
              <Popconfirm
                title="确认启用"
                description="确定启用该图片？"
                onConfirm={() => handleReview(record.id, 1, record.isSelected)}
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
              {record.isSelected === 0 ? (
                <Popconfirm
                  title="确认精选"
                  description="确定精选该图片？"
                  onConfirm={() => handleToggleFeatured(record, 1)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button
                    size="small"
                    icon={<StarOutlined />}
                    style={{ color: '#d4a017', borderColor: '#d4a017' }}
                  >
                    精选
                  </Button>
                </Popconfirm>
              ) : (
                <Popconfirm
                  title="确认取消精选"
                  description="确定取消精选该图片？"
                  onConfirm={() => handleToggleFeatured(record, 0)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button
                    size="small"
                    icon={<StarFilled />}
                    style={{ color: '#d4a017', borderColor: '#d4a017' }}
                  >
                    取消精选
                  </Button>
                </Popconfirm>
              )}
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
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleBatchReview(1)}
            >
              批量通过 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
            </Button>
            <Button
              danger
              icon={<CloseOutlined />}
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleBatchReview(0)}
            >
              批量拒绝 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
            </Button>
            <Button
              disabled={selectedRowKeys.length === 0}
              icon={<StarOutlined />}
              style={{ color: '#d4a017', borderColor: '#d4a017' }}
              onClick={() => handleBatchFeatured(1)}
            >
              批量精选 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
            </Button>
            <Button
              danger
              icon={<StarFilled />}
              disabled={selectedRowKeys.length === 0}
              onClick={() => handleBatchFeatured(0)}
            >
              批量不精选 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
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
          rowSelection={rowSelection}
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
            locale: PAGINATION_LOCALE,
          }}
          onChange={handleTableChange}
          scroll={{ x: 1120 }}
        />
      </Card>
    </main>
  )
}

export default AdminPictureManagement
