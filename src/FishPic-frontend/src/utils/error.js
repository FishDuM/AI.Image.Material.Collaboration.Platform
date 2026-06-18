/**
 * 判断请求错误是否为取消请求导致的（可安全忽略）
 */
export function isCanceledError(error) {
  return error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED'
}
