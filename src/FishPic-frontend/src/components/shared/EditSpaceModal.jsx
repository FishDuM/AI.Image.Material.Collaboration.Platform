import { useEffect } from 'react'
import { Button, Modal, Form, Input } from 'antd'
import { spaceNameRules } from '../../utils/formRules'

function EditSpaceModal({ open, loading, initialValues, onSubmit, onCancel }) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (open && initialValues) {
      form.setFieldsValue(initialValues)
    } else if (!open) {
      form.resetFields()
    }
  }, [open, initialValues, form])

  const handleCancel = () => {
    onCancel()
  }

  return (
    <Modal
      title="修改空间"
      open={open}
      onCancel={handleCancel}
      footer={
        <div style={{ textAlign: 'right' }}>
          <Button onClick={handleCancel} style={{ marginRight: 8 }}>
            取消
          </Button>
          <Button type="primary" onClick={() => form.submit()} loading={loading}>
            保存
          </Button>
        </div>
      }
      closable={false}
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={onSubmit}
        style={{ marginTop: 16 }}
      >
        <Form.Item
          name="name"
          label="空间名称"
          rules={spaceNameRules}
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
  )
}

export default EditSpaceModal
