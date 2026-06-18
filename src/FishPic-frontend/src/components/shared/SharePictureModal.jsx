import { Button, Form, Input, Modal, Select } from 'antd'

const expireOptions = [
  { value: 1, label: '1 天' },
  { value: 3, label: '3 天' },
  { value: 7, label: '7 天' },
]

const permissionOptions = [
  { value: true, label: '允许下载' },
  { value: false, label: '仅预览' },
]

function SharePictureModal({
  open,
  form,
  loading,
  shareLink,
  onCreate,
  onCopy,
  onClose,
}) {
  return (
    <Modal
      title="分享图片"
      open={open}
      onCancel={onClose}
      footer={null}
      width={420}
    >
      {!shareLink ? (
        <Form form={form} layout="vertical" onFinish={onCreate} initialValues={{ expireDays: 1, allowDownload: true }} style={{ marginTop: 16 }}>
          <Form.Item name="expireDays" label="有效期">
            <Select options={expireOptions} />
          </Form.Item>
          <Form.Item name="allowDownload" label="权限">
            <Select options={permissionOptions} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Button onClick={onClose} style={{ marginRight: 8 }}>取消</Button>
            <Button type="primary" htmlType="submit" loading={loading}>生成链接</Button>
          </Form.Item>
        </Form>
      ) : (
        <div style={{ marginTop: 16 }}>
          <Input.TextArea value={shareLink} readOnly autoSize style={{ marginBottom: 12 }} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={onClose}>关闭</Button>
            <Button type="primary" onClick={onCopy}>复制链接</Button>
          </div>
        </div>
      )}
    </Modal>
  )
}

export default SharePictureModal
