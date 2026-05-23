import { useState, useEffect, useContext, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Table, Card, Typography, Button, Tag, Space, Select, Tabs, Statistic, Row, Col, Switch, message as antMsg } from 'antd'
import { ReloadOutlined, RobotOutlined, CheckCircleOutlined, CloseCircleOutlined, SyncOutlined, BarChartOutlined, SettingOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { getAiTasks, getAiStats, getAiConfig, updateAiConfig } from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import { PAGINATION_LOCALE } from '../utils/constants'
import './AIManagement.css'

const { Title } = Typography

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

const TYPE_OPTIONS = [
  { value: undefined, label: '全部类型' },
  { value: 0, label: '自动标注' },
  { value: 1, label: '图片编辑' },
  { value: 2, label: '图片生成' },
  { value: 3, label: '推荐' },
]

const STATUS_OPTIONS = [
  { value: undefined, label: '全部状态' },
  { value: 0, label: '处理中' },
  { value: 1, label: '成功' },
  { value: 2, label: '失败' },
]

function AIManagement() {
  const navigate = useNavigate()
  const { userInfo } = useContext(AuthContext)
  const [activeTab, setActiveTab] = useState('tasks')
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState([])
  const [typeFilter, setTypeFilter] = useState(undefined)
  const [statusFilter, setStatusFilter] = useState(undefined)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const hasFetchedRef = useRef(false)

  const [stats, setStats] = useState(null)
  const [statsLoading, setStatsLoading] = useState(false)

  const [config, setConfig] = useState(null)
  const [configLoading, setConfigLoading] = useState(false)
  const [configSaving, setConfigSaving] = useState(false)

  const fetchTasks = useCallback(async (current, pageSize, type, status) => {
    setLoading(true)
    try {
      const params = { current, pageSize }
      if (type !== undefined && type !== null) params.type = type
      if (status !== undefined && status !== null) params.status = status
      const result = await getAiTasks(params)
      setData(result?.records || [])
      setPagination(prev => ({ ...prev, current, pageSize, total: result?.total || 0 }))
    } catch (error) {
      antMsg.error(error.message || '获取AI任务列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!userInfo || userInfo.role !== 'admin') {
      antMsg.error('无权访问，正在跳转到 404 页面...')
      setTimeout(() => navigate('/404', { replace: true }), 500)
      return
    }
    if (hasFetchedRef.current) return
    hasFetchedRef.current = true
    fetchTasks(1, 20)
  }, [userInfo, navigate, fetchTasks])

  const fetchStats = useCallback(async () => {
    setStatsLoading(true)
    try {
      const result = await getAiStats()
      setStats(result)
    } catch (error) {
      antMsg.error(error.message || '获取统计信息失败')
    } finally {
      setStatsLoading(false)
    }
  }, [])

  const fetchConfig = useCallback(async () => {
    setConfigLoading(true)
    try {
      const result = await getAiConfig()
      setConfig(result)
    } catch (error) {
      antMsg.error(error.message || '获取AI配置失败')
    } finally {
      setConfigLoading(false)
    }
  }, [])

  const handleTabChange = (key) => {
    setActiveTab(key)
    if (key === 'stats') fetchStats()
    if (key === 'config') fetchConfig()
  }

  const handleTableChange = (pag) => {
    fetchTasks(pag.current, pag.pageSize, typeFilter, statusFilter)
  }

  const handleTypeFilterChange = (value) => {
    setTypeFilter(value)
    setPagination(prev => ({ ...prev, current: 1 }))
    fetchTasks(1, pagination.pageSize, value, statusFilter)
  }

  const handleStatusFilterChange = (value) => {
    setStatusFilter(value)
    setPagination(prev => ({ ...prev, current: 1 }))
    fetchTasks(1, pagination.pageSize, typeFilter, value)
  }

  const handleConfigChange = async (key, value) => {
    setConfigSaving(true)
    try {
      await updateAiConfig({ [key]: value })
      setConfig(prev => ({ ...prev, [key]: value }))
      antMsg.success('配置已更新')
    } catch (error) {
      antMsg.error(error.message || '更新配置失败')
    } finally {
      setConfigSaving(false)
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    {
      title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100,
    },
    {
      title: '类型', dataIndex: 'type', key: 'type', width: 100,
      render: (type) => {
        const info = TYPE_MAP[type] || { color: 'default', text: '未知' }
        return <Tag color={info.color}>{info.text}</Tag>
      },
    },
    {
      title: '子类型', dataIndex: 'subType', key: 'subType', width: 120,
      render: (val) => val || '-',
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: (status) => {
        const info = STATUS_MAP[status] || { color: 'default', text: '未知' }
        const icon = status === 0 ? <SyncOutlined spin /> : status === 1 ? <CheckCircleOutlined /> : status === 2 ? <CloseCircleOutlined /> : null
        return <Tag color={info.color} icon={icon}>{info.text}</Tag>
      },
    },
    {
      title: '图片ID', dataIndex: 'pictureId', key: 'pictureId', width: 90,
      render: (val) => val ?? '-',
    },
    {
      title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170,
      render: (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '-',
    },
    {
      title: '错误信息', dataIndex: 'errorMsg', key: 'errorMsg', width: 200, ellipsis: true,
      render: (val) => val ? <span style={{ color: 'var(--text-danger)' }}>{val}</span> : '-',
    },
  ]

  if (!userInfo || userInfo.role !== 'admin') {
    return (
      <main className="ai-management-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  const tabItems = [
    {
      key: 'tasks',
      label: <span><UnorderedListOutlined /> 任务监控</span>,
      children: (
        <Card variant="borderless" className="ai-management-card">
          <div className="admin-comment-toolbar">
            <Space wrap>
              <Select value={typeFilter} onChange={handleTypeFilterChange} style={{ width: 120 }} options={TYPE_OPTIONS} />
              <Select value={statusFilter} onChange={handleStatusFilterChange} style={{ width: 120 }} options={STATUS_OPTIONS} />
              <Button icon={<ReloadOutlined />} onClick={() => fetchTasks(pagination.current, pagination.pageSize, typeFilter, statusFilter)}>刷新</Button>
            </Space>
            <span className="admin-comment-total">共 {pagination.total} 条任务</span>
          </div>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={loading}
            pagination={{ ...pagination, showSizeChanger: true, showQuickJumper: true, showTotal: (total) => `共 ${total} 条`, pageSizeOptions: ['10', '20', '50', '100'], locale: PAGINATION_LOCALE }}
            onChange={handleTableChange}
            scroll={{ x: 1000 }}
          />
        </Card>
      ),
    },
    {
      key: 'stats',
      label: <span><BarChartOutlined /> 使用统计</span>,
      children: (
        <Card variant="borderless" className="ai-management-card" loading={statsLoading}>
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={6}>
              <Card size="small">
                <Statistic title="总任务数" value={stats?.totalTasks || 0} />
              </Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small">
                <Statistic title="成功" value={stats?.successTasks || 0} styles={{ value: { color: '#52c41a' } }} prefix={<CheckCircleOutlined />} />
              </Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small">
                <Statistic title="失败" value={stats?.failedTasks || 0} styles={{ value: { color: '#ff4d4f' } }} prefix={<CloseCircleOutlined />} />
              </Card>
            </Col>
            <Col xs={12} sm={6}>
              <Card size="small">
                <Statistic title="处理中" value={stats?.processingTasks || 0} styles={{ value: { color: '#1890ff' } }} prefix={<SyncOutlined spin />} />
              </Card>
            </Col>
          </Row>
          {stats?.typeCounts && (
            <Card size="small" title="按类型分布" style={{ marginTop: 16 }}>
              <Row gutter={[16, 8]}>
                {Object.entries(stats.typeCounts).map(([type, count]) => (
                  <Col xs={12} sm={6} key={type}>
                    <Statistic title={TYPE_MAP[type]?.text || `类型${type}`} value={count} />
                  </Col>
                ))}
              </Row>
            </Card>
          )}
          <div style={{ marginTop: 16 }}>
            <Button icon={<ReloadOutlined />} onClick={fetchStats}>刷新统计</Button>
          </div>
        </Card>
      ),
    },
    {
      key: 'config',
      label: <span><SettingOutlined /> AI 配置</span>,
      children: (
        <Card variant="borderless" className="ai-management-card" loading={configLoading}>
          {config && (
            <div className="ai-config-list">
              <div className="ai-config-item">
                <div>
                  <div className="ai-config-label">自动标注</div>
                  <div className="ai-config-desc">上传图片后自动触发 AI 标签和描述生成</div>
                </div>
                <Switch checked={config.taggingEnabled} loading={configSaving} onChange={(val) => handleConfigChange('taggingEnabled', val)} />
              </div>
              <div className="ai-config-item">
                <div>
                  <div className="ai-config-label">图片编辑</div>
                  <div className="ai-config-desc">允许用户使用 AI 修图功能（背景移除、风格转换）</div>
                </div>
                <Switch checked={config.editingEnabled} loading={configSaving} onChange={(val) => handleConfigChange('editingEnabled', val)} />
              </div>
              <div className="ai-config-item">
                <div>
                  <div className="ai-config-label">图片生成</div>
                  <div className="ai-config-desc">允许用户使用 AI 文生图功能</div>
                </div>
                <Switch checked={config.generationEnabled} loading={configSaving} onChange={(val) => handleConfigChange('generationEnabled', val)} />
              </div>
              <div className="ai-config-item">
                <div>
                  <div className="ai-config-label">智能推荐</div>
                  <div className="ai-config-desc">允许用户使用 AI 图片推荐功能</div>
                </div>
                <Switch checked={config.recommendationEnabled} loading={configSaving} onChange={(val) => handleConfigChange('recommendationEnabled', val)} />
              </div>
            </div>
          )}
        </Card>
      ),
    },
  ]

  return (
    <main className="ai-management-container">
      <div className="ai-management-header">
        <Title level={2}>
          <RobotOutlined style={{ marginRight: 8 }} />
          AI 管理
        </Title>
        <p className="header-subtitle">管理 AI 配置和智能服务</p>
      </div>
      <Tabs activeKey={activeTab} onChange={handleTabChange} items={tabItems} />
    </main>
  )
}

export default AIManagement
