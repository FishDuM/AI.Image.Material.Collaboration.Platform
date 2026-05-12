import { Button, Form, Input } from 'antd'
import { useState } from 'react'

function ImageEditModal({ picture, onSave, onCancel }) {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (values) => {
    setLoading(true)
    try {
      await onSave?.(values)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      {picture?.url && (
        <div style={{ textAlign: 'center', marginBottom: 16 }}>
          <img
            src={picture.url}
            alt={picture.name}
            style={{ maxWidth: '100%', maxHeight: 200, objectFit: 'contain', borderRadius: 8 }}
          />
        </div>
      )}
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          name: picture?.name || '',
          introduction: picture?.introduction || '',
        }}
        onFinish={handleSubmit}
      >
        <Form.Item label="图片名称" name="name" rules={[{ required: true, message: '请输入图片名称' }]}>
          <Input placeholder="请输入图片名称" />
        </Form.Item>
        <Form.Item label="图片介绍" name="introduction">
          <Input.TextArea rows={3} placeholder="请输入图片介绍" />
        </Form.Item>
        <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}>
          <Button onClick={onCancel} style={{ marginRight: 8 }}>取消</Button>
          <Button type="primary" htmlType="submit" loading={loading}>保存</Button>
        </Form.Item>
      </Form>
    </>
  )
}

export default ImageEditModal
