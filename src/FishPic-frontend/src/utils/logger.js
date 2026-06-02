/**
 * 统一日志工具，生产环境可接入 Sentry 等
 */
export const logError = (context, error) => {
  if (import.meta.env.DEV) {
    console.error(`[${context}]`, error)
  }
}

export const logWarn = (context, message) => {
  if (import.meta.env.DEV) {
    console.warn(`[${context}]`, message)
  }
}
