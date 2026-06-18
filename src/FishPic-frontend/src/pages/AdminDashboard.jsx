import { useEffect, useState, useRef } from 'react'
import { App as AntApp, Typography, Card, Row, Col, Statistic, Spin } from 'antd'
import {
  UserOutlined,
  PictureOutlined,
  AppstoreOutlined,
  RiseOutlined,
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { getSystemStats } from '../api'
import { useAdminGuard } from '../hooks/useAdminGuard'
import { CHART_COLORS } from '../utils/constants'
import './AdminDashboard.css'

const { Title } = Typography

function AdminDashboard() {
  const { message } = AntApp.useApp()
  const { userInfo } = useAdminGuard('system:log:manage')
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState(null)
  const hasFetchedRef = useRef(false)

  useEffect(() => {
    if (hasFetchedRef.current || !userInfo) return
    const fetchStats = async () => {
      setLoading(true)
      try {
        const result = await getSystemStats()
        setStats(result)
        hasFetchedRef.current = true
      } catch (err) {
        message.error(err.message || '获取统计数据失败')
      } finally {
        setLoading(false)
      }
    }
    fetchStats()
  }, [userInfo, message])

  if (!userInfo) {
    return (
      <main className="dashboard-container">
        <div style={{ textAlign: 'center', padding: '100px 0' }}>
          <Title level={3}>无权访问</Title>
        </div>
      </main>
    )
  }

  const overviewOption = stats ? {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['用户', '图片', '空间'],
      axisLabel: { color: 'var(--text-secondary)' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: 'var(--text-secondary)' },
    },
    series: [{
      name: '总量',
      type: 'bar',
      data: [
        stats.totalUsers || 0,
        stats.totalPictures || 0,
        stats.totalSpaces || 0,
      ],
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: CHART_COLORS.primary[0] },
            { offset: 1, color: CHART_COLORS.primary[1] },
          ],
        },
      },
    }],
  } : {}

  const todayOption = stats ? {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['新增用户', '新增图片'],
      axisLabel: { color: 'var(--text-secondary)' },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: 'var(--text-secondary)' },
      minInterval: 1,
    },
    series: [{
      name: '今日新增',
      type: 'bar',
      data: [
        stats.todayNewUsers || 0,
        stats.todayNewPictures || 0,
      ],
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: CHART_COLORS.success[0] },
            { offset: 1, color: CHART_COLORS.success[1] },
          ],
        },
      },
    }],
  } : {}

  return (
    <main className="dashboard-container">
      <div className="dashboard-header">
        <Title level={2}>数据概览</Title>
        <p className="header-subtitle">平台运营数据一览</p>
      </div>

      <Spin spinning={loading}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card variant="borderless" className="stat-card">
              <Statistic title="用户总数" value={stats?.totalUsers || 0} prefix={<UserOutlined />} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card variant="borderless" className="stat-card">
              <Statistic title="图片总数" value={stats?.totalPictures || 0} prefix={<PictureOutlined />} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card variant="borderless" className="stat-card">
              <Statistic title="空间总数" value={stats?.totalSpaces || 0} prefix={<AppstoreOutlined />} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card variant="borderless" className="stat-card stat-card-today">
              <Statistic title="今日新增用户" value={stats?.todayNewUsers || 0} prefix={<RiseOutlined />} valueStyle={{ color: CHART_COLORS.success[0] }} />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Card variant="borderless" className="stat-card stat-card-today">
              <Statistic title="今日新增图片" value={stats?.todayNewPictures || 0} prefix={<RiseOutlined />} valueStyle={{ color: CHART_COLORS.success[0] }} />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={12}>
            <Card variant="borderless" className="chart-card" title="数据总量">
              {stats && <ReactECharts option={overviewOption} style={{ height: 300 }} />}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card variant="borderless" className="chart-card" title="今日新增">
              {stats && <ReactECharts option={todayOption} style={{ height: 300 }} />}
            </Card>
          </Col>
        </Row>
      </Spin>
    </main>
  )
}

export default AdminDashboard
