import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Tag, Space, Card, Typography, Avatar } from 'antd'
import { UserOutlined } from '@ant-design/icons'
import { getUserInfo, request } from '../utils/storage'
import './AdminUserList.css'

const { Title } = Typography

function AdminUserList() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [users, setUsers] = useState([])
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })
  const [currentUser, setCurrentUser] = useState(null)

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

    fetchUserList(1, 20)
  }, [navigate])

  const fetchUserList = async (current, pageSize) => {
    setLoading(true)
    try {
      const result = await request('/api/user/admin/userList', {
        method: 'POST',
        body: JSON.stringify({
          current,
          pageSize,
        }),
      })

      const { records, total } = result.data
      console.log('用户数据:', records, '总数:', total)
      setUsers(records || [])
      setPagination({
        current,
        pageSize,
        total: total || 0,
      })
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
  }

  const handleTableChange = (pag) => {
    fetchUserList(pag.current, pag.pageSize)
  }

  const columns = [
    {
      title: '用户',
      dataIndex: 'username',
      key: 'username',
      render: (username, record) => (
        <Space>
          <Avatar style={{ backgroundColor: 'var(--accent)' }}>
            {(record.nickname || username)?.charAt(0)?.toUpperCase()}
          </Avatar>
          <div className="user-info">
            <div className="user-name">{username}</div>
            <div className="user-nickname">{record.nickname || '无昵称'}</div>
          </div>
        </Space>
      ),
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
  ]

  if (!currentUser || currentUser.role !== 'admin') {
    return null
  }

  return (
    <div className="admin-user-list-container">
      <div className="admin-user-list-header">
        <Title level={2}>用户列表管理</Title>
        <p className="header-subtitle">管理系统所有用户信息和权限</p>
      </div>

      <Card className="user-table-card" variant="borderless">
        <Table
          columns={columns}
          dataSource={users}
          loading={loading}
          rowKey="id"
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={handleTableChange}
          scroll={{ x: 800 }}
        />
      </Card>
    </div>
  )
}

export default AdminUserList
