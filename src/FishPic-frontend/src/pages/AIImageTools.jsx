import { useState, useEffect, useCallback, useRef } from 'react'
import { App as AntApp, Card, Typography, Button, Input, Select, Slider, Table, Tag, Space, Tabs, Row, Col, Empty, Image } from 'antd'
import {
  RobotOutlined, ThunderboltOutlined, ScissorOutlined, UnorderedListOutlined,
  ReloadOutlined, SendOutlined, CheckCircleOutlined, CloseCircleOutlined,
  SyncOutlined, PictureOutlined, ExperimentOutlined, ClearOutlined,
} from '@ant-design/icons'
import { getMyAiTasks, submitAiGenerate, submitAiEdit, getPictureList } from '../api'
import { PAGINATION_LOCALE } from '../utils/constants'
import './AIImageTools.css'

const { Title, Text, Paragraph } = Typography
const { TextArea } = Input

const TYPE_MAP = {
  0: { color: 'blue', text: '自动标注' },
  1: { color: 'purple', text: '图片编辑' },
  2: { color: 'cyan', text: '图片生成' },
  3: { color: 'geekblue', text: '推荐' },
}

const STATUS_MAP = {
  0: { color: 'processing', text: '处理中' },
  1: { color: 'success', text: '成功' },
  2: { color: 'error', text: '失败' },
}

const EDIT_TYPES = [
  { value: 'background_removal', label: '背景移除' },
  { value: 'style_transfer', label: '风格迁移' },
]

const STYLE_OPTIONS = [
  { value: 'anime', label: '动漫风' },
  { value: 'oil_painting', label: '油画风' },
  { value: 'watercolor', label: '水彩风' },
  { value: 'sketch', label: '素描风' },
  { value: '3d', label: '3D 渲染' },
]

