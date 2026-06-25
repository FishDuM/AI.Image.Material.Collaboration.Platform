import { useState } from 'react'
import { Form } from 'antd'
import { createShare } from '../api'

export function useShare({ selectedIds, message, onValidate }) {
  const [showShare, setShowShare] = useState(false)
  const [shareForm] = Form.useForm()
  const [shareLoading, setShareLoading] = useState(false)
  const [shareLink, setShareLink] = useState('')

  const handleOpenShare = () => {
    if (selectedIds.length === 0) {
      message.warning('请选择至少一张图片进行分享')
      return
    }
    if (onValidate && onValidate() === false) return
    shareForm.resetFields()
    setShareLink('')
    setShowShare(true)
  }

  const handleCreateShare = async (values) => {
    setShareLoading(true)
    try {
      const token = await createShare({
        pictureIds: selectedIds,
        expireDays: values.expireDays || 1,
        allowDownload: values.allowDownload ? 1 : 0,
        maxViewCount: values.maxViewCount ?? undefined,
      })
      const link = `${window.location.origin}/s/${token}`
      setShareLink(link)
      message.success('分享链接已生成')
    } catch (error) {
      message.error(error.message || '创建分享失败')
    } finally {
      setShareLoading(false)
    }
  }

  const handleCopyShareLink = () => {
    navigator.clipboard.writeText(shareLink).then(() => {
      message.success('链接已复制到剪贴板')
    }).catch(() => {
      message.error('复制失败，请手动复制')
    })
  }

  const handleCloseShare = () => {
    setShowShare(false)
    shareForm.resetFields()
    setShareLink('')
  }

  return {
    showShare,
    shareForm,
    shareLoading,
    shareLink,
    handleOpenShare,
    handleCreateShare,
    handleCopyShareLink,
    handleCloseShare,
  }
}
