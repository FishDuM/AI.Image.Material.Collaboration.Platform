import { useEffect } from 'react'
import { Card, Typography, Empty } from 'antd'
import './PrivateSpace.css'

const { Title } = Typography

function PrivateSpace() {
  useEffect(() => {
    // 页面加载逻辑
  }, [])

  return (
    <main className="private-space-container">
      <div className="private-space-header">
        <Title level={2}>私人空间</Title>
        <p className="header-subtitle">您的个人私密空间</p>
      </div>

      <Card className="private-content-card" variant="borderless">
        <div className="empty-state-wrapper">
          <Empty description="功能开发中，敬请期待" />
        </div>
      </Card>
    </main>
  )
}

export default PrivateSpace
