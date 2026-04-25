import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Empty } from 'antd'
import { getUserInfo } from '../utils/storage'
import FunnyBackground from '../components/FunnyBackground'
import './TeamManagement.css'

const { Title } = Typography

function TeamManagement() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const hasFetchedRef = useRef(false)

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
  }, [navigate])

  if (!currentUser || currentUser.role !== 'admin') {
    return (
      <FunnyBackground>
        <main className="team-management-container">
          <div style={{ textAlign: 'center', padding: '100px 0' }}>
            <Title level={3}>无权访问</Title>
          </div>
        </main>
      </FunnyBackground>
    )
  }

  return (
    <FunnyBackground>
      <main className="team-management-container">
      <div className="team-management-header">
        <Title level={2}>团队管理</Title>
        <p className="header-subtitle">管理团队成员和团队配置</p>
      </div>

      <Card className="team-content-card" variant="borderless">
        <div className="empty-state-wrapper">
          <Empty description="功能开发中，敬请期待" />
        </div>
      </Card>
    </main>
  </FunnyBackground>
  )
}

export default TeamManagement
