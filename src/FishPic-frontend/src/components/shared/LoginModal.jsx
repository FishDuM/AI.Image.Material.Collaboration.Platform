import { Form, Input, Button, Card, Checkbox, Modal } from 'antd'
import { UserOutlined, LockOutlined, ScanOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { captchaRules, confirmPasswordRules, passwordRules, usernameRules } from '../../utils/formRules'
import { PLACEHOLDER_QR } from '../../utils/fallbacks'

const QR_PLACEHOLDER = PLACEHOLDER_QR

function LoginPanel({ form, loading, checkCodeUrl, onSubmit, onRefreshCode, onSwitchToRegister }) {
  const [codeRefreshing, setCodeRefreshing] = useState(false)

  const handleRefreshCode = async () => {
    setCodeRefreshing(true)
    try { await onRefreshCode() } finally { setCodeRefreshing(false) }
  }

  return (
    <div className="form-container">
      <h2 className="form-title">账号登录</h2>
      <Form form={form} name="login" onFinish={onSubmit} autoComplete="off" size="large" requiredMark={false} layout="vertical">
        <Form.Item name="username" rules={usernameRules}>
          <Input prefix={<UserOutlined className="input-icon" />} placeholder="请输入账号" className="xhs-input" />
        </Form.Item>
        <Form.Item name="password" rules={passwordRules}>
          <Input.Password prefix={<LockOutlined className="input-icon" />} placeholder="请输入密码" className="xhs-input" />
        </Form.Item>
        <div className="check-code-row xhs">
          <Form.Item name="checkCode" noStyle rules={captchaRules}>
            <Input prefix={<LockOutlined className="input-icon" />} placeholder="请输入验证码" className="xhs-input check-code-input" maxLength={5} />
          </Form.Item>
          <Button className="get-code-btn" onClick={handleRefreshCode} type="link" loading={codeRefreshing} disabled={codeRefreshing}>
            {checkCodeUrl && <img src={checkCodeUrl} alt="验证码" className="check-code-img-btn" />}
          </Button>
        </div>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block className="xhs-submit-btn">登录</Button>
        </Form.Item>
        <div className="form-footer">
          <span>还没有账号？</span>
          <Button type="link" onClick={onSwitchToRegister} className="switch-form-btn">立即注册</Button>
        </div>
      </Form>
    </div>
  )
}

function RegisterPanel({ form, loading, checkCodeUrl, onSubmit, onRefreshCode, onSwitchToLogin, showAgreement }) {
  const [agreed, setAgreed] = useState(false)
  const [codeRefreshing, setCodeRefreshing] = useState(false)

  const handleSubmit = (values) => {
    if (showAgreement && !agreed) return
    onSubmit(values)
  }

  const handleRefreshCode = async () => {
    setCodeRefreshing(true)
    try { await onRefreshCode() } finally { setCodeRefreshing(false) }
  }

  return (
    <div className="form-container">
      <h2 className="form-title">账号注册</h2>
      <Form form={form} name="register" onFinish={handleSubmit} autoComplete="off" size="large" requiredMark={false} layout="vertical">
        <Form.Item name="username" rules={usernameRules}>
          <Input prefix={<UserOutlined className="input-icon" />} placeholder="请输入账号" className="xhs-input" />
        </Form.Item>
        <Form.Item name="password" rules={passwordRules}>
          <Input.Password prefix={<LockOutlined className="input-icon" />} placeholder="请输入密码" className="xhs-input" />
        </Form.Item>
        <Form.Item name="checkPassword" dependencies={['password']} rules={confirmPasswordRules}>
          <Input.Password prefix={<LockOutlined className="input-icon" />} placeholder="请再次输入密码" className="xhs-input" autoComplete="new-password" />
        </Form.Item>
        <div className="check-code-row xhs">
          <Form.Item name="checkCode" noStyle rules={captchaRules}>
            <Input prefix={<LockOutlined className="input-icon" />} placeholder="请输入验证码" className="xhs-input check-code-input" maxLength={5} />
          </Form.Item>
          <Button className="get-code-btn" onClick={handleRefreshCode} type="link" loading={codeRefreshing} disabled={codeRefreshing}>
            {checkCodeUrl && <img src={checkCodeUrl} alt="验证码" className="check-code-img-btn" />}
          </Button>
        </div>
        {showAgreement && (
          <Form.Item>
            <Checkbox checked={agreed} onChange={(e) => setAgreed(e.target.checked)}>
              <span style={{ color: 'var(--text-secondary)', fontSize: 13 }}>我已阅读并同意《用户协议》《隐私政策》</span>
            </Checkbox>
          </Form.Item>
        )}
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block className="xhs-submit-btn" disabled={showAgreement && !agreed}>注册</Button>
        </Form.Item>
        <div className="form-footer">
          <span>已有账号？</span>
          <Button type="link" onClick={onSwitchToLogin} className="switch-form-btn">立即登录</Button>
        </div>
      </Form>
    </div>
  )
}

function LeftPanel({ hint }) {
  return (
    <div className="xhs-left-panel">
      <div className="scan-hint">{hint}</div>
      <div className="qr-card">
        <Card className="qr-code-card" variant="borderless">
          <div className="qr-code-wrapper">
            <div className="qr-code-bg">
              <img src="/qrcode.jpg" alt="二维码" className="qr-placeholder" onError={(e) => { e.target.src = QR_PLACEHOLDER }} />
            </div>
          </div>
          <div className="scan-status">
            <ScanOutlined className="scan-icon" />
            <span>暂未实现该功能，敬请期待</span>
          </div>
        </Card>
      </div>
      <div className="scan-tips">
        <span>可用</span>
        <span className="app-name">FishPics</span>
        <span>或</span>
        <span className="app-name-wechat">微信</span>
        <span>扫码</span>
      </div>
    </div>
  )
}

export function LoginModal({ open, onCancel, loginForm, loading, checkCodeUrl, onSubmit, onRefreshCode, onSwitchToRegister }) {
  return (
    <Modal open={open} onCancel={onCancel} footer={null} centered className="xhs-modal" destroyOnHidden width={800}>
      <div className="xhs-modal-content">
        <LeftPanel hint="登录后推荐更懂你的笔记" />
        <div className="xhs-right-panel">
          <LoginPanel
            form={loginForm}
            loading={loading}
            checkCodeUrl={checkCodeUrl}
            onSubmit={onSubmit}
            onRefreshCode={onRefreshCode}
            onSwitchToRegister={onSwitchToRegister}
          />
        </div>
      </div>
    </Modal>
  )
}

export function RegisterModal({ open, onCancel, registerForm, loading, checkCodeUrl, onSubmit, onRefreshCode, onSwitchToLogin, showAgreement }) {
  return (
    <Modal open={open} onCancel={onCancel} footer={null} centered className="xhs-modal" destroyOnHidden width={800}>
      <div className="xhs-modal-content">
        <LeftPanel hint="加入我们，开始创作" />
        <div className="xhs-right-panel">
          <RegisterPanel
            form={registerForm}
            loading={loading}
            checkCodeUrl={checkCodeUrl}
            onSubmit={onSubmit}
            onRefreshCode={onRefreshCode}
            onSwitchToLogin={onSwitchToLogin}
            showAgreement={showAgreement}
          />
        </div>
      </div>
    </Modal>
  )
}

export function SettingsModal({ open, onCancel }) {
  return (
    <Modal open={open} onCancel={onCancel} footer={null} centered className="settings-modal" destroyOnHidden width={400}>
      <div className="settings-modal-content">
        <p className="dev-message">功能正在开发中，敬请期待 ~</p>
        <p className="dev-by">— By Fish</p>
        <div className="social-links">
          <a href="https://github.com/FishDuM" target="_blank" rel="noopener noreferrer" className="social-link-item" title="GitHub">
            <span className="anticon anticon-github"><svg viewBox="64 64 896 896" fill="currentColor" width="1em" height="1em"><path d="M511.6 76.3C264.3 76.3 64 276.8 64 523.7 64 718.1 189.8 882 369.3 946c18.4 3.4 25.2-8 25.2-17.7 0-8.8-.3-38.2-.6-69.6-149 32.4-180.4-62.8-180.4-62.8-24.4-62.1-59.7-78.5-59.7-78.5-48.8-33.4.4-32.7.4-32.7 54 3.8 82.5 55.4 82.5 55.4 48 82.1 125.7 58.4 156.3 44.7 4.8-34.7 18.8-58.4 34.1-71.8-119.5-13.6-244.9-59.8-244.9-265.9 0-58.7 21-106.8 55.4-144.6-5.6-13.6-24-68.2 5.2-142.2 0 0 45.2-14.5 148 55.2 42.9-11.9 89-17.9 134.8-18.1 45.8.2 91.9 6.2 134.9 18.1 102.6-69.7 147.7-55.2 147.7-55.2 29.3 74 10.9 128.6 5.3 142.2 34.5 37.8 55.3 85.9 55.3 144.6 0 206.6-125.8 252.2-245.5 265.5 19.3 16.6 36.4 49.4 36.4 99.7 0 72-0.6 129.8-0.6 147.2 0 14.4 9.7 31.4 25.5 26.1C875.6 881.6 1000 717.8 1000 523.7 1000 276.8 799.7 76.3 552.4 76.3z" /></svg></span>
          </a>
          <a href="https://space.bilibili.com/386312184" target="_blank" rel="noopener noreferrer" className="social-link-item" title="Bilibili">
            <span className="anticon"><svg viewBox="0 0 24 24" fill="currentColor" width="1em" height="1em"><path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.151.929.4.267.249.391.551.391.907 0 .355-.124.657-.373.906zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z" /></svg></span>
          </a>
          <a href="https://qm.qq.com/q/bH26HucOhW" target="_blank" rel="noopener noreferrer" className="social-link-item" title="QQ">
            <span className="anticon anticon-qq"><svg viewBox="64 64 896 896" fill="currentColor" width="1em" height="1em"><path d="M824.8 613.2c-16-51.4-34.4-94.6-62.7-165.3C766.5 271.5 689.3 136 511.5 136 333.7 136 256.5 271.5 260 447.9c-28.3 70.7-46.7 113.9-62.7 165.3-34 109.5-23 154.8-14.6 155.8 18 2.2 70.1-82.4 70.1-82.4 0 49 25.2 112.9 79.8 159-26.4 8.1-85.7 29.9-71.6 73.1 82.2 25.7 229.3-4.6 298-92.7 68.7 88.1 215.8 118.5 298 92.7 14.1-43.2-45.3-65-71.6-73.1 54.6-46.1 79.8-110 79.8-159 0 0 52.1 84.6 70.1 82.4 8.4-1 19.4-46.3-14.5-155.8z" /></svg></span>
          </a>
          <a href="https://fishdum.github.io/" target="_blank" rel="noopener noreferrer" className="social-link-item" title="个人网站">
            <span className="anticon anticon-global"><svg viewBox="64 64 896 896" fill="currentColor" width="1em" height="1em"><path d="M880 112c-3.8 0-7.7.2-11.6.5C837.3 56.7 776.6 32 712 32c-11.2 0-22.7 1.6-34.4 4.7C634.8 50.1 597 74 576 106.2c-21-32.2-58.8-56.1-101.6-69.5C462.7 33.6 451.2 32 440 32c-64.6 0-125.3 24.7-156.8 80.5C279.3 112.2 275.4 112 272 112c-53 0-96 43-96 96 0 11.5 2 22.6 5.8 33.1C134.7 269.8 104 333.8 104 404c0 27.7 4.6 54.4 13 79.3C59.2 512.5 24 562.8 24 620c0 53 43 96 96 96h104c13.3 0 24 10.7 24 24v64c0 13.3 10.7 24 24 24h368c13.3 0 24-10.7 24-24v-64c0-13.3 10.7-24 24-24h104c53 0 96-43 96-96 0-57.2-35.2-107.5-85-136.7 8.6-24.9 13.2-51.6 13.2-79.3 0-70.2-34.7-134.2-88.3-162.4 3.8-10.5 5.8-21.6 5.8-33.1 0-53-43-96-96-96zM184 620c-22.1 0-40-17.9-40-40s17.9-40 40-40 40 17.9 40 40-17.9 40-40 40zm656 0c-22.1 0-40-17.9-40-40s17.9-40 40-40 40 17.9 40 40-17.9 40-40 40z" /></svg></span>
          </a>
        </div>
      </div>
    </Modal>
  )
}
