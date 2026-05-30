import { useState } from 'react'
import { App as AntApp, Card, Typography, Button, Input, Select, Space, Row, Col, Image } from 'antd'
import {
  RobotOutlined, ThunderboltOutlined,
  SendOutlined, SyncOutlined, ExperimentOutlined, ClearOutlined,
} from '@ant-design/icons'
import { submitAiGenerate } from '../api'
import './AIImageTools.css'

const { Title, Text } = Typography
const { TextArea } = Input

const DRAW_STYLE_OPTIONS = [
  { value: 'auto', label: '自动' },
  { value: 'photography', label: '摄影' },
  { value: 'portrait', label: '人像/肖像' },
  { value: '3d cartoon', label: '3D卡通' },
  { value: 'anime', label: '动漫' },
  { value: 'oil painting', label: '油画' },
  { value: 'watercolor', label: '水彩画' },
  { value: 'sketch', label: '速写/素描' },
  { value: 'chinese painting', label: '中国画/国画' },
  { value: 'flat illustration', label: '扁平化插画' },
]

function AIImageTools() {
  const { message } = AntApp.useApp()

  const [genPrompt, setGenPrompt] = useState('')
  const [genNegative, setGenNegative] = useState('')
  const [genWidth, setGenWidth] = useState(1024)
  const [genHeight, setGenHeight] = useState(1024)
  const [genStyle, setGenStyle] = useState('auto')
  const [genSubmitting, setGenSubmitting] = useState(false)
  const [genResults, setGenResults] = useState(null)

  const handleGenerate = async () => {
    if (!genPrompt.trim()) { message.warning('请输入画面描述'); return }
    setGenSubmitting(true)
    try {
      const url = await submitAiGenerate({
        description: genPrompt.trim(),
        exclusion: genNegative.trim() || undefined,
        width: genWidth,
        height: genHeight,
        style: genStyle,
      }, { timeout: 0 })
      message.success('图片生成成功')
      setGenResults({ url })
    } catch (e) {
      message.error(e.message || '生成失败')
    } finally {
      setGenSubmitting(false)
    }
  }

  return (
    <main className="ai-tools-container">
      <div className="ai-tools-header">
        <Title level={2}>
          <RobotOutlined style={{ marginRight: 8 }} />
          AI 创作工具
        </Title>
        <p className="header-subtitle">使用 AI 生成图片</p>
      </div>
      <Row gutter={[24, 24]}>
        <Col xs={24} md={10}>
          <Card variant="borderless" className="ai-tool-card" title="创作参数">
            <div className="ai-tool-form">
              <div className="ai-form-group">
                <div className="ai-form-label">画面描述 <Text type="danger">*</Text></div>
                <TextArea
                  value={genPrompt}
                  onChange={(e) => setGenPrompt(e.target.value)}
                  placeholder="描述你想要的画面，例如：一只可爱的橘猫坐在窗台上，窗外是樱花树，阳光明媚"
                  rows={4}
                  maxLength={500}
                  showCount
                />
              </div>
              <div className="ai-form-group">
                <div className="ai-form-label">排除内容</div>
                <TextArea
                  value={genNegative}
                  onChange={(e) => setGenNegative(e.target.value)}
                  placeholder="不希望出现的内容，例如：模糊, 低质量, 扭曲"
                  rows={2}
                  maxLength={200}
                  showCount
                />
              </div>
              <Row gutter={16}>
                <Col span={12}>
                  <div className="ai-form-group">
                    <div className="ai-form-label">宽度</div>
                    <Select value={genWidth} onChange={setGenWidth} style={{ width: '100%' }}
                      options={[
                        { value: 512, label: '512px' },
                        { value: 768, label: '768px' },
                        { value: 1024, label: '1024px' },
                        { value: 1280, label: '1280px' },
                      ]}
                    />
                  </div>
                </Col>
                <Col span={12}>
                  <div className="ai-form-group">
                    <div className="ai-form-label">高度</div>
                    <Select value={genHeight} onChange={setGenHeight} style={{ width: '100%' }}
                      options={[
                        { value: 512, label: '512px' },
                        { value: 768, label: '768px' },
                        { value: 1024, label: '1024px' },
                        { value: 1280, label: '1280px' },
                      ]}
                    />
                  </div>
                </Col>
              </Row>
              <div className="ai-form-group">
                <div className="ai-form-label">生成风格</div>
                <Select value={genStyle} onChange={setGenStyle} style={{ width: '100%' }} options={DRAW_STYLE_OPTIONS} />
              </div>
              <Space>
                <Button type="primary" icon={<SendOutlined />} onClick={handleGenerate} loading={genSubmitting} size="large">
                  开始生成
                </Button>
                <Button icon={<ClearOutlined />} onClick={() => { setGenPrompt(''); setGenNegative(''); setGenStyle('auto'); setGenResults(null) }}>
                  清空
                </Button>
              </Space>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={14}>
          <Card variant="borderless" className="ai-tool-card" title="生成结果">
            {genResults ? (
              <div className="ai-result-area">
                {genResults.url && (
                  <div style={{ textAlign: 'center', marginBottom: 16 }}>
                    <Image
                      src={genResults.url}
                      alt="AI 生成图片"
                      style={{ maxWidth: '100%', maxHeight: 500, borderRadius: 8 }}
                    />
                  </div>
                )}
                {genResults.url && (
                  <div style={{ textAlign: 'center', marginTop: 8 }}>
                    <a href={genResults.url} download rel="noopener noreferrer">
                      下载图片
                    </a>
                  </div>
                )}
              </div>
            ) : genSubmitting ? (
              <div className="ai-result-placeholder">
                <SyncOutlined className="ai-result-icon" spin />
                <Text type="secondary">图片生成中，请耐心等待...</Text>
              </div>
            ) : (
              <div className="ai-result-placeholder">
                <ExperimentOutlined className="ai-result-icon" />
                <Text type="secondary">输入描述并点击生成按钮，AI 将为你创作图片</Text>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </main>
  )
}

export default AIImageTools
