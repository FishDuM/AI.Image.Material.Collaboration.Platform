import { useState, useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Form, Input, Button, message } from 'antd'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { updatePicture } from '../api'
import './MobileEditPicturePage.css'

export default function MobileEditPicturePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  const { pictureId, pictureUrl, pictureName, introduction } = location.state || {}

  const handleClose = useCallback(() => {
    navigate(-1)
  }, [navigate])

  const handleSubmit = useCallback(async (values) => {
    if (!pictureId) return
    setLoading(true)
    try {
      await updatePicture({
        id: pictureId,
        pictureName: values.pictureName || undefined,
        introduction: values.introduction || undefined,
      })
      message.success('编辑成功')
      navigate(-1)
    } catch (error) {
      message.error(error.message || '编辑失败')
    } finally {
      setLoading(false)
    }
  }, [pictureId, navigate])

  return (
    <MobilePageWrapper
      title="编辑图片"
      onClose={handleClose}
      rightContent={
        <Button type="primary" size="small" onClick={() => form.submit()} loading={loading}>
          保存
        </Button>
      }
    >
      <div className="mobile-edit-picture">
        <div className="mobile-edit-picture-image">
          {pictureUrl ? (
            <img src={pictureUrl} alt="" />
          ) : (
            <div className="mobile-edit-picture-placeholder">无图片</div>
          )}
        </div>
        <div className="mobile-edit-picture-form-wrapper">
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ pictureName: pictureName || '', introduction: introduction || '' }}
          >
            <Form.Item name="pictureName" label="图片名称">
              <Input placeholder="留空则不修改" maxLength={50} allowClear />
            </Form.Item>
            <Form.Item name="introduction" label="图片介绍">
              <Input.TextArea placeholder="留空则不修改" maxLength={500} rows={4} allowClear />
            </Form.Item>
          </Form>
        </div>
      </div>
    </MobilePageWrapper>
  )
}
