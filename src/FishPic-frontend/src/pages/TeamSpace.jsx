import { useEffect } from 'react'
import { Card, Typography, Empty } from 'antd'
import './TeamSpace.css'

const { Title } = Typography

function TeamSpace() {
  useEffect(() => {
    // 页面加载逻辑
  }, [])

  return (
    <main className="team-space-container">
      <div className="team-space-header">
        <Title level={2}>团队空间</Title>
        <p className="header-subtitle">团队协作和共享空间</p>
      </div>

      <Card className="team-content-card" variant="borderless">
        <div className="empty-state-wrapper">
          <Empty description="功能开发中，敬请期待" />
        </div>
      </Card>
    </main>
  )
}

export default TeamSpace
