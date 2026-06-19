import { useState, useEffect, useCallback, useRef, useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Button, Input, Select, Space, Row, Col, Image, Tooltip } from 'antd'
import {
  RobotOutlined,
  SendOutlined, SyncOutlined, ExperimentOutlined, ClearOutlined, CloseCircleOutlined,
  RedoOutlined, SaveOutlined, TeamOutlined, DownloadOutlined,
  CrownOutlined,
} from '@ant-design/icons'
import { submitAiDraw, savePictureByUrl, downloadAiImage } from '../api'
import { downloadFile } from '../utils/file'
import { useIsMobile } from '../hooks/useIsMobile'
import { useAiSse } from '../hooks/useAiSse'
import { AuthContext } from '../context/AuthContext'
import SaveToSpaceModal from '../components/shared/SaveToSpaceModal'
import './AIImageTools.css'

const { Title, Text } = Typography
const { TextArea } = Input

const DRAW_STYLE_OPTIONS = [
  { value: 'photography', label: '摄影' },
  { value: 'anime', label: '动漫' },
  { value: 'oil painting', label: '油画' },
  { value: 'watercolor', label: '水彩' },
  { value: 'sketch', label: '素描' },
  { value: '3d', label: '3D' },
  { value: 'pixel art', label: '像素' },
  { value: 'flat illustration', label: '扁平' },
  { value: 'chinese painting', label: '中国风' },
  { value: 'cyberpunk', label: '赛博朋克' },
]

const DRAW_SIZE_OPTIONS = [
  { value: '1:1', label: '2048×2048 (1:1)' },
  { value: '16:9', label: '2688×1536 (16:9)' },
  { value: '9:16', label: '1536×2688 (9:16)' },
  { value: '4:3', label: '2368×1728 (4:3)' },
  { value: '3:4', label: '1728×2368 (3:4)' },
]

