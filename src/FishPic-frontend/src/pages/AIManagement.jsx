import { useEffect, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Empty } from 'antd'
import { AuthContext } from '../context/AuthContext.jsx'
import './AIManagement.css'

const { Title } = Typography

function AIManagement() {
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
      <main className="ai-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  return (
    <main className="ai-management-container">
    <div className="ai-management-header">
      <Title level={2}>AI 管理</Title>
      <p className="header-subtitle">管理 AI 配置和智能服务</p>
    </div>
    <Card variant="borderless">
      <Empty description="功能开发中，敬请期待" />
    </Card>
    </main>
  )
}

export default AIManagement
