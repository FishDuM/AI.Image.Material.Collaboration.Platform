import { useState } from 'react'
import { Tabs, Button, message } from 'antd'
import './Notifications.css'

function Notifications() {
  const [activeTab, setActiveTab] = useState('comments')

  const handleMarkAllAsRead = () => {
    message.success('已将所有消息标记为已读')
  }

  const items = [
    {
      key: 'comments',
      label: '评论互动',
      children: (
        <div className="tab-content">
          <div className="empty-state">
            <p>暂无评论互动</p>
          </div>
        </div>
      ),
    },
    {
      key: 'likes',
      label: '赞和收藏',
      children: (
        <div className="tab-content">
          <div className="empty-state">
            <p>暂无赞和收藏</p>
          </div>
        </div>
      ),
    },
    {
      key: 'followers',
      label: '新增关注',
      children: (
        <div className="tab-content">
          <div className="empty-state">
            <p>暂无新增关注</p>
          </div>
        </div>
      ),
    },
    {
      key: 'system',
      label: '系统通知',
      children: (
        <div className="tab-content">
          <div className="empty-state">
            <p>暂无系统通知</p>
          </div>
        </div>
      ),
    },
    {
      key: 'messages',
      label: '私信',
      children: (
        <div className="tab-content">
          <div className="empty-state">
            <p>暂无私信</p>
          </div>
        </div>
      ),
    },
  ]

  return (
    <div className="notifications-page">
      <div className="notifications-content">
        <div className="tabs-header">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={items}
            className="notifications-tabs"
            centered
          />
          <Button 
            type="primary" 
            size="small"
            onClick={handleMarkAllAsRead}
            className="mark-all-read-btn"
          >
            一键已读
          </Button>
        </div>
      </div>
    </div>
  )
}

export default Notifications
