import { Button, Form, Input, Modal, Select } from 'antd'
import { EditOutlined } from '@ant-design/icons'

export default function PictureEditModal({
  open,
  form,
  picture,
  tags = [],
  loading,
  canUseAi,
  onSubmit,
  onAiTag,
  onEditImage,
  onCancel,
}) {
  return (
    <Modal
      className="edit-picture-modal"
      title={null}
      open={open}
      onCancel={onCancel}
      width="80vw"
      style={{ maxHeight: '75vh' }}
      footer={null}
      closable={false}
    >
      <div className="edit-picture-layout">
        <div className="edit-picture-left">
          {picture ? <img src={picture.url} alt="编辑中的图片" className="edit-picture-img" /> : null}
        </div>
        <div className="edit-picture-right">
          <div className="edit-picture-right-header">
            <span className="edit-picture-title">编辑图片信息</span>
          </div>
          <Form form={form} layout="vertical" onFinish={onSubmit} className="edit-picture-form">
            <Form.Item name="pictureName" label="图片名称">
              <Input placeholder="留空则不修改" maxLength={50} allowClear />
            </Form.Item>
            <Form.Item name="introduction" label="图片介绍">
              <Input.TextArea placeholder="留空则不修改" maxLength={500} rows={3} allowClear />
            </Form.Item>
            <Form.Item name="tags" label="标签">
              <Select mode="multiple" placeholder="请选择标签" allowClear options={tags.map(t => ({ label: t, value: t }))} />
            </Form.Item>
          </Form>
          <div className="edit-picture-right-footer">
            <div>
              {canUseAi && (
                <Button onClick={onAiTag}>AI一键填写</Button>
              )}
              <Button icon={<EditOutlined />} onClick={onEditImage}>编辑图片</Button>
            </div>
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <Button onClick={onCancel}>取消</Button>
              <Button type="primary" onClick={() => form.submit()} loading={loading}>
                保存
              </Button>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  )
}
