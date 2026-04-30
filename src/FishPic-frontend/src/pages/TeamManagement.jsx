import { useEffect, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Empty } from 'antd'
import { AuthContext } from '../context/AuthContext.jsx'
import './TeamManagement.css'

const { Title } = Typography

function TeamManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)

  useEffect(() => {
    if (!userInfo || userInfo.role !== 'admin') {
      message.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => {
        navigate('/404', { replace: true })
      }, 500)
      return
    }
  }, [navigate, userInfo])

  if (!userInfo || userInfo.role !== 'admin') {
    return (
      <main className="team-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  return (
    <main className="team-management-container">
    <div className="team-management-header">
      <Title level={2}>团队管理</Title>
      <p className="header-subtitle">管理团队成员和团队配置</p>
    </div>
    <Card variant="borderless">
      <Empty description="功能开发中，敬请期待" />
    </Card>
    </main>
  )
}

export default TeamManagement
