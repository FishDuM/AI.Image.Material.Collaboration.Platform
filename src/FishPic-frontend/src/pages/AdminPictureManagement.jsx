import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Typography, Button, Image, Tag, Space, Select, Popconfirm } from 'antd'
import { PictureOutlined, ReloadOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { getAdminPictureList, reviewPicture } from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import { PAGINATION_LOCALE } from '../utils/constants'
import './AdminPictureManagement.css'

const { Title } = Typography

const STATUS_OPTIONS = [
  { value: 5, label: '精选申请' },
  { value: 4, label: '已精选' },
]

// isSelected 的语义：1=精选通过 2=用户申请精选 0=普通
const SELECTED_MAP = {
  1: { color: 'gold', text: '已精选' },
  2: { color: 'orange', text: '申请中' },
  0: { color: 'default', text: '普通' },
}

function AdminPictureManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [pictures, setPictures] = useState([])
  const [tab, setTab] = useState(5)
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
    fetchPictureList(1, 20, tab)
  }, [tab, userInfo, navigate, message, fetchPictureList])

  const handleReview = async (pictureId, selected) => {
    try {
      await reviewPicture(pictureId, null, selected)
      message.success(selected === 1 ? '已精选' : '已拒绝')
      fetchPictureList(pagination.current, pagination.pageSize, tab)
    } catch (error) {
      message.error(error.message || '操作失败')
    }
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
          src={url} alt="图片" width={80} height={80}
          style={{ objectFit: 'cover', borderRadius: 8 }}
          fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAiIGhlaWdodD0iODAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjgwIiBoZWlnaHQ9IjgwIiBmaWxsPSIjMjEyMTIxIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIxMiIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPkxvYWRpbmcuLi48L3RleHQ+PC9zdmc+"
        />
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
      title: '精选状态',
      dataIndex: 'isSelected',
      key: 'isSelected',
      width: 100,
      render: (v) => {
        const info = SELECTED_MAP[v] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (ct) => {
        if (!ct) return '-'
        return new Date(ct).toLocaleString('zh-CN', {
          year: 'numeric', month: '2-digit', day: '2-digit',
          hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
        })
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        if (tab === 5 && record.isSelected === 2) {
          return (
            <Space size="small">
              <Popconfirm
                title="确认精选"
                description="通过精选申请？"
                onConfirm={() => handleReview(record.id, 1)}
                okText="精选"
                cancelText="取消"
              >
                <Button type="primary" size="small" icon={<CheckOutlined />}>
                  精选
                </Button>
              </Popconfirm>
              <Popconfirm
                title="确认拒绝"
                description="拒绝精选申请？"
                onConfirm={() => handleReview(record.id, 0)}
                okText="拒绝"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button danger size="small" icon={<CloseOutlined />}>
                  拒绝
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
          精选管理
        </Title>
        <p className="header-subtitle">审批用户的精选申请</p>
      </div>
      <Card variant="borderless" className="admin-picture-card">
        <div className="admin-picture-toolbar">
          <Space wrap>
            <Select
              value={tab}
              onChange={(v) => { setTab(v); setPagination(prev => ({ ...prev, current: 1 })) }}
              style={{ width: 140 }}
              options={STATUS_OPTIONS}
            />
            <Button icon={<ReloadOutlined />} onClick={() => fetchPictureList(pagination.current, pagination.pageSize, tab)}>
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
          onChange={(pag) => fetchPictureList(pag.current, pag.pageSize, tab)}
          scroll={{ x: 800 }}
        />
      </Card>
    </main>
  )
}

export default AdminPictureManagement
