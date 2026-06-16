import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp, Card, Typography, Button, Input, Select, Space, Row, Col, Image, Tooltip } from 'antd'
import {
  RobotOutlined,
  SendOutlined, SyncOutlined, ExperimentOutlined, ClearOutlined, CloseCircleOutlined,
  RedoOutlined, SaveOutlined, TeamOutlined, DownloadOutlined,
} from '@ant-design/icons'
import { submitAiDraw, savePictureByUrl, downloadAiImage } from '../api'
import { useIsMobile } from '../hooks/useIsMobile'
import { useAiSse } from '../hooks/useAiSse'
import SaveToSpaceModal from '../components/shared/SaveToSpaceModal'
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
  const [saveModalOpen, setSaveModalOpen] = useState(false)

  const [genPrompt, setGenPrompt] = useState('')
  const [genNegative, setGenNegative] = useState('')
  const [genSize, setGenSize] = useState('1:1')
  const [genStyle, setGenStyle] = useState('auto')
  const [genResults, setGenResults] = useState(null)

  // 异步生成状态
  const [genState, setGenState] = useState('idle') // idle | generating | done | failed
  const [genError, setGenError] = useState('')
  const [currentTaskId, setCurrentTaskId] = useState(null)
  const genStateRef = useRef('idle')
  const genParamsRef = useRef(null)

  const setGenStateAndRef = useCallback((s) => {
    setGenState(s)
    genStateRef.current = s
  }, [])

  // SSE 推送（替代轮询）
  const { result: sseResult, error: sseError } = useAiSse(
    genState === 'generating' ? currentTaskId : null
  )

  // 处理 SSE 推送结果
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

  // 恢复中断的任务（导航离开再返回时）
  useEffect(() => {
    const savedTaskId = sessionStorage.getItem('ai_pending_task')
    if (savedTaskId && genState === 'idle') {
      setCurrentTaskId(savedTaskId)
      setGenStateAndRef('generating')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 在提交任务时保存到 sessionStorage，完成后清除
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
      // SSE hook 会自动监听 currentTaskId
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
    setGenStyle('auto')
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
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = `ai-image-${currentTaskId}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(objectUrl)
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

    // idle
    return (
      <div className="ai-result-placeholder">
        <ExperimentOutlined className="ai-result-icon" />
        <Text type="secondary">输入描述并点击生成按钮，AI 将为你创作图片</Text>
      </div>
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
                  placeholder="描述你想要的画面，例如：一只可爱的橘猫坐在窗台上，窗外是樱花树，阳光明媚"
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
                  placeholder="不希望出现的内容，例如：模糊, 低质量, 扭曲"
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
