/**
 * 生成 COS imageMogr2 缩略图 URL
 */
export function getThumbnailUrl(url, size = 200) {
  if (!url) return url
  return `${url}?imageMogr2/thumbnail/${size}x${size}`
}
