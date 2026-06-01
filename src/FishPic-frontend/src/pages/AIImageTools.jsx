import { useState, useEffect, useCallback, useRef } from 'react'
import { App as AntApp, Card, Typography, Button, Input, Select, Space, Row, Col, Image, Tooltip } from 'antd'
import {
  RobotOutlined,
  SendOutlined, SyncOutlined, ExperimentOutlined, ClearOutlined, CloseCircleOutlined,
  RedoOutlined, SaveOutlined, TeamOutlined,
} from '@ant-design/icons'
import { submitAiDraw, getAiDrawResult, savePictureByUrl } from '../api'
import { onMessage, offMessage, getConnectionStatus } from '../hooks/useWebSocket'
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

  const [genPrompt, setGenPrompt] = useState('')
  const [genNegative, setGenNegative] = useState('')
  const [genSize, setGenSize] = useState('1:1')
  const [genStyle, setGenStyle] = useState('auto')
  const [genResults, setGenResults] = useState(null)

  // 异步生成状态
  const [genState, setGenState] = useState('idle') // idle | generating | done | failed
  const [genError, setGenError] = useState('')
  const [currentTaskId, setCurrentTaskId] = useState(null)
  const [wsConnected, setWsConnected] = useState(false)
  const genStateRef = useRef('idle')
  const pollTimerRef = useRef(null)
  const genParamsRef = useRef(null)

  // 监听 WebSocket 连接状态
  useEffect(() => {
    const onWsEvent = (data) => {
      if (data.type === '__WS_OPEN__') setWsConnected(true)
      if (data.type === '__WS_CLOSE__') setWsConnected(false)
    }
    const unsub = onMessage(onWsEvent)
    setWsConnected(getConnectionStatus() === 'OPEN')
    return unsub
  }, [])

  // 监听 WebSocket 消息
  useEffect(() => {
    const handleMessage = (data) => {
      if (!data.type || !currentTaskId) return
      if (data.taskId !== currentTaskId) return

      if (data.type === 'TASK_DONE') {
        setGenStateAndRef('done')
        setGenResults({ url: data.result })
        message.success('图片生成成功')
        stopPolling()
      } else if (data.type === 'TASK_FAILED') {
        setGenStateAndRef('failed')
        setGenError(data.errorMsg || '生成失败')
        message.error(data.errorMsg || '图片生成失败')
        stopPolling()
      }
    }

    const unsub = onMessage(handleMessage)
    return () => {
      unsub()
      offMessage(handleMessage)
    }
  }, [currentTaskId, message])

  const setGenStateAndRef = (s) => {
    setGenState(s)
    genStateRef.current = s
  }
  useEffect(() => {
    return () => stopPolling()
  }, [])

  // 轮询兜底
  const startPolling = useCallback((taskId) => {
    stopPolling()
    pollTimerRef.current = setInterval(async () => {
      try {
        const task = await getAiDrawResult(taskId)
        if (task.status === 'DONE') {
          setGenStateAndRef('done')
          setGenResults({ url: task.result })
          message.success('图片生成成功')
          stopPolling()
        } else if (task.status === 'FAILED') {
          setGenStateAndRef('failed')
          setGenError(task.errorMsg || '生成失败')
          message.error(task.errorMsg || '图片生成失败')
          stopPolling()
        }
      } catch {
        // 轮询失败继续重试
      }
    }, 3000)
  }, [message])

  const stopPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }

  const doSubmit = useCallback(async (params) => {
    try {
      const result = await submitAiDraw(params)
      const tId = result.taskId
      setCurrentTaskId(tId)

      if (wsConnected) {
        setTimeout(() => {
          if (genStateRef.current === 'generating') {
            startPolling(tId)
          }
        }, 120000)
      } else {
        startPolling(tId)
      }
    } catch (e) {
      setGenStateAndRef('failed')
      setGenError(e.message || '提交生成任务失败')
      message.error(e.message || '提交生成任务失败')
    }
  }, [wsConnected, message])

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

  const handleSaveToTeam = () => {
    if (!genResults?.url) return
    message.info('团队空间功能待实现')
  }

  const renderResultArea = () => {
    if (genState === 'generating') {
      return (
        <div className="ai-result-placeholder">
          <SyncOutlined className="ai-result-icon" spin />
          <Text type="secondary">AI 正在创作，完成后将自动展示...</Text>
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
            <Button icon={<SaveOutlined />} onClick={handleSaveToPrivate}>
              保存到私人空间
            </Button>
            <Button icon={<TeamOutlined />} onClick={handleSaveToTeam}>
              保存到团队空间
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
    </main>
  )
}

export default AIImageTools
