import { LoginModal, RegisterModal } from './LoginModal.jsx'

function AuthModals({ authModal, loginForm, registerForm, showAgreement }) {
  return (
    <>
      <LoginModal
        open={authModal.loginVisible}
        onCancel={() => { loginForm.resetFields(); authModal.closeLogin() }}
        loginForm={loginForm}
        loading={authModal.loginLoading}
        checkCodeUrl={authModal.loginCheckCodeUrl}
        onSubmit={(values) => authModal.handleLoginSubmit(values, loginForm)}
        onRefreshCode={() => authModal.refreshLoginCode(loginForm)}
        onSwitchToRegister={authModal.switchToRegister}
      />

      <RegisterModal
        open={authModal.registerVisible}
        onCancel={() => { registerForm.resetFields(); authModal.closeRegister() }}
        registerForm={registerForm}
        loading={authModal.registerLoading}
        checkCodeUrl={authModal.registerCheckCodeUrl}
        onSubmit={(values) => authModal.handleRegisterSubmit(values, registerForm)}
        onRefreshCode={() => authModal.refreshRegisterCode(registerForm)}
        onSwitchToLogin={authModal.switchToLogin}
        showAgreement={showAgreement}
      />
    </>
  )
}

export default AuthModals