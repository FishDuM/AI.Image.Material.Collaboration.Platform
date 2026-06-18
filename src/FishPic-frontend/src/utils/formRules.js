export const usernameRules = [
  { required: true, message: '请输入账号' },
  { min: 6, message: '账号至少 6 个字符' },
  { max: 30, message: '账号最多 30 个字符' },
]

export const passwordRules = [
  { required: true, message: '请输入密码' },
  { min: 8, message: '密码至少 8 个字符' },
  { max: 32, message: '密码最多 32 个字符' },
]

export const newPasswordRules = [
  { required: true, message: '请输入新密码' },
  { min: 8, message: '密码至少 8 个字符' },
  { max: 32, message: '密码最多 32 个字符' },
]

export const optionalPasswordRules = [
  { min: 8, message: '密码至少 8 个字符' },
  { max: 32, message: '密码最多 32 个字符' },
]

export const originalPasswordRules = [
  { required: true, message: '请输入原始密码' },
]

export const nicknameRules = [
  { required: true, message: '请输入昵称' },
]

export const emailRules = [
  { type: 'email', message: '请输入有效的邮箱地址' },
]

export const phoneRules = [
  { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' },
]

export const spaceNameRules = [
  { required: true, message: '请输入空间名称' },
  { max: 20, message: '空间名称不超过 20 个字符' },
]

export const teamNameRules = [
  { required: true, message: '请输入团队名称' },
  { max: 20, message: '团队名称不超过 20 个字符' },
]

export const captchaRules = [
  { required: true, message: '请输入验证码' },
  { len: 5, message: '请输入5位验证码' },
]

export const confirmPasswordRules = [
  { required: true, message: '请再次输入密码' },
  ({ getFieldValue }) => ({
    validator(_, value) {
      if (!value || getFieldValue('password') === value) {
        return Promise.resolve()
      }
      return Promise.reject(new Error('两次输入的密码不一致'))
    },
  }),
]
