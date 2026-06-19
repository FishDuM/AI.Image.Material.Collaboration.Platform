import { useState, useEffect, useCallback } from 'react'
import { App as AntApp, Table, Typography, Button, Image, Tag, Space, Select, Popconfirm, Card } from 'antd'
import { PictureOutlined, ReloadOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { getAdminPictureList, reviewPicture } from '../api'
import { useAdminGuard } from '../hooks/useAdminGuard'
import { PAGINATION_LOCALE, formatDateTime, getPlaceholderImageBase64 } from '../utils/constants'
import './AdminPictureManagement.css'

const { Title } = Typography

const SELECTED_OPTIONS = [
  { value: 1, label: '已精选' },
  { value: 2, label: '精选申请' },
  { value: 0, label: '普通' },
]

const SELECTED_MAP = {
  0: { color: 'default', text: '普通' },
  1: { color: 'gold', text: '已精选' },
  2: { color: 'orange', text: '申请中' },
}

function AdminPictureManagement() {
  const { message } = AntApp.useApp()
  const { hasPermission } = useAdminGuard('system:log:manage')
  const [loading, setLoading] = useState(false)
  const [pictures, setPictures] = useState([])
  const [selectedFilter, setSelectedFilter] = useState(1)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })

  const fetchPictureList = useCallback(async (current, pageSize, selected) => {
    setLoading(true)
    try {
      const result = await getAdminPictureList(current, pageSize, selected)
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
    if (!hasPermission) return
    fetchPictureList(1, pagination.pageSize, selectedFilter)
  }, [selectedFilter, hasPermission, fetchPictureList, pagination.pageSize])

  const refreshCurrentPage = () => {
    fetchPictureList(pagination.current, pagination.pageSize, selectedFilter)
  }

  const handleSetFeatured = async (pictureId) => {
    try {
      await reviewPicture(pictureId, 1)
      message.success('已设为精选')
      refreshCurrentPage()
    } catch (error) {
      message.error(error.message || '操作失败')
    }
  }

  const handleCancelFeatured = async (pictureId) => {
    try {
      await reviewPicture(pictureId, 0)
      message.success('已取消精选')
      refreshCurrentPage()
    } catch (error) {
      message.error(error.message || '操作失败')
    }
  }

  const renderActions = (record) => {
    const isFeaturedPending = record.isSelected === 2
    const isSelected = record.isSelected === 1

    return (
      <Space size="small" wrap>
        {isFeaturedPending && (
          <>
            <Popconfirm
              title="通过精选申请"
              description="确认将这张图片设为精选？"
              onConfirm={() => handleSetFeatured(record.id)}
              okText="通过"
              cancelText="取消"
            >
              <Button type="primary" size="small" icon={<CheckOutlined />}>
                通过
              </Button>
            </Popconfirm>
            <Popconfirm
              title="拒绝精选申请"
              description="确认拒绝这张图片的精选申请？"
              onConfirm={() => handleCancelFeatured(record.id)}
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
        {isSelected && (
          <Popconfirm
            title="取消精选"
            description="确认取消这张图片的精选状态？取消后将不在首页展示。"
            onConfirm={() => handleCancelFeatured(record.id)}
            okText="取消精选"
            cancelText="关闭"
            okButtonProps={{ danger: true }}
          >
            <Button danger size="small" icon={<CloseOutlined />}>
              取消精选
            </Button>
          </Popconfirm>
        )}
        {!isSelected && !isFeaturedPending && (
          <Popconfirm
            title="设为精选"
            description="确认将这张图片设为精选？设为精选后将在首页展示。"
            onConfirm={() => handleSetFeatured(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="primary" size="small" icon={<CheckOutlined />}>
              精选
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
          fallback={getPlaceholderImageBase64(80, 80)}
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
      render: (createTime) => formatDateTime(createTime),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => renderActions(record),
    },
  ]

  return (
    <main className="admin-picture-container">
      <div className="admin-picture-header">
        <Title level={2}>
          <PictureOutlined style={{ marginRight: 8 }} />
          图片精选
        </Title>
        <p className="header-subtitle">管理首页精选图片，审核精选申请</p>
      </div>
      <Card variant="borderless" className="admin-picture-card">
        <div className="admin-picture-toolbar">
          <Space wrap>
            <Select
              value={selectedFilter}
              onChange={(value) => {
                setSelectedFilter(value)
                setPagination(prev => ({ ...prev, current: 1 }))
              }}
              style={{ width: 140 }}
              options={SELECTED_OPTIONS}
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
          onChange={(page) => fetchPictureList(page.current, page.pageSize, selectedFilter)}
          scroll={{ x: 980 }}
        />
      </Card>
    </main>
  )
}

export default AdminPictureManagement
