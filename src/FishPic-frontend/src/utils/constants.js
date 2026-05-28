export const PAGINATION_LOCALE = {
  items_per_page: '条/页',
  jump_to: '跳至',
  jump_to_confirm: '确定',
  page: '页',
  prev_page: '上一页',
  next_page: '下一页',
  prev_5: '向前 5 页',
  next_5: '向后 5 页',
  prev_3: '向前 3 页',
  next_3: '向后 3 页',
  page_size: '页码',
}

export const LEVEL_MAP = {
  0: { label: '普通', color: 'green', className: 'level-normal', cardClass: 'storage-card-normal' },
  1: { label: 'VIP', color: 'gold', className: 'level-vip', cardClass: 'storage-card-vip' },
  2: { label: 'SVIP', color: 'orange', className: 'level-svip', cardClass: 'storage-card-svip' },
}

export const storageStrokeColor = {
  '0%': '#5A5A5A',
  '100%': '#87d068',
}

export const formatStorage = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const val = bytes / Math.pow(1024, i)
  return `${val.toFixed(i > 2 ? 1 : (i > 1 ? 2 : 0))} ${units[i]}`
}

export const PAGE_SIZE = 20

export const formatTime = (timeString) => {
  if (!timeString) return ''
  const now = new Date()
  const diffMs = now - new Date(timeString)
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffDays >= 7) {
    return new Date(timeString).toLocaleString('zh-CN')
  }
  if (diffDays > 0) return `${diffDays}天前`
  if (diffHours > 0) return `${diffHours}小时前`
  if (diffMinutes > 0) return `${diffMinutes}分钟前`
  if (diffSeconds > 0) return `${diffSeconds}秒前`
  return '刚刚'
}