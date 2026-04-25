import { useState, useEffect } from 'react'
import { Card, Typography, Empty } from 'antd'
import './CommunitySquare.css'

const { Title } = Typography

function CommunitySquare() {
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    // 页面加载逻辑
  }, [])

  return (
    <main className="community-square-container">
      <div className="community-square-header">
        <Title level={2}>社区广场</Title>
        <p className="header-subtitle">探索社区精彩内容和分享</p>
      </div>

      <Card className="community-content-card" variant="borderless">
        <div className="empty-state-wrapper">
          <Empty description="功能开发中，敬请期待" />
        </div>
      </Card>
    </main>
  )
}

export default CommunitySquare
