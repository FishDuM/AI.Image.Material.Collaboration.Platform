import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Tag, Space, Button, Card, Typography, Avatar, Popconfirm, Input, Row, Col, Form, Select, Modal, Upload } from 'antd'
import { UserOutlined, EditOutlined, SearchOutlined, ReloadOutlined, LockOutlined, UnlockOutlined, PlusOutlined, LoadingOutlined } from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import api, { getAdminUser } from '../api'
import FunnyBackground from '../components/FunnyBackground'
import './UserManagement.css'

const { Title } = Typography

function UserManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [loading, setLoading] = useState(false)
  const [users, setUsers] = useState([])
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
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
  const [avatarVisible, setAvatarVisible] = useState(false)
  const [previewAvatar, setPreviewAvatar] = useState(null)
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState(null)
  const [uploadingAvatar, setUploadingAvatar] = useState(false)



  const fetchUserList = useCallback(async (current, pageSize, params = {}) => {
    setLoading(true)
    try {
      const result = await api.post('/user/admin/userList', {
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
    if (!userInfo || userInfo.role !== 'admin') {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
      return
    }

    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchUserList(1, 20)
  }, [fetchUserList, userInfo])

  const handleSetStatus = async (userId) => {
    if (userInfo && userInfo.id === userId) {
      message.warning('不能封禁自己')
      return
    }

    try {
      await api.post('/user/admin/setStatus', { userId })
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

  useEffect(() => {
    if (isModalOpen && editingUser) {
      requestAnimationFrame(() => {
        editForm.setFieldsValue({
          id: editingUser.id,
          username: editingUser.username,
          password: '',
          nickname: editingUser.nickname,
          email: editingUser.email,
          phone: editingUser.phone,
          role: editingUser.role,
        })
      })
    }
  }, [isModalOpen, editingUser])

  const handleEdit = async (record) => {
    try {
      const data = await getAdminUser(record.id)
      setEditingUser(data)
      setIsModalOpen(true)
    } catch (error) {
      console.error('获取用户信息失败:', error)
      message.error('获取用户信息失败：' + error.message)
    }
  }

  const handleEditSubmit = async (values) => {
    try {
      const submitData = {
        id: editingUser.id,
        username: values.username,
        password: values.password || null,
        email: values.email || null,
        phone: values.phone || null,
        nickname: values.nickname || null,
        role: values.role,
      }

      await api.post('/user/admin/editUser', submitData)

      editForm.resetFields()
      message.success('编辑用户成功')
      setIsModalOpen(false)
      fetchUserList(pagination.current, pagination.pageSize)
    } catch (error) {
      console.error('编辑用户失败:', error)
      message.error('编辑用户失败：' + error.message)
    }
  }

  const handleModalClose = () => {
    editForm.resetFields()
    setIsModalOpen(false)
    setEditingUser(null)
    setAvatarPreviewUrl(null)
    setUploadingAvatar(false)
  }

  const getBase64 = (file) => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.addEventListener('load', () => resolve(reader.result))
      reader.addEventListener('error', reject)
      reader.readAsDataURL(file)
    })
  }

  const beforeUpload = (file) => {
    const isImage = file.type.startsWith('image/')
    if (!isImage) {
      message.error('只能上传图片文件！')
    }
    const isLt5M = file.size / 1024 / 1024 < 5
    if (!isLt5M) {
      message.error('图片大小不能超过5MB！')
    }
    return isImage && isLt5M
  }

  const handleAvatarChange = async (info) => {
    if (info.file.status === 'uploading') {
      setUploadingAvatar(true)
      return
    }
    if (info.file.status === 'done') {
      await getBase64(info.file.originFileObj).then((url) => {
        setUploadingAvatar(false)
        setAvatarPreviewUrl(url)
      })
      const avatarUrl = info.file.response
      setAvatarPreviewUrl(avatarUrl)
      message.success('头像上传成功')
    }
    if (info.file.status === 'error') {
      setUploadingAvatar(false)
      message.error('头像上传失败')
    }
  }

  const uploadButton = (
    <button style={{ border: 0, background: 'none' }} type="button">
      {uploadingAvatar ? <LoadingOutlined /> : <PlusOutlined />}
      <div style={{ marginTop: 8 }}>上传</div>
    </button>
  )

  const handleAvatarUpload = async (options) => {
    const { file, onSuccess, onError } = options
    
    setUploadingAvatar(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('id', editingUser.id)
      
      const result = await api.post('/picture/avatar', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      
      if (onSuccess) {
        onSuccess(result)
      }
    } catch (error) {
      if (onError) {
        onError(error)
      }
    }
  }

  const handleAvatarClick = (avatar) => {
    if (avatar) {
      setPreviewAvatar(avatar)
      setAvatarVisible(true)
    }
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
            cursor: record.avatar ? 'pointer' : 'default'
          }}
          onClick={() => handleAvatarClick(record.avatar)}
        >
          {!record.avatar && (record.nickname || record.username)?.charAt(0)?.toUpperCase()}
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

  if (!userInfo || userInfo.role !== 'admin') {
    return (
      <main className="user-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  return (
    <FunnyBackground>
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
          destroyOnHidden
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
    </FunnyBackground>
  )
}

export default UserManagement
