import React from 'react'

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, errorMessage: '' }
  }

  static getDerivedStateFromError(error) {
    const message = error instanceof Error
      ? error.message
      : typeof error === 'string'
        ? error
        : '发生了未知错误'
    return { hasError: true, errorMessage: message }
  }

  componentDidCatch(error, errorInfo) {
    console.error('ErrorBoundary caught an error:', error)
    console.error('Component stack:', errorInfo.componentStack)
  }

  handleReset = () => {
    this.setState({ hasError: false, errorMessage: '' })
    if (this.props.onReset) {
      this.props.onReset()
    } else {
      window.location.href = '/'
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          padding: '20px',
          textAlign: 'center',
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          backgroundColor: 'var(--bg-primary, #f5f5f5)'
        }}>
          <h1 style={{ color: 'var(--accent, #D70015)', marginBottom: '16px' }}>页面出错了</h1>
          <p style={{ color: 'var(--text-secondary, #666)', marginBottom: '24px' }}>
            {this.state.errorMessage}
          </p>
          <button
            onClick={this.handleReset}
            style={{
              padding: '8px 16px',
              backgroundColor: 'var(--accent, #3A3A3A)',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            返回首页
          </button>
        </div>
      )
    }

    return this.props.children
  }
}

export default ErrorBoundary
