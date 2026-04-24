import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Result } from 'antd'
import { HomeOutlined, SmileOutlined, FrownOutlined, MehOutlined } from '@ant-design/icons'
import './NotFound.css'

const funnyMessages = [
  { icon: <SmileOutlined />, text: '哎呀，页面被猫藏起来了！', subtext: '猫咪说它只是暂时保管一下~' },
  { icon: <FrownOutlined />, text: '糟糕，页面去火星旅行了！', subtext: '听说它正在和火星人跳舞呢~' },
  { icon: <MehOutlined />, text: '呃...页面好像迷路了！', subtext: '它可能在看地图找不到北~' },
  { icon: <SmileOutlined />, text: '震惊！页面竟然离家出走！', subtext: '别担心，我们已经派出寻人启事了~' },
  { icon: <FrownOutlined />, text: '页面被程序员吃掉了！', subtext: '他说这个页面味道不错...（？？？）' },
]

function NotFound() {
  const navigate = useNavigate()
  const [currentMessage, setCurrentMessage] = useState(null)
  const [emoji, setEmoji] = useState('😵')

  useEffect(() => {
    const randomIndex = Math.floor(Math.random() * funnyMessages.length)
    setCurrentMessage(funnyMessages[randomIndex])

    const emojis = ['😵', '🤯', '😜', '🙃', '', '']
    const randomEmoji = emojis[Math.floor(Math.random() * emojis.length)]
    setEmoji(randomEmoji)
  }, [])

  const handleGoHome = () => {
    navigate('/')
  }

  return (
    <div className="not-found-container">
      <div className="floating-emoji emoji-1">🐱</div>
      <div className="floating-emoji emoji-2">🚀</div>
      <div className="floating-emoji emoji-3">🗺️</div>
      <div className="floating-emoji emoji-4">🍕</div>
      <div className="floating-emoji emoji-5">🎮</div>
      
      <div className="not-found-content">
        <div className="emoji-bounce" style={{ fontSize: '120px' }}>
          {emoji}
        </div>
        
        <h1 className="error-code">404</h1>
        
        {currentMessage && (
          <div className="funny-message">
            <div className="message-icon">{currentMessage.icon}</div>
            <h2 className="message-text">{currentMessage.text}</h2>
            <p className="message-subtext">{currentMessage.subtext}</p>
          </div>
        )}
        
        <div className="action-buttons">
          <Button
            type="primary"
            size="large"
            onClick={handleGoHome}
            icon={<HomeOutlined />}
            className="home-btn"
          >
            返回首页
          </Button>
        </div>
        
        <div className="easter-egg">
          <p>💡 小贴士：其实这个页面比某些功能页面还要好看呢~</p>
        </div>
      </div>
    </div>
  )
}

export default NotFound
