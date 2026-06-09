/**
 * 统一日志工具，生产环境接入 Sentry 等
 */
export const logError = (context, error) => {
  if (import.meta.env.DEV) {
    console.error(`[${context}]`, error)
  }
  // 生产环境保留 console.error 用于错误追踪
  if (!import.meta.env.DEV) {
    console.error(`[PROD][${context}]`, error?.message || error)
  }
}

export const logWarn = (context, message) => {
  if (import.meta.env.DEV) {
    console.warn(`[${context}]`, message)
  }
}