function AIImageTools() {
  const { message } = AntApp.useApp()
  const navigate = useNavigate()
  const isMobile = useIsMobile()
  const { userInfo } = useContext(AuthContext)
  const [saveModalOpen, setSaveModalOpen] = useState(false)

  const userLevel = userInfo?.level ?? 0
  const isVip = userLevel >= 1

  const [genPrompt, setGenPrompt] = useState('')
  const [genNegative, setGenNegative] = useState('')
  const [genSize, setGenSize] = useState('1:1')
  const [genStyle, setGenStyle] = useState('photography')
  const [genResults, setGenResults] = useState(null)

  const [genState, setGenState] = useState('idle')
  const [genError, setGenError] = useState('')
  const [currentTaskId, setCurrentTaskId] = useState(null)
  const genStateRef = useRef('idle')
  const genParamsRef = useRef(null)

  const setGenStateAndRef = useCallback((s) => {
    setGenState(s)
    genStateRef.current = s
  }, [])

  const { result: sseResult, error: sseError } = useAiSse(
    genState === 'generating' ? currentTaskId : null
  )

  useEffect(() => {
    if (sseResult) {
      setGenStateAndRef('done')
      setGenResults({ url: sseResult.result })
      message.success('图片生成成功')
      sessionStorage.removeItem('ai_pending_task')
    }
  }, [sseResult, message, setGenStateAndRef])

  useEffect(() => {
    if (sseError && genState === 'generating') {
      setGenStateAndRef('failed')
      setGenError(sseError)
      message.error(sseError)
      sessionStorage.removeItem('ai_pending_task')
    }
  }, [sseError, genState, message, setGenStateAndRef])

  useEffect(() => {
    const savedTaskId = sessionStorage.getItem('ai_pending_task')
    if (savedTaskId && genState === 'idle') {
      setCurrentTaskId(savedTaskId)
      setGenStateAndRef('generating')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (genState === 'generating' && currentTaskId) {
      sessionStorage.setItem('ai_pending_task', currentTaskId)
    } else if (genState !== 'generating') {
      sessionStorage.removeItem('ai_pending_task')
    }
  }, [genState, currentTaskId])

  const doSubmit = useCallback(async (params) => {
    try {
      const result = await submitAiDraw(params)
      setCurrentTaskId(result.taskId)
    } catch (e) {
      setGenStateAndRef('failed')
      setGenError(e.message || '提交生成任务失败')
      message.error(e.message || '提交生成任务失败')
    }
  }, [message, setGenStateAndRef])

  const handleGenerate = async () => {
    if (!genPrompt.trim()) { message.warning('请输入画面描述'); return }

    setGenStateAndRef('generating')
    setGenError('')
    setGenResults(null)
    setCurrentTaskId(null)

    const params = {
      description: genPrompt.trim(),
      exclusion: genNegative.trim() || undefined,
      size: genSize,
      style: genStyle,
    }
    genParamsRef.current = params

    doSubmit(params)
  }

  const handleRegenerate = () => {
    if (!genParamsRef.current || genStateRef.current === 'generating') return

    setGenStateAndRef('generating')
    setGenError('')
    setGenResults(null)
    setCurrentTaskId(null)

    doSubmit(genParamsRef.current)
  }

  const handleClear = () => {
    setGenPrompt('')
    setGenNegative('')
    setGenStyle('photography')
    setGenSize('1:1')
    genParamsRef.current = null
  }

  const handleSaveToPrivate = async () => {
    if (!genResults?.url) return
    try {
      await savePictureByUrl(genResults.url, null)
      message.success('已保存到私人空间')
    } catch (e) {
      message.error(e.message || '保存失败')
    }
  }

  const handleSaveToSpace = () => {
    if (!genResults?.url) return
    if (isMobile) {
      navigate('/mobile/save-to-space', { state: { imageUrl: genResults.url } })
    } else {
      setSaveModalOpen(true)
    }
  }

  const handleDownload = async () => {
    if (!currentTaskId) {
      message.warning('当前图片暂不可下载，请重新生成后再试')
      return
    }

    try {
      const blob = await downloadAiImage(currentTaskId)
      if (!(blob instanceof Blob)) {
        throw new Error('下载响应无效')
      }
      const objectUrl = window.URL.createObjectURL(blob)
      downloadFile(objectUrl, `ai-image-${currentTaskId}.png`)
    } catch (error) {
      message.error(error.message || '下载失败')
    }
  }

  const renderResultArea = () => {
    if (genState === 'generating') {
      return (
        <div className="ai-result-placeholder">
          <SyncOutlined className="ai-result-icon" spin />
          <Text type="secondary">AI 正在创作，完成后将自动推送...</Text>
          {currentTaskId && (
            <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
              任务编号: {currentTaskId}
            </Text>
          )}
        </div>
      )
    }

    if (genState === 'failed') {
      return (
        <div className="ai-result-placeholder" style={{ color: '#ff4d4f' }}>
          <CloseCircleOutlined className="ai-result-icon" />
          <Text type="danger">{genError}</Text>
        </div>
      )
    }

    if (genState === 'done' && genResults?.url) {
      return (
        <div className="ai-result-area">
          <div className="ai-result-image-wrap">
            <Image
              src={genResults.url}
              alt="AI 生成图片"
              style={{ width: '100%', height: '100%', objectFit: 'contain' }}
              preview={{ cover: false }}
            />
          </div>
          <div className="ai-result-toolbar">
            <Tooltip title="使用提交时的参数重新生成">
              <Button icon={<RedoOutlined />} onClick={handleRegenerate} disabled={genState === 'generating'}>
                重新生成
              </Button>
            </Tooltip>
            <Button icon={<DownloadOutlined />} onClick={handleDownload}>
              下载图片
            </Button>
            <Button icon={<SaveOutlined />} onClick={handleSaveToPrivate}>
              保存到私人空间
            </Button>
            <Button icon={<TeamOutlined />} onClick={handleSaveToSpace}>
              保存到空间
            </Button>
          </div>
        </div>
      )
    }

    return (
      <div className="ai-result-placeholder">
        <ExperimentOutlined className="ai-result-icon" />
        <Text type="secondary">输入描述并点击生成，AI 将为你创作图片</Text>
      </div>
    )
  }

  if (!isVip) {
    return (
      <main className="ai-tools-container">
        <div className="ai-tools-header">
          <Title level={2}>
            <RobotOutlined style={{ marginRight: 8 }} />
            AI 创作工具
          </Title>
        </div>
        <Card variant="borderless" className="ai-tool-card" style={{ textAlign: 'center', padding: '60px 24px' }}>
          <CrownOutlined style={{ fontSize: 48, color: '#faad14', marginBottom: 16 }} />
          <Title level={3}>升级 VIP 解锁 AI 创作</Title>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 24 }}>
            AI 生图和 AI 标注功能仅对 VIP 及以上用户开放
          </p>
          <Space orientation="vertical" size="small" style={{ marginBottom: 24 }}>
            <p><strong>VIP：</strong>AI 生图 50次/月 · AI 标注 1000次/月</p>
            <p><strong>SVIP：</strong>AI 生图 200次/月 · AI 标注 5000次/月</p>
          </Space>
          <Button type="primary" size="large" onClick={() => navigate('/profile')}>
            查看会员权益
          </Button>
        </Card>
      </main>
    )
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
                  placeholder="描述你想要的画面，例如：一只可爱的橘猫坐在窗台上，窗外是樱花树，阳光明亮"
                  rows={4}
                  maxLength={500}
                  showCount
                  disabled={genState === 'generating'}
                />
              </div>
              <div className="ai-form-group">
                <div className="ai-form-label">排除内容</div>
                <TextArea
                  value={genNegative}
                  onChange={(e) => setGenNegative(e.target.value)}
                  placeholder="不希望出现的内容，例如：模糊、低质量、扭曲"
                  rows={2}
                  maxLength={200}
                  showCount
                  disabled={genState === 'generating'}
                />
              </div>
              <div className="ai-form-group">
                <div className="ai-form-label">图片尺寸</div>
                <Select value={genSize} onChange={setGenSize} style={{ width: '100%' }}
                  options={DRAW_SIZE_OPTIONS}
                  disabled={genState === 'generating'}
                />
              </div>
              <div className="ai-form-group">
                <div className="ai-form-label">生成风格</div>
                <Select value={genStyle} onChange={setGenStyle} style={{ width: '100%' }} options={DRAW_STYLE_OPTIONS} disabled={genState === 'generating'} />
              </div>
              <Space>
                <Button type="primary" icon={<SendOutlined />} onClick={handleGenerate} loading={genState === 'generating'} size="large">
                  {genState === 'generating' ? '生成中...' : '开始生成'}
                </Button>
                <Button icon={<ClearOutlined />} onClick={handleClear} disabled={genState === 'generating'}>
                  清空
                </Button>
              </Space>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={14}>
          <Card variant="borderless" className="ai-tool-card ai-result-card" title="生成结果">
            {renderResultArea()}
          </Card>
        </Col>
      </Row>
      <SaveToSpaceModal
        open={saveModalOpen}
        onClose={() => setSaveModalOpen(false)}
        imageUrl={genResults?.url}
      />
    </main>
  )
}

export default AIImageTools
