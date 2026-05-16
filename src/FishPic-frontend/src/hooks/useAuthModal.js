import { useState, useContext, useCallback } from 'react'
import { App as AntApp } from 'antd'
import { AuthContext } from '../context/AuthContext.jsx'
import { getLoginCheckCode, login, getRegisterCheckCode, register } from '../api'

export function useAuthModal(onLoginSuccess) {
  const { message } = AntApp.useApp()
  const auth = useContext(AuthContext)
  const authLogin = auth?.login

  const [loginVisible, setLoginVisible] = useState(false)
  const [registerVisible, setRegisterVisible] = useState(false)
  const [loginLoading, setLoginLoading] = useState(false)
  const [registerLoading, setRegisterLoading] = useState(false)
  const [loginCheckCodeUrl, setLoginCheckCodeUrl] = useState('')
  const [loginKey, setLoginKey] = useState('')
  const [registerCheckCodeUrl, setRegisterCheckCodeUrl] = useState('')
  const [registerKey, setRegisterKey] = useState('')

  const fetchLoginCheckCode = useCallback(async () => {
    try {
      const response = await getLoginCheckCode()
      const data = response?.data ?? response
      const inner = data?.data ?? data
      if (inner?.captchaKey && inner?.base64Image) {
        setLoginKey(inner.captchaKey)
        const imageSrc = inner.base64Image.startsWith('data:')
          ? inner.base64Image
          : `data:image/png;base64,${inner.base64Image}`
        setLoginCheckCodeUrl(imageSrc)
      } else {
        message.error('获取验证码失败')
      }
    } catch {
      message.error('获取验证码失败')
    }
  }, [message])

  const fetchRegisterCheckCode = useCallback(async () => {
    try {
      const response = await getRegisterCheckCode()
      const data = response?.data ?? response
      const inner = data?.data ?? data
      if (inner?.captchaKey && inner?.base64Image) {
        setRegisterKey(inner.captchaKey)
        const imageSrc = inner.base64Image.startsWith('data:')
          ? inner.base64Image
          : `data:image/png;base64,${inner.base64Image}`
        setRegisterCheckCodeUrl(imageSrc)
      } else {
        message.error('获取验证码失败')
      }
    } catch {
      message.error('获取验证码失败')
    }
  }, [message])

  const openLogin = useCallback(() => {
    setRegisterVisible(false)
    setLoginVisible(true)
    fetchLoginCheckCode()
  }, [fetchLoginCheckCode])

  const openRegister = useCallback(() => {
    setLoginVisible(false)
    setRegisterVisible(true)
    fetchRegisterCheckCode()
  }, [fetchRegisterCheckCode])

  const closeLogin = useCallback(() => {
    setLoginVisible(false)
    setLoginCheckCodeUrl('')
    setLoginKey('')
  }, [])

  const closeRegister = useCallback(() => {
    setRegisterVisible(false)
    setRegisterCheckCodeUrl('')
    setRegisterKey('')
  }, [])

  const handleLoginSubmit = useCallback(async (values, loginForm) => {
    setLoginLoading(true)
    try {
      if (!loginKey) {
        message.error('验证码已过期，请刷新验证码')
        fetchLoginCheckCode()
        loginForm?.setFieldValue('checkCode', '')
        setLoginLoading(false)
        return
      }
      const result = await login({ ...values, captchaKey: loginKey })
      authLogin(result)
      message.success('登录成功')
      loginForm?.resetFields()
      closeLogin()
      onLoginSuccess?.()
    } catch (error) {
      message.error(error.message || '登录失败，请重试')
      fetchLoginCheckCode()
      loginForm?.setFieldValue('checkCode', '')
    } finally {
      setLoginLoading(false)
    }
  }, [loginKey, authLogin, message, closeLogin, fetchLoginCheckCode, onLoginSuccess])

  const handleRegisterSubmit = useCallback(async (values, registerForm) => {
    setRegisterLoading(true)
    try {
      if (!registerKey) {
        message.error('验证码已过期，请刷新验证码')
        fetchRegisterCheckCode()
        registerForm?.setFieldValue('checkCode', '')
        setRegisterLoading(false)
        return
      }
      await register({
        username: values.username,
        password: values.password,
        checkPassword: values.checkPassword,
        checkCode: values.checkCode,
        captchaKey: registerKey,
      })
      message.success('注册成功，请登录')
      registerForm?.resetFields()
      closeRegister()
      openLogin()
    } catch (error) {
      message.error(error.message || '注册失败，请重试')
      fetchRegisterCheckCode()
      registerForm?.setFieldValue('checkCode', '')
    } finally {
      setRegisterLoading(false)
    }
  }, [registerKey, message, closeRegister, fetchRegisterCheckCode, openLogin])

  const refreshLoginCode = useCallback((loginForm) => {
    fetchLoginCheckCode()
    loginForm?.setFieldValue('checkCode', '')
  }, [fetchLoginCheckCode])

  const refreshRegisterCode = useCallback((registerForm) => {
    fetchRegisterCheckCode()
    registerForm?.setFieldValue('checkCode', '')
  }, [fetchRegisterCheckCode])

  return {
    loginVisible,
    registerVisible,
    loginLoading,
    registerLoading,
    loginCheckCodeUrl,
    registerCheckCodeUrl,
    openLogin,
    openRegister,
    closeLogin,
    closeRegister,
    handleLoginSubmit,
    handleRegisterSubmit,
    refreshLoginCode,
    refreshRegisterCode,
    switchToRegister: openRegister,
    switchToLogin: openLogin,
  }
}
