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
    const abortController = new AbortController()
    getShareInfo(token, { signal: abortController.signal })
      .then((data) => {
        if (abortController.signal.aborted) return
        if (data?.expireTime && new Date(data.expireTime) < new Date()) {
          setError('分享链接已过期')
          return
        }
        setShareData(data)
      })
      .catch((err) => {
        if (abortController.signal.aborted) return
        if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
        setError(err.message || '分享链接无效或已过期')
      })
      .finally(() => {
        if (!abortController.signal.aborted) setLoading(false)
      })
    return () => abortController.abort()
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

  const handleDownload = (url, name) => {
    if (!url) return
    const link = document.createElement('a')
    link.href = url
    link.download = name || 'image'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const expireDate = shareData.expireTime ? new Date(shareData.expireTime).toLocaleString('zh-CN') : ''
  const pictures = shareData.pictures || []
  const isMulti = pictures.length > 1

  return (
    <div className="share-page">
      <div className="share-page-card">
        <div className="share-page-header">
          <Title level={4} className="share-page-title">
            <PictureOutlined /> 图片分享
          </Title>
          <Space size="middle" className="share-page-meta">
            <Tag icon={<ClockCircleOutlined />} color="orange">
              {expireDate} 过期
            </Tag>
            <Tag color={shareData.allowDownload === 1 ? 'green' : 'blue'}>
              {shareData.allowDownload === 1 ? '允许下载' : '仅预览'}
            </Tag>
            <Tag>{pictures.length} 张图片</Tag>
          </Space>
        </div>

        <div className={`share-page-grid ${isMulti ? 'multi' : 'single'}`}>
          {pictures.map((pic) => (
            <div key={pic.pictureId} className="share-page-grid-item">
              <div className="share-page-grid-image-wrapper">
                <img
                  src={pic.previewUrl}
                  alt={pic.pictureName || '分享图片'}
                  className="share-page-grid-image"
                />
              </div>
              <div className="share-page-grid-info">
                <Text className="share-page-grid-name" ellipsis>
                  {pic.pictureName || '未命名图片'}
                </Text>
                {pic.introduction && (
                  <Text className="share-page-grid-desc" ellipsis>
                    {pic.introduction}
                  </Text>
                )}
                <div className="share-page-grid-meta-row">
                  {pic.width && pic.height && (
                    <Tag>{pic.width} x {pic.height}</Tag>
                  )}
                  {shareData.allowDownload === 1 && pic.downloadUrl && (
                    <Button
                      type="primary"
                      size="small"
                      icon={<DownloadOutlined />}
                      onClick={() => handleDownload(pic.downloadUrl, pic.pictureName)}
                    >
                      下载
                    </Button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default SharePage
