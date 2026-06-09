/**
 * 生成 COS imageMogr2 缩略图 URL
 * @param {string} url 原图 URL
 * @param {number} size 缩略图尺寸（正方形边长）
 * @returns {string} 缩略图 URL
 */
export function getThumbnailUrl(url, size = 200) {
  if (!url) return url
  return `${url}?imageMogr2/thumbnail/${size}x${size}`
}
