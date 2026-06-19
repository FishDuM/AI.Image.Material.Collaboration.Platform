import { useNavigate } from 'react-router-dom'
import { Button, Result } from 'antd'
import { HomeOutlined } from '@ant-design/icons'
import './NotFound.css'

function NotFound() {
  const navigate = useNavigate()

  return (
    <div className="not-found-container">
      <div className="not-found-content">
        <Result
          status="404"
          title="404"
          subTitle="抱歉，您访问的页面不存在"
          extra={
            <Button
              type="primary"
              size="large"
              onClick={() => navigate('/')}
              icon={<HomeOutlined />}
            >
              返回首页
            </Button>
          }
        />
      </div>
    </div>
  )
}

export default NotFound
