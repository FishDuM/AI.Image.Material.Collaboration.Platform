import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Tag, Space, Button, Card, Typography, Avatar, Popconfirm, Dropdown, Input, Row, Col, Form, Select, Modal } from 'antd'
import { UserOutlined, EditOutlined, DeleteOutlined, SettingOutlined, TeamOutlined, LogoutOutlined, SunOutlined, MoonOutlined, SearchOutlined, ReloadOutlined, LockOutlined, UnlockOutlined, HomeOutlined } from '@ant-design/icons'
import { getUserInfo, removeUserInfo, request } from '../utils/storage'
import { ThemeContext } from '../main.jsx'
import '../App.css'
import './UserManagement.css'

const { Title } = Typography

function UserManagement() {
  const { message } = AntApp.useApp()
  const { isDarkMode, toggleTheme } = useContext(ThemeContext)
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [users, setUsers] = useState([])
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const [currentUser, setCurrentUser] = useState(null)
  const [searchParams, setSearchParams] = useState({
    id: null,
    username: null,
    phone: null,
    nickname: null,
    role: null,
  })
  const hasFetchedRef = useRef(false)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingUser, setEditingUser] = useState(null)

  const handleLogout = () => {
    removeUserInfo()
    setCurrentUser(null)
    message.success('已退出登录')
    navigate('/')
  }

  const systemManagementMenuItems = [
    {
      key: 'user-management',
      icon: <TeamOutlined />,
      label: '用户管理',
      onClick: () => {
        navigate('/admin/users')
      },
    },
  ]

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ]

  const fetchUserList = useCallback(async (current, pageSize, params = {}) => {
    setLoading(true)
    try {
      const result = await request('/api/user/admin/userList', {
        method: 'POST',
        body: JSON.stringify({
          current,
          pageSize,
          ...params,
        }),
      })

      const { records, total } = result.data
      setUsers(records || [])
      setPagination(prev => ({
        ...prev,
        current,
        pageSize,
        total: total || 0,
      }))
      message.success('获取用户列表成功')
    } catch (error) {
      console.error('获取用户列表失败:', error)
      message.error('获取用户列表失败，请检查网络连接')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
    } finally {
      setLoading(false)
    }
  }, [navigate, message, searchParams])

  useEffect(() => {
    const user = getUserInfo()
    setCurrentUser(user)

    if (!user || user.role !== 'admin') {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
      return
    }

    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchUserList(1, 20)
  }, [fetchUserList])

  const handleDeleteUser = async (userId) => {
    try {
      const result = await request(`/api/user/delete/${userId}`, {
        method: 'DELETE',
      })

      message.success('删除用户成功')
      fetchUserList(pagination.current, pagination.pageSize)
    } catch (error) {
      console.error('删除用户失败:', error)
      message.error('删除用户失败：' + error.message)
    }
  }

  const handleSetStatus = async (userId) => {
    if (currentUser && currentUser.id === userId) {
      message.warning('不能封禁自己')
      return
    }

    try {
      const result = await request('/api/user/admin/setStatus', {
        method: 'POST',
        body: JSON.stringify({ userId }),
      })

      message.success('操作成功')
      fetchUserList(pagination.current, pagination.pageSize)
    } catch (error) {
      console.error('操作失败:', error)
      message.error('操作失败：' + error.message)
    }
  }

  const handleTableChange = (pag) => {
    fetchUserList(pag.current, pag.pageSize)
  }

  const handleSearch = (values) => {
    const newParams = {
      id: values.id || null,
      username: values.username || null,
      phone: values.phone || null,
      nickname: values.nickname || null,
      role: values.role || null,
      status: values.status || null,
    }
    setSearchParams(newParams)
    fetchUserList(1, pagination.pageSize, newParams)
  }

  const handleReset = () => {
    form.resetFields()
    setSearchParams({
      id: null,
      username: null,
      phone: null,
      nickname: null,
      role: null,
      status: null,
    })
    fetchUserList(1, pagination.pageSize)
  }

  const handleEdit = (record) => {
    setEditingUser(record)
    editForm.setFieldsValue({
      id: record.id,
      username: record.username,
      password: '',
      nickname: record.nickname,
      avatar: record.avatar,
      email: record.email,
      phone: record.phone,
      role: record.role,
      status: record.status,
    })
    setIsModalOpen(true)
  }

  const handleEditSubmit = async (values) => {
    try {
      const submitData = {
        id: editingUser.id,
        username: values.username,
        password: values.password || null,
        avatar: values.avatar || null,
        email: values.email || null,
        phone: values.phone || null,
        nickname: values.nickname || null,
        status: values.status,
        role: values.role,
      }

      const response = await fetch('/api/user/admin/editUser', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(submitData),
      })

      const result = await response.json()

      if (result.code !== 1) {
        throw new Error(result.message || '编辑用户失败')
      }

      message.success('编辑用户成功')
      setIsModalOpen(false)
      editForm.resetFields()
      fetchUserList(pagination.current, pagination.pageSize)
    } catch (error) {
      console.error('编辑用户失败:', error)
      message.error('编辑用户失败：' + error.message)
    }
  }

  const handleModalClose = () => {
    setIsModalOpen(false)
    editForm.resetFields()
    setEditingUser(null)
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
        <Avatar style={{ backgroundColor: 'var(--accent)' }}>
          {(record.nickname || record.username)?.charAt(0)?.toUpperCase()}
        </Avatar>
      ),
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      render: (nickname) => nickname || '无昵称',
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
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      render: (role) => (
        <Tag color={role === 'admin' ? 'red' : 'blue'}>
          {role === 'admin' ? '管理员' : '普通用户'}
        </Tag>
      ),
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
        })
      },
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

  if (!currentUser || currentUser.role !== 'admin') {
    return (
      <div className="user-management-page">
        <header className="app-header">
          <div className="header-content">
            <div className="logo-section">
              <h1 className="logo-text" onClick={() => navigate('/')}>FishPics</h1>
            </div>
            <div className="header-actions">
              <Button
                type="text"
                size="large"
                className="theme-toggle-btn"
                onClick={toggleTheme}
                icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />}
              />
            </div>
          </div>
        </header>
        <main className="user-management-container">
          <div style={{ textAlign: 'center', padding: '100px 0' }}>
            <Title level={3}>无权访问</Title>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="user-management-page">
      <header className="app-header">
        <div className="header-content">
          <div className="logo-section">
            <h1 className="logo-text" onClick={() => navigate('/')}>FishPics</h1>
            <Button
              type="text"
              size="large"
              icon={<HomeOutlined />}
              onClick={() => navigate('/')}
            >
              首页
            </Button>
            <Dropdown menu={{ items: systemManagementMenuItems }} placement="bottomLeft">
              <Button
                type="text"
                size="large"
                className="system-management-btn"
              >
                <SettingOutlined />
                <span>系统管理</span>
              </Button>
            </Dropdown>
          </div>
          <div className="header-actions">
            {currentUser && (
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
                <div className="user-info">
                  <Avatar size={32} style={{ backgroundColor: 'var(--accent)' }}>
                    {(currentUser.nickname || currentUser.username)?.charAt(0)?.toUpperCase()}
                  </Avatar>
                  <span className="user-name">{currentUser.nickname || currentUser.username}</span>
                </div>
              </Dropdown>
            )}
            <Button
              type="text"
              size="large"
              className="theme-toggle-btn"
              onClick={toggleTheme}
              icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />}
            />
          </div>
        </div>
      </header>

      <main className="user-management-container">
        <div className="user-management-header">
          <Title level={2}>用户管理</Title>
          <p className="header-subtitle">管理系统所有用户信息和权限</p>
        </div>

        <Card className="search-card" variant="borderless">
          <Form
            form={form}
            name="search"
            onFinish={handleSearch}
            layout="inline"
            className="search-form"
          >
            <Row gutter={[24, 24]}>
              <Col xs={24} sm={12} md={8} lg={8}>
                <Form.Item name="id" label="ID">
                  <Input placeholder="请输入用户 ID" allowClear />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} md={8} lg={8}>
                <Form.Item name="username" label="账号">
                  <Input placeholder="请输入账号" allowClear />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} md={8} lg={8}>
                <Form.Item name="phone" label="手机号">
                  <Input placeholder="请输入手机号" allowClear />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} md={8} lg={8}>
                <Form.Item name="nickname" label="昵称">
                  <Input placeholder="请输入昵称" allowClear />
                </Form.Item>
              </Col>
              <Form.Item name="role" label="角色">
                <Select placeholder="请选择角色" allowClear>
                  <Select.Option value="user">普通用户</Select.Option>
                  <Select.Option value="admin">管理员</Select.Option>
                </Select>
              </Form.Item>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" allowClear>
                  <Select.Option value="1">正常</Select.Option>
                  <Select.Option value="0">禁用</Select.Option>
                </Select>
              </Form.Item>
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
              locale: {
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
              },
            }}
            onChange={handleTableChange}
            scroll={{ x: 1400 }}
          />
        </Card>

        <Modal
          title="编辑用户"
          open={isModalOpen}
          onCancel={handleModalClose}
          footer={null}
          centered
          className="edit-user-modal"
          destroyOnClose
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
              <Col xs={24} sm={12}>
                <Form.Item
                  name="username"
                  label="账号"
                  rules={[
                    { required: true, message: '请输入账号' },
                    { min: 6, message: '账号至少 6 个字符' },
                  ]}
                >
                  <Input 
                    placeholder="请输入账号" 
                    disabled
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="password"
                  label="密码"
                  rules={[
                    { min: 6, message: '密码至少 6 个字符' },
                  ]}
                >
                  <Input.Password placeholder="请输入密码（不修改请留空）" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="nickname"
                  label="昵称"
                >
                  <Input placeholder="请输入昵称" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="avatar"
                  label="头像 URL"
                  rules={[
                    { type: 'url', message: '请输入有效的 URL' },
                  ]}
                >
                  <Input placeholder="请输入头像 URL" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="email"
                  label="邮箱"
                  rules={[
                    { type: 'email', message: '请输入有效的邮箱地址' },
                  ]}
                >
                  <Input placeholder="请输入邮箱" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="phone"
                  label="手机号"
                  rules={[
                    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' },
                  ]}
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
                  <Select placeholder="请选择角色">
                    <Select.Option value="user">普通用户</Select.Option>
                    <Select.Option value="admin">管理员</Select.Option>
                  </Select>
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="status"
                  label="状态"
                  rules={[{ required: true, message: '请选择状态' }]}
                >
                  <Select placeholder="请选择状态">
                    <Select.Option value={1}>正常</Select.Option>
                    <Select.Option value={0}>禁用</Select.Option>
                  </Select>
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
    </div>
  )
}

export default UserManagement
