import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Spin, Result, Button, Typography, Space, Tag } from 'antd'
import { DownloadOutlined, ClockCircleOutlined, PictureOutlined } from '@ant-design/icons'
import { getShareInfo } from '../api'
import './SharePage.css'

const { Title, Text } = Typography

function SharePage() {
  const { token } = useParams()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [shareData, setShareData] = useState(null)

  useEffect(() => {
    if (!token) {
      setError('无效的分享链接')
      setLoading(false)
      return
    }

    getShareInfo(token)
      .then((data) => {
        setShareData(data)
      })
      .catch((err) => {
        setError(err.message || '分享链接无效或已过期')
      })
      .finally(() => setLoading(false))
  }, [token])

  if (loading) {
    return (
      <div className="share-page-loading">
        <Spin size="large" />
      </div>
    )
  }

  if (error || !shareData) {
    return (
      <div className="share-page-error">
        <Result
          status="warning"
          title="无法访问"
          subTitle={error || '分享链接无效或已过期'}
          extra={
            <Button type="primary" onClick={() => { window.location.href = '/' }}>
              返回首页
            </Button>
          }
        />
      </div>
    )
  }

  const handleDownload = () => {
    if (!shareData.downloadUrl) return
    const link = document.createElement('a')
    link.href = shareData.downloadUrl
    link.download = shareData.pictureName || 'image'
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const expireDate = shareData.expireTime ? new Date(shareData.expireTime).toLocaleString('zh-CN') : ''

  return (
    <div className="share-page">
      <div className="share-page-card">
        <div className="share-page-image-wrapper">
          <img
            src={shareData.previewUrl}
            alt={shareData.pictureName || '分享图片'}
            className="share-page-image"
          />
        </div>
        <div className="share-page-info">
          <Title level={4} className="share-page-title">
            <PictureOutlined /> {shareData.pictureName || '未命名图片'}
          </Title>
          {shareData.introduction && (
            <Text className="share-page-desc">{shareData.introduction}</Text>
          )}
          <div className="share-page-meta">
            <Space size="middle">
              {shareData.width && shareData.height && (
                <Tag>{shareData.width} x {shareData.height}</Tag>
              )}
              <Tag icon={<ClockCircleOutlined />} color="orange">
                {expireDate} 过期
              </Tag>
              <Tag color={shareData.allowDownload === 1 ? 'green' : 'blue'}>
                {shareData.allowDownload === 1 ? '允许下载' : '仅预览'}
              </Tag>
            </Space>
          </div>
          {shareData.allowDownload === 1 && shareData.downloadUrl && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              size="large"
              className="share-page-download-btn"
              onClick={handleDownload}
            >
              下载原图
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

export default SharePage
