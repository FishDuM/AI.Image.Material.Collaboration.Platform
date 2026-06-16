import { useCallback, useState } from 'react'
import { getSaveableSpaces, savePictureByUrl } from '../api'

export const SPACE_TYPE_MAP = { 0: '私人空间', 1: '团队空间' }
export const SPACE_TYPE_COLOR = { 0: 'blue', 1: 'green' }

export function useSaveToSpace({ imageUrl, message, onSaved }) {
  const [activeTab, setActiveTab] = useState('private')
  const [privateSpace, setPrivateSpace] = useState(null)
  const [teamSpaces, setTeamSpaces] = useState([])
  const [selectedSpaceIds, setSelectedSpaceIds] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const reset = useCallback(() => {
    setActiveTab('private')
    setSelectedSpaceIds([])
  }, [])

  const loadSpaces = useCallback(async () => {
    setLoading(true)
    try {
      const result = await getSaveableSpaces()
      const list = Array.isArray(result) ? result : []
      setPrivateSpace(list.find(space => space.type === 0) || null)
      setTeamSpaces(list.filter(space => space.type === 1))
    } catch {
      setPrivateSpace(null)
      setTeamSpaces([])
    } finally {
      setLoading(false)
    }
  }, [])

  const toggleSpace = useCallback((spaceId) => {
    setSelectedSpaceIds(prev =>
      prev.includes(spaceId) ? prev.filter(id => id !== spaceId) : [...prev, spaceId]
    )
  }, [])

  const saveSelectedSpaces = useCallback(async () => {
    if (selectedSpaceIds.length === 0) {
      message.warning('请至少选择一个空间')
      return false
    }
    if (!imageUrl) {
      message.error('图片 URL 为空')
      return false
    }

    setSaving(true)
    try {
      const results = await Promise.allSettled(
        selectedSpaceIds.map(spaceId => savePictureByUrl(imageUrl, spaceId))
      )
      const successCount = results.filter(result => result.status === 'fulfilled').length
      const failCount = results.length - successCount

      if (successCount > 0) {
        message.success(`已保存到 ${successCount} 个空间`)
      }
      if (failCount > 0) {
        message.error(`${failCount} 个空间保存失败`)
      }

      onSaved?.()
      return successCount > 0
    } catch {
      message.error('保存失败')
      return false
    } finally {
      setSaving(false)
    }
  }, [imageUrl, message, onSaved, selectedSpaceIds])

  return {
    activeTab,
    setActiveTab,
    privateSpace,
    teamSpaces,
    selectedSpaceIds,
    loading,
    saving,
    reset,
    loadSpaces,
    toggleSpace,
    saveSelectedSpaces,
  }
}
