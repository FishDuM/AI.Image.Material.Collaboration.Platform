import { useState, useCallback, createElement } from 'react'
import { App as AntApp } from 'antd'
import { PlusOutlined, LoadingOutlined } from '@ant-design/icons'
import { uploadAvatar } from '../api'
import { getBase64 } from '../utils/upload'

export function useAvatarUpload({ userId, onSuccess }) {
  const { message } = AntApp.useApp()
  const [uploading, setUploading] = useState(false)
  const [previewUrl, setPreviewUrl] = useState(null)

  const handleChange = useCallback(async (info) => {
    if (info.file.status === 'uploading') {
      setUploading(true)
      return
    }
    if (info.file.status === 'done') {
      const url = await getBase64(info.file.originFileObj)
      setUploading(false)
      setPreviewUrl(url)
      if (info.file.response) {
        setPreviewUrl(info.file.response)
      }
      onSuccess?.()
      message.success('头像上传成功')
    }
    if (info.file.status === 'error') {
      setUploading(false)
      message.error('头像上传失败')
    }
  }, [message, onSuccess])

  const handleUpload = useCallback(async (options) => {
    const { file, onSuccess: onOk, onError } = options
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('id', userId)
      const result = await uploadAvatar(formData)
      onOk?.(result)
    } catch (error) {
      onError?.(error)
    }
  }, [userId])

  const reset = useCallback(() => {
    setPreviewUrl(null)
    setUploading(false)
  }, [])

  const uploadButton = createElement(
    'button',
    { style: { border: 0, background: 'none' }, type: 'button' },
    uploading ? createElement(LoadingOutlined) : createElement(PlusOutlined),
    createElement('div', { style: { marginTop: 8 } }, '上传')
  )

  return { uploading, previewUrl, setPreviewUrl, handleChange, handleUpload, reset, uploadButton }
}
