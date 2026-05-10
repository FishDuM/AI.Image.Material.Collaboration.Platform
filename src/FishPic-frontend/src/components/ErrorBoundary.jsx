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
    void errorInfo
  }

  handleReset = () => {
    this.setState({ hasError: false, errorMessage: '' })
    window.location.href = '/'
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
          backgroundColor: '#f5f5f5'
        }}>
          <h1 style={{ color: '#ff4d4f', marginBottom: '16px' }}>页面出错了</h1>
          <p style={{ color: '#666', marginBottom: '24px' }}>
            {this.state.errorMessage}
          </p>
          <button
            onClick={this.handleReset}
            style={{
              padding: '8px 16px',
              backgroundColor: '#1890ff',
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
