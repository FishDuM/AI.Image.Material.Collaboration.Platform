import { useState } from 'react'
import { App } from 'antd'

const useUrlImport = () => {
  const { message } = App.useApp()
  const [urlImportUrl, setUrlImportUrl] = useState('')
  const [urlImporting, setUrlImporting] = useState(false)
  const [urlPreviewUrl, setUrlPreviewUrl] = useState('')
  const [urlPreviewError, setUrlPreviewError] = useState(false)
  const [urlResolved, setUrlResolved] = useState(false)
  const [popoverOpen, setPopoverOpen] = useState(false)

  const handleUrlResolve = () => {
    if (!urlImportUrl.trim()) { message.warning('请输入图片URL'); return }
    setUrlPreviewUrl(urlImportUrl.trim())
    setUrlPreviewError(false)
    setUrlResolved(true)
  }

  const handleUrlCancel = () => {
    setUrlImportUrl('')
    setUrlPreviewUrl('')
    setUrlPreviewError(false)
    setUrlResolved(false)
    setPopoverOpen(false)
  }

  const resetUrlImport = () => {
    setUrlImportUrl('')
    setUrlImporting(false)
    setUrlPreviewUrl('')
    setUrlPreviewError(false)
    setUrlResolved(false)
    setPopoverOpen(false)
  }

  return {
    urlImportUrl,
    setUrlImportUrl,
    urlImporting,
    setUrlImporting,
    urlPreviewUrl,
    setUrlPreviewUrl,
    urlPreviewError,
    setUrlPreviewError,
    urlResolved,
    setUrlResolved,
    popoverOpen,
    setPopoverOpen,
    handleUrlResolve,
    handleUrlCancel,
    resetUrlImport,
  }
}

export default useUrlImport
