import { useState, useEffect, useCallback } from 'react'
import { App as AntApp, Card, Typography, Button, Empty, Modal, Form, Input, Spin, Row, Col, Tag } from 'antd'
import { LockOutlined } from '@ant-design/icons'
import { createSpace, listSpace } from '../api'
import './PrivateSpace.css'

const { Title, Text } = Typography

const STORAGE_OPTIONS = [
  { value: '10GB', label: '10 GB' },
  { value: '50GB', label: '50 GB' },
  { value: '100GB', label: '100 GB' },
  { value: '250GB', label: '250 GB' },
]

function PrivateSpace() {
  const { message } = AntApp.useApp()
  const [spaces, setSpaces] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [form] = Form.useForm()

  const fetchSpaces = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
      setShowCreate(list.length === 0)
    } catch {
      setSpaces([])
      setShowCreate(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSpaces()
  }, [fetchSpaces])

  const handleCreate = async (values) => {
    setCreateLoading(true)
    try {
      await createSpace({
        name: values.name,
        type: 0,
        introduction: values.introduction || '',
      })
      message.success('私人空间创建成功')
      form.resetFields()
      fetchSpaces()
    } catch (error) {
      message.error(error.message || '创建失败')
    } finally {
      setCreateLoading(false)
    }
  }

  return (
    <main className="private-space-container">
      <div className="private-space-header">
        <div className="private-space-header-left">
          <Title level={2}>
            私人空间{spaces.length > 0 && ` - ${spaces[0].name}`}
          </Title>
          <p className="header-subtitle">
            {spaces.length > 0 && spaces[0].introduction ? spaces[0].introduction : '你的专属私密存储空间'}
          </p>
        </div>
      </div>

      <Spin spinning={loading}>
        {!loading && (
          <>
            {spaces.length > 0 && (
              <Row gutter={[16, 16]}>
                {spaces.map((space) => (
                  <Col key={space.id} xs={24} sm={12} lg={8}>
                    <Card className="private-space-card" variant="borderless">
                      <div className="space-card-header">
                        <LockOutlined className="space-card-icon" />
                        <Text strong className="space-card-name">{space.name}</Text>
                      </div>
                      {space.introduction && (
                        <div className="space-card-intro">
                          <Text type="secondary">{space.introduction}</Text>
                        </div>
                      )}
                      <div className="space-card-meta">
                        <Text type="secondary">
                          创建于 {new Date(space.createTime).toLocaleString('zh-CN', {
                            year: 'numeric', month: '2-digit', day: '2-digit',
                            hour: '2-digit', minute: '2-digit', second: '2-digit',
                          })}
                        </Text>
                      </div>
                    </Card>
                  </Col>
                ))}
              </Row>
            )}
            {spaces.length === 0 && (
              <Card className="private-content-card" variant="borderless">
                <div className="empty-state-wrapper">
                  <Empty description="暂无私人空间，创建一个吧" />
                </div>
              </Card>
            )}
          </>
        )}
      </Spin>

      <Modal
        title="创建私人空间"
        open={showCreate}
        onCancel={() => { setShowCreate(false); form.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowCreate(false); form.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => form.submit()} loading={createLoading}>
              创建
            </Button>
          </div>
        }
        destroyOnHidden
        closable={false}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="空间名称"
            rules={[
              { required: true, message: '请输入空间名称' },
              { max: 20, message: '空间名称不超过 20 个字符' },
            ]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="introduction"
            label="空间介绍"
          >
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
          </Form>
        </Modal>
    </main>
  )
}

export default PrivateSpace
