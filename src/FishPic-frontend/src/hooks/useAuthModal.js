import { useState, useContext, useCallback } from 'react'
import { App as AntApp } from 'antd'
import { AuthContext } from '../context/AuthContext.jsx'
import { getLoginCheckCode, login, getRegisterCheckCode, register } from '../api'
import { useCaptcha } from './useCaptcha'

export function useAuthModal(onLoginSuccess) {
  const { message } = AntApp.useApp()
  const auth = useContext(AuthContext)
  const authLogin = auth.login

  const [loginVisible, setLoginVisible] = useState(false)
  const [registerVisible, setRegisterVisible] = useState(false)
  const [loginLoading, setLoginLoading] = useState(false)
  const [registerLoading, setRegisterLoading] = useState(false)
  const loginCaptcha = useCaptcha(getLoginCheckCode)
  const registerCaptcha = useCaptcha(getRegisterCheckCode)

  const openLogin = () => {
    setRegisterVisible(false)
    setLoginVisible(true)
    loginCaptcha.refreshCaptcha()
  }

  const openRegister = () => {
    setLoginVisible(false)
    setRegisterVisible(true)
    registerCaptcha.refreshCaptcha()
  }

  const closeLogin = () => {
    setLoginVisible(false)
    loginCaptcha.clearCaptcha()
  }

  const closeRegister = () => {
    setRegisterVisible(false)
    registerCaptcha.clearCaptcha()
  }

  const handleLoginSubmit = useCallback(async (values, loginForm) => {
    setLoginLoading(true)
    try {
      if (!loginCaptcha.captchaKey) {
        message.error('验证码已过期，请刷新验证码')
        loginCaptcha.refreshCaptcha()
        loginForm?.setFieldValue('checkCode', '')
        setLoginLoading(false)
        return
      }
      const result = await login({ ...values, captchaKey: loginCaptcha.captchaKey })
      authLogin(result)
      message.success('登录成功')
      loginForm?.resetFields()
      closeLogin()
      onLoginSuccess?.()
    } catch (error) {
      message.error(error.message || '登录失败，请重试')
      loginForm?.setFieldValue('checkCode', '')
    } finally {
      setLoginLoading(false)
    }
  }, [loginCaptcha, authLogin, message, closeLogin, onLoginSuccess])

  const handleRegisterSubmit = useCallback(async (values, registerForm) => {
    setRegisterLoading(true)
    try {
      if (!registerCaptcha.captchaKey) {
        message.error('验证码已过期，请刷新验证码')
        registerForm?.setFieldValue('checkCode', '')
        setRegisterLoading(false)
        return
      }
      await register({
        username: values.username,
        password: values.password,
        checkPassword: values.checkPassword,
        checkCode: values.checkCode,
        captchaKey: registerCaptcha.captchaKey,
      })
      message.success('注册成功，请登录')
      registerForm?.resetFields()
      closeRegister()
      openLogin()
    } catch (error) {
      message.error(error.message || '注册失败，请重试')
      registerForm?.setFieldValue('checkCode', '')
    } finally {
      setRegisterLoading(false)
    }
  }, [registerCaptcha.captchaKey, message, closeRegister, openLogin])

  const refreshLoginCode = useCallback((loginForm) => {
    loginCaptcha.refreshCaptcha()
    loginForm?.setFieldValue('checkCode', '')
  }, [loginCaptcha])

  const refreshRegisterCode = useCallback((registerForm) => {
    registerCaptcha.refreshCaptcha()
    registerForm?.setFieldValue('checkCode', '')
  }, [registerCaptcha])

  return {
    loginVisible,
    registerVisible,
    loginLoading,
    registerLoading,
    loginCheckCodeUrl: loginCaptcha.captchaImage,
    registerCheckCodeUrl: registerCaptcha.captchaImage,
    openLogin,
    openRegister,
    closeLogin,
    closeRegister,
    handleLoginSubmit,
    handleRegisterSubmit,
    refreshLoginCode,
    refreshRegisterCode,
  }
}
