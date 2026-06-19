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

export const DEFAULT_LEVEL = { label: '普通', color: 'green', className: 'level-normal', cardClass: 'storage-card-normal' }

export const storageStrokeColor = {
  '0%': '#5A5A5A',
  '100%': '#87d068',
}

export const formatStorage = (bytes, fallback = '0 B') => {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return fallback
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(n) / Math.log(1024)), units.length - 1)
  const val = n / Math.pow(1024, i)
  return `${val.toFixed(i > 2 ? 1 : (i > 0 ? 2 : 0))} ${units[i]}`
}

export function computeSpaceStorage(space) {
  const sizeBytes = Number(space?.size) || 0;
  const storageBytes = Number(space?.storageSize) || 0;
  const percent = storageBytes > 0 ? Math.min(100, Math.round((sizeBytes / storageBytes) * 100)) : 0;
  return { sizeBytes, storageBytes, percent };
}

export function isVipUser(level) {
  return level != null && level >= 1;
}

export function showUpgradeHint(modal) {
  modal.info({ title: '升级会员', content: '请联系管理员开通 VIP/SVIP 会员', okText: '知道了' });
}

export const PAGE_SIZE = 20

export const TOKEN_REFRESH_INTERVAL = 5 * 60 * 1000

export const MAX_SELECT_COUNT = 15

export const CHUNK_UPLOAD_RETRY_COUNT = 3

export const CHUNK_UPLOAD_BACKOFF_BASE = 500

export const LOAD_MORE_THRESHOLD = 200

export const TIMEOUT_DEFAULT = 10000
export const TIMEOUT_AVATAR = 60000
export const TIMEOUT_PICTURE = 120000

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

// 空间类型映射
export const SPACE_TYPE_MAP = { 0: '私人空间', 1: '团队空间' }
export const SPACE_TYPE_COLOR = { 0: 'blue', 1: 'green' }

// 等级标签映射（简化版，用于表格 Tag 展示）
export const LEVEL_TAG_MAP = { 0: '普通', 1: 'VIP', 2: 'SVIP' }
export const LEVEL_TAG_COLOR = { 0: 'default', 1: 'gold', 2: 'red' }

// 管理员角色标签（优先级高于等级）
export const ADMIN_ROLE_TAG = { color: 'red', text: '管理员' }

// 图表主题色
export const CHART_COLORS = {
  primary: ['#1890ff', '#69c0ff'],
  success: ['#52c41a', '#95de64'],
  error: '#ff4d4f',
}

export function formatDateTime(t) {
  if (!t) return '-'
  const date = new Date(t)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

export function getPlaceholderImageBase64(width = 80, height = 80) {
  const svg = `<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg"><rect width="${width}" height="${height}" fill="#212121"/><text x="50%" y="50%" font-family="arial" font-size="12" fill="#6b6b6b" text-anchor="middle" dy=".3em">Loading...</text></svg>`
  return `data:image/svg+xml;base64,${btoa(svg)}`
}
