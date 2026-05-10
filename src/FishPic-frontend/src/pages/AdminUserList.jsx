import { useState, useEffect, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Table, Tag, Space, Card, Typography, Avatar } from 'antd'
import { UserOutlined } from '@ant-design/icons'
import { AuthContext } from '../context/AuthContext.jsx'
import api from '../api'
import './AdminUserList.css'

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

function AdminUserList() {
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

  const fetchUserList = async (current, pageSize) => {
    setLoading(true)
    try {
      const result = await api.post('/user/admin/userList', {
        current,
        pageSize,
      })

      const { records, total } = result
      setUsers(records || [])
      setPagination({
        current,
        pageSize,
        total: total || 0,
      })
    } catch (error) {
      message.error(error.message || '获取用户列表失败')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
    } finally {
      setLoading(false)
    }
  }

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!userInfo || userInfo.role !== 'admin') {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
      return
    }

    fetchUserList(1, 20)
  }, [navigate, userInfo])
  /* eslint-enable react-hooks/set-state-in-effect */

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
          <Avatar 
            src={record.avatar}
            style={{ 
              backgroundColor: record.avatar ? 'transparent' : 'var(--accent)'
            }}
          >
            {!record.avatar && (record.nickname || username)?.charAt(0)?.toUpperCase()}
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

  if (!userInfo || userInfo.role !== 'admin') {
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
            locale: PAGINATION_LOCALE,
          }}
          onChange={handleTableChange}
          scroll={{ x: 800 }}
        />
      </Card>
    </div>
  )
}

export default AdminUserList
