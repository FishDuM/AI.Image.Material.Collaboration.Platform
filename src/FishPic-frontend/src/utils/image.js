export function getThumbnailUrl(url, size = 200) {
  if (!url) return url
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}imageMogr2/thumbnail/${size}x${size}`
}
