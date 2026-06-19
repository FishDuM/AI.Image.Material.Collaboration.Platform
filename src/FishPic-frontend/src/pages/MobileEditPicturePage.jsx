import { useState, useCallback, useEffect, useContext } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Form, Input, Select, Button, message } from 'antd'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { updatePicture, getPictureEditMessage, submitAiTag } from '../api'
import { useSystemTypes } from '../hooks/useSystemTypes'
import { AuthContext } from '../context/AuthContext'
import { isVipUser } from '../utils/constants'
import './MobileEditPicturePage.css'

export default function MobileEditPicturePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const systemTags = useSystemTypes()
  const { userInfo } = useContext(AuthContext)

  const { pictureId, pictureUrl, pictureName, introduction, tags } = location.state || {}

  useEffect(() => {
    if (!pictureId) return
    getPictureEditMessage(pictureId).then(result => {
      if (result) {
        form.setFieldsValue({
          pictureName: result.pictureName || '',
          introduction: result.introduction || '',
          tags: Array.isArray(result.tags) ? result.tags : [],
        })
      }
    }).catch(() => {})
  }, [pictureId, form])

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
        tags: values.tags || undefined,
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
            <img src={pictureUrl} alt="预览" />
          ) : (
            <div className="mobile-edit-picture-placeholder">无图片</div>
          )}
        </div>
        <div className="mobile-edit-picture-form-wrapper">
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ pictureName: pictureName || '', introduction: introduction || '', tags: tags || [] }}
          >
            <Form.Item name="pictureName" label="图片名称">
              <Input placeholder="留空则不修改" maxLength={50} allowClear />
            </Form.Item>
            <Form.Item name="introduction" label="图片介绍">
              <Input.TextArea placeholder="留空则不修改" maxLength={500} rows={4} allowClear />
            </Form.Item>
            <Form.Item name="tags" label="标签">
              <Select mode="multiple" placeholder="请选择标签" allowClear options={systemTags.map(t => ({ label: t, value: t }))} />
            </Form.Item>
            {isVipUser(userInfo?.level) && (
              <Form.Item>
              <Button block onClick={async () => {
                try {
                  await submitAiTag(pictureId)
                  message.info('AI正在后台执行，完成后将自动填充')
                  navigate(-1)
                } catch (e) {
                  message.error(e.message || 'AI识别提交失败')
                }
              }}>AI一键填写</Button>
              </Form.Item>
            )}
          </Form>
        </div>
      </div>
    </MobilePageWrapper>
  )
}
