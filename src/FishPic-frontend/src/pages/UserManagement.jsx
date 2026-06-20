import { useState, useEffect, useCallback, useRef } from 'react'
import { App as AntApp, Table, Tag, Space, Button, Card, Typography, Avatar, Input, Row, Col, Form, Select, Modal, Upload } from 'antd'
import { EditOutlined, SearchOutlined, ReloadOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons'
import { adminEditUser, adminListUsers, adminSetUserStatus, getAdminUserDetail } from '../api'
import { createBeforeUpload } from '../utils/upload'
import { PAGINATION_LOCALE, formatDateTime } from '../utils/constants'
import { emailRules, optionalPasswordRules, phoneRules, usernameRules } from '../utils/formRules'
import { useAdminGuard } from '../hooks/useAdminGuard'
import { useAvatarUpload } from '../hooks/useAvatarUpload'
import { LEVEL_TAG_MAP, LEVEL_TAG_COLOR, ADMIN_ROLE_TAG } from '../utils/constants'
import './UserManagement.css'

const { Title } = Typography

const LEVEL_OPTIONS = [
  { value: 0, label: '普通用户' },
  { value: 1, label: 'VIP' },
  { value: 2, label: 'SVIP' },
]

const ROLE_OPTIONS = [
  { value: 0, label: '普通用户' },
  { value: 1, label: '管理员' },
]

function UserManagement() {
  const { message, modal } = AntApp.useApp()
  const beforeUpload = createBeforeUpload(message)
  const { userInfo } = useAdminGuard('system:user:manage')
  const [loading, setLoading] = useState(false)
  const [users, setUsers] = useState([])
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const hasFetchedRef = useRef(false)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingUser, setEditingUser] = useState(null)
  const [avatarVisible, setAvatarVisible] = useState(false)
  const [previewAvatar, setPreviewAvatar] = useState(null)

  const { previewUrl: avatarPreviewUrl, handleChange: handleAvatarChange, handleUpload: handleAvatarUpload, reset: resetAvatar, uploadButton } = useAvatarUpload({
    userId: editingUser?.id,
  })

  const fetchUserList = useCallback(async (current, pageSize, params = {}) => {
    setLoading(true)
    try {
      const result = await adminListUsers({
        current,
        pageSize,
        ...params,
      })

      const { records, total } = result
      setUsers(records || [])
      setPagination(prev => ({
        ...prev,
        current,
        pageSize,
        total: total || 0,
      }))
    } catch (error) {
      message.error(error.message || '获取用户列表失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    if (!userInfo || hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchUserList(1, 20)
  }, [fetchUserList, userInfo])

  useEffect(() => {
    if (!isModalOpen || !editingUser) return
    requestAnimationFrame(() => {
      editForm.setFieldsValue({
        id: editingUser.id,
        username: editingUser.username,
        password: '',
        nickname: editingUser.nickname,
        email: editingUser.email,
        phone: editingUser.phone,
        level: editingUser.level ?? 0,
        role: editingUser.roleId ?? 0,
      })
    })
  }, [editForm, editingUser, isModalOpen])

  const handleSetStatus = async (userId) => {
    if (userInfo?.id === userId) {
      message.warning('不能封禁自己')
      return
    }

    modal.confirm({
      title: '确认操作',
      content: '确定要切换该用户的启用状态吗？',
      onOk: async () => {
        try {
          await adminSetUserStatus(userId)
          message.success('操作成功')
          fetchUserList(pagination.current, pagination.pageSize)
        } catch (error) {
          message.error(error.message || '操作失败')
        }
      },
    })
  }

  const handleTableChange = (pag) => {
    fetchUserList(pag.current, pag.pageSize)
  }

  const handleSearch = (values) => {
    fetchUserList(1, pagination.pageSize, {
      id: values.id || null,
      username: values.username || null,
      phone: values.phone || null,
      nickname: values.nickname || null,
      status: values.status ?? null,
    })
  }

  const handleReset = () => {
    form.resetFields()
    fetchUserList(1, pagination.pageSize)
  }

  const handleEdit = async (record) => {
    try {
      const data = await getAdminUserDetail(record.id)
      setEditingUser(data)
      setIsModalOpen(true)
    } catch (error) {
      message.error(error.message || '获取用户信息失败')
    }
  }

  const handleEditSubmit = async (values) => {
    try {
      await adminEditUser({
        id: editingUser.id,
        username: values.username,
        password: values.password || null,
        nickname: values.nickname || null,
        email: values.email || null,
        phone: values.phone || null,
        level: values.level,
        role: values.role,
      })

      message.success('编辑用户成功')
      handleModalClose()
      fetchUserList(pagination.current, pagination.pageSize)
    } catch (error) {
      message.error(error.message || '编辑用户失败')
    }
  }

  const handleModalClose = () => {
    editForm.resetFields()
    setIsModalOpen(false)
    setEditingUser(null)
    resetAvatar()
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 180,
    },
    {
      title: '头像',
      dataIndex: 'avatar',
      key: 'avatar',
      width: 80,
      render: (_, record) => (
        <Avatar
          src={record.avatar}
          style={{
            backgroundColor: record.avatar ? 'transparent' : 'var(--accent)',
            cursor: record.avatar ? 'pointer' : 'default',
          }}
          onClick={() => {
            if (!record.avatar) return
            setPreviewAvatar(record.avatar)
            setAvatarVisible(true)
          }}
        >
          {!record.avatar && (record.nickname || record.username)?.charAt(0)?.toUpperCase()}
        </Avatar>
      ),
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      render: (nickname) => nickname || '未设置',
    },
    {
      title: '账号',
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      render: (email) => email || '-',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      render: (phone) => phone || '-',
    },
    {
      title: '等级',
      dataIndex: 'level',
      key: 'level',
      render: (level, record) => record.roleId === 1
        ? <Tag color={ADMIN_ROLE_TAG.color}>{ADMIN_ROLE_TAG.text}</Tag>
        : <Tag color={LEVEL_TAG_COLOR[level ?? 0] || 'default'}>{LEVEL_TAG_MAP[level ?? 0] || '普通'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={status === 1 ? 'green' : 'default'}>
          {status === 1 ? '正常' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={record.status === 1 ? <LockOutlined /> : <UnlockOutlined />}
            danger={record.status === 1}
            onClick={() => handleSetStatus(record.id)}
          >
            {record.status === 1 ? '封禁' : '解封'}
          </Button>
        </Space>
      ),
    },
  ]

  if (!userInfo) {
    return (
      <main className="user-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  return (
    <main className="user-management-container">
      <div className="user-management-header">
        <Title level={2}>用户管理</Title>
        <p className="header-subtitle">管理系统内所有用户的基本信息与等级状态</p>
      </div>

      <Card className="search-card" variant="borderless">
        <Form
          form={form}
          name="search"
          onFinish={handleSearch}
          className="search-form"
        >
          <Row gutter={[24, 16]}>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="id" label="ID">
                <Input placeholder="请输入用户 ID" allowClear />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="username" label="账号">
                <Input placeholder="请输入账号" allowClear />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="phone" label="手机号">
                <Input placeholder="请输入手机号" allowClear />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="nickname" label="昵称">
                <Input placeholder="请输入昵称" allowClear />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" allowClear>
                  <Select.Option value={1}>正常</Select.Option>
                  <Select.Option value={0}>禁用</Select.Option>
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24}>
              <div className="search-form-actions">
                <Space>
                  <Button
                    type="primary"
                    htmlType="submit"
                    icon={<SearchOutlined />}
                  >
                    查询
                  </Button>
                  <Button
                    htmlType="button"
                    icon={<ReloadOutlined />}
                    onClick={handleReset}
                  >
                    重置
                  </Button>
                </Space>
              </div>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card className="user-table-card" variant="borderless">
        <Table
          columns={columns}
          dataSource={users}
          loading={loading}
          rowKey="id"
          locale={{
            emptyText: '暂无数据',
          }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
            locale: PAGINATION_LOCALE,
          }}
          onChange={handleTableChange}
          scroll={{ x: 1400 }}
        />
      </Card>

      <Modal
        className="avatar-modal"
        open={avatarVisible}
        onCancel={() => setAvatarVisible(false)}
        footer={null}
        width={600}
      >
        {previewAvatar && <img src={previewAvatar} alt="avatar" />}
      </Modal>

      <Modal
        title="编辑用户"
        open={isModalOpen}
        onCancel={handleModalClose}
        footer={null}
        centered
        className="edit-user-modal"
      >
        <Form
          form={editForm}
          name="edit"
          onFinish={handleEditSubmit}
          layout="vertical"
          size="large"
          requiredMark={false}
        >
          <Row gutter={[16, 16]}>
            <Col xs={24}>
              <Form.Item label="修改头像">
                <Upload
                  name="avatar"
                  listType="picture-circle"
                  className="avatar-uploader"
                  showUploadList={false}
                  accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
                  customRequest={handleAvatarUpload}
                  beforeUpload={beforeUpload}
                  onChange={handleAvatarChange}
                >
                  {avatarPreviewUrl || editingUser?.avatar ? (
                    <img src={avatarPreviewUrl || editingUser.avatar} alt="avatar" style={{ width: '100%' }} />
                  ) : (
                    uploadButton
                  )}
                </Upload>
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="username"
                label="账号"
                rules={usernameRules}
              >
                <Input placeholder="请输入账号" disabled />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="password"
                label="密码"
                rules={optionalPasswordRules}
              >
                <Input.Password placeholder="不修改密码可留空" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="nickname" label="昵称">
                <Input placeholder="请输入昵称" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="email"
                label="邮箱"
                rules={emailRules}
              >
                <Input placeholder="请输入邮箱" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="phone"
                label="手机号"
                rules={phoneRules}
              >
                <Input placeholder="请输入手机号" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="role"
                label="角色"
                rules={[{ required: true, message: '请选择角色' }]}
              >
                <Select placeholder="请选择角色" options={ROLE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="level"
                label="等级"
                rules={[{ required: true, message: '请选择等级' }]}
              >
                <Select placeholder="请选择等级" options={LEVEL_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item style={{ marginBottom: 0, marginTop: 24 }}>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={handleModalClose}>取消</Button>
              <Button type="primary" htmlType="submit">
                确定
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </main>
  )
}

export default UserManagement