function AIImageTools() {
  const { message } = AntApp.useApp()
  const [activeTab, setActiveTab] = useState('generate')

  // ---- Generation State ----
  const [genPrompt, setGenPrompt] = useState('')
  const [genNegative, setGenNegative] = useState('')
  const [genWidth, setGenWidth] = useState(1024)
  const [genHeight, setGenHeight] = useState(1024)
  const [genNum, setGenNum] = useState(1)
  const [genSubmitting, setGenSubmitting] = useState(false)
  const [genResults, setGenResults] = useState(null)

  // ---- Editing State ----
  const [editImageUrl, setEditImageUrl] = useState('')
  const [editType, setEditType] = useState('background_removal')
  const [editStyle, setEditStyle] = useState('anime')
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editResults, setEditResults] = useState(null)
  const [userPictures, setUserPictures] = useState([])
  const [picturesLoading, setPicturesLoading] = useState(false)

  // ---- Tasks State ----
  const [tasks, setTasks] = useState([])
  const [tasksLoading, setTasksLoading] = useState(false)
  const [taskPagination, setTaskPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const hasFetchedRef = useRef(false)

  const fetchTasks = useCallback(async (current, pageSize) => {
    setTasksLoading(true)
    try {
      const result = await getMyAiTasks({ current, pageSize })
      setTasks(result?.records || [])
      setTaskPagination(prev => ({ ...prev, current, pageSize, total: result?.total || 0 }))
    } catch {
      // ignore
    } finally {
      setTasksLoading(false)
    }
  }, [])

  useEffect(() => {
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchTasks(1, 20)
  }, [fetchTasks])

  const fetchUserPictures = useCallback(async () => {
    setPicturesLoading(true)
    try {
      const result = await getPictureList(1, 50)
      setUserPictures(result?.records || [])
    } catch {
      // ignore
    } finally {
      setPicturesLoading(false)
    }
  }, [])

  // ---- Generation ----
  const handleGenerate = async () => {
    if (!genPrompt.trim()) { message.warning('请输入画面描述'); return }
    setGenSubmitting(true)
    try {
      const taskId = await submitAiGenerate({
        prompt: genPrompt.trim(),
        negativePrompt: genNegative.trim() || undefined,
        width: genWidth,
        height: genHeight,
        numImages: genNum,
      })
      message.success(`已提交生图任务 #${taskId}`)
      setGenResults({ taskId })
      fetchTasks(1, taskPagination.pageSize)
    } catch (e) {
      message.error(e.message || '提交失败')
    } finally {
      setGenSubmitting(false)
    }
  }

  // ---- Editing ----
  const handleEdit = async () => {
    if (!editImageUrl.trim()) { message.warning('请选择或输入图片地址'); return }
    setEditSubmitting(true)
    try {
      const taskId = await submitAiEdit({
        imageUrl: editImageUrl.trim(),
        editType,
        options: editType === 'style_transfer' ? { style: editStyle } : {},
      })
      message.success(`已提交修图任务 #${taskId}`)
      setEditResults({ taskId })
      fetchTasks(1, taskPagination.pageSize)
    } catch (e) {
      message.error(e.message || '提交失败')
    } finally {
      setEditSubmitting(false)
    }
  }

  const handleSelectPicture = (url) => {
    setEditImageUrl(url)
  }

  // ---- Task Table Columns ----
  const taskColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '类型', dataIndex: 'type', key: 'type', width: 100,
      render: (type) => {
        const info = TYPE_MAP[type] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (status) => {
        const info = STATUS_MAP[status] || { color: 'default', text: '未知' }
        const icon = status === 0 ? <SyncOutlined spin /> : status === 1 ? <CheckCircleOutlined /> : status === 2 ? <CloseCircleOutlined /> : null
        return <Tag color={info.color} icon={icon}>{info.text}</Tag>
      },
    },
    {
      title: '图片ID', dataIndex: 'pictureId', key: 'pictureId', width: 80,
      render: (val) => val ?? '-',
    },
    {
      title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170,
      render: (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '-',
    },
    {
      title: '错误信息', dataIndex: 'errorMsg', key: 'errorMsg', ellipsis: true,
      render: (val) => val ? <Text type="danger">{val}</Text> : '-',
    },
  ]

  const tabItems = [
    // ---- 图片生成 ----
    {
      key: 'generate',
      label: <span><ThunderboltOutlined /> 图片生成</span>,
      children: (
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
                  <div className="ai-form-label">生成数量: {genNum}</div>
                  <Slider min={1} max={4} value={genNum} onChange={setGenNum} marks={{ 1: '1', 2: '2', 3: '3', 4: '4' }} />
                </div>
                <Space>
                  <Button type="primary" icon={<SendOutlined />} onClick={handleGenerate} loading={genSubmitting} size="large">
                    开始生成
                  </Button>
                  <Button icon={<ClearOutlined />} onClick={() => { setGenPrompt(''); setGenNegative(''); setGenResults(null) }}>
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
                  <div className="ai-result-task-id">
                    <CheckCircleOutlined style={{ color: 'var(--success)' }} />
                    <span>任务已提交，编号: <Text code>{genResults.taskId}</Text></span>
                  </div>
                  <Paragraph type="secondary">
                    图片正在生成中，可在「我的任务」标签页查看进度。
                  </Paragraph>
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
      ),
    },
    // ---- 图片编辑 ----
    {
      key: 'edit',
      label: <span><ScissorOutlined /> 图片编辑</span>,
      children: (
        <Row gutter={[24, 24]}>
          <Col xs={24} md={10}>
            <Card variant="borderless" className="ai-tool-card" title="编辑参数">
              <div className="ai-tool-form">
                <div className="ai-form-group">
                  <div className="ai-form-label">编辑类型</div>
                  <Select value={editType} onChange={(v) => setEditType(v)} style={{ width: '100%' }} options={EDIT_TYPES} />
                </div>
                {editType === 'style_transfer' && (
                  <div className="ai-form-group">
                    <div className="ai-form-label">目标风格</div>
                    <Select value={editStyle} onChange={(v) => setEditStyle(v)} style={{ width: '100%' }} options={STYLE_OPTIONS} />
                  </div>
                )}
                <div className="ai-form-group">
                  <div className="ai-form-label">图片地址 <Text type="danger">*</Text></div>
                  <TextArea
                    value={editImageUrl}
                    onChange={(e) => setEditImageUrl(e.target.value)}
                    placeholder="输入图片 URL，或从下方「我的图片」中选择"
                    rows={2}
                  />
                  {editImageUrl && (
                    <div style={{ marginTop: 8 }}>
                      <Image src={editImageUrl} alt="待编辑图片" style={{ maxHeight: 160, borderRadius: 8 }} fallback="data:image/png;base64,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" />
                    </div>
                  )}
                </div>
                <Space>
                  <Button type="primary" icon={<SendOutlined />} onClick={handleEdit} loading={editSubmitting} size="large">
                    开始编辑
                  </Button>
                  <Button icon={<ClearOutlined />} onClick={() => { setEditImageUrl(''); setEditResults(null) }}>
                    清空
                  </Button>
                </Space>
              </div>
            </Card>
          </Col>
          <Col xs={24} md={14}>
            <Card variant="borderless" className="ai-tool-card" title={<span><PictureOutlined /> 我的图片 <Button type="link" size="small" icon={<ReloadOutlined />} onClick={fetchUserPictures} loading={picturesLoading}>刷新</Button></span>}>
              {userPictures.length === 0 ? (
                <Empty description="暂无图片，上传图片后可使用 AI 编辑" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <div className="ai-picture-grid">
                  {userPictures.map((pic) => (
                    <div
                      key={pic.id}
                      className={`ai-picture-item${editImageUrl === pic.url ? ' selected' : ''}`}
                      onClick={() => handleSelectPicture(pic.url)}
                    >
                      <Image src={pic.url} alt={`图片 ${pic.id}`} preview={false} style={{ width: '100%', height: 120, objectFit: 'cover', borderRadius: 6 }} />
                      {pic.tags && pic.tags.length > 0 && (
                        <div className="ai-picture-tags">
                          {pic.tags.slice(0, 3).map((tag, i) => <Tag key={i} color="blue" style={{ fontSize: 10, margin: '1px' }}>{tag}</Tag>)}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
              {editResults && (
                <div className="ai-result-area" style={{ marginTop: 16 }}>
                  <div className="ai-result-task-id">
                    <CheckCircleOutlined style={{ color: 'var(--success)' }} />
                    <span>任务已提交，编号: <Text code>{editResults.taskId}</Text></span>
                  </div>
                  <Paragraph type="secondary">图片正在编辑中，可在「我的任务」标签页查看进度。</Paragraph>
                </div>
              )}
            </Card>
          </Col>
        </Row>
      ),
    },
    // ---- 我的任务 ----
    {
      key: 'tasks',
      label: <span><UnorderedListOutlined /> 我的任务</span>,
      children: (
        <Card variant="borderless" className="ai-tool-card">
          <div className="ai-tasks-toolbar">
            <Button icon={<ReloadOutlined />} onClick={() => fetchTasks(taskPagination.current, taskPagination.pageSize)}>刷新</Button>
            <Text type="secondary">共 {taskPagination.total} 条</Text>
          </div>
          <Table
            rowKey="id"
            columns={taskColumns}
            dataSource={tasks}
            loading={tasksLoading}
            pagination={{ ...taskPagination, showSizeChanger: true, showQuickJumper: true, showTotal: (total) => `共 ${total} 条`, pageSizeOptions: ['10', '20', '50'], locale: PAGINATION_LOCALE }}
            onChange={(pag) => fetchTasks(pag.current, pag.pageSize)}
            scroll={{ x: 700 }}
            locale={{ emptyText: <Empty description="暂无 AI 任务" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
          />
        </Card>
      ),
    },
  ]

  return (
    <main className="ai-tools-container">
      <div className="ai-tools-header">
        <Title level={2}>
          <RobotOutlined style={{ marginRight: 8 }} />
          AI 创作工具
        </Title>
        <p className="header-subtitle">使用 AI 生成、编辑图片</p>
      </div>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} size="large" />
    </main>
  )
}

export default AIImageTools
