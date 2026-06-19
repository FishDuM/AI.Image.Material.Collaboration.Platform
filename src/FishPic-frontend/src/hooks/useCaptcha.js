import { useCallback, useState } from 'react'
import { App } from 'antd'

export function useCaptcha(fetcher) {
  const { message } = App.useApp()
  const [captchaImage, setCaptchaImage] = useState('')
  const [captchaKey, setCaptchaKey] = useState('')

  const refreshCaptcha = useCallback(async () => {
    try {
      const response = await fetcher()
      const inner = response.data?.data ?? response.data
      if (inner?.captchaKey && inner?.base64Image) {
        setCaptchaKey(inner.captchaKey)
        setCaptchaImage(inner.base64Image)
        return inner
      }
      throw new Error('验证码响应异常')
    } catch {
      setCaptchaKey('')
      setCaptchaImage('')
      message.error('获取验证码失败')
      return null
    }
  }, [fetcher, message])

  const clearCaptcha = () => {
    setCaptchaKey('')
    setCaptchaImage('')
  }

  return {
    captchaImage,
    captchaKey,
    refreshCaptcha,
    clearCaptcha,
  }
}
