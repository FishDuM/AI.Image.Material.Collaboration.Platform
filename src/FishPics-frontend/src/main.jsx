import { StrictMode, useState, useEffect, createContext } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider, App as AntdApp, theme as antdTheme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import './index.css'
import App from './App.jsx'

export const ThemeContext = createContext({ isDarkMode: false, toggleTheme: () => {} })



function ThemeWrapper({ children }) {
  const [isDarkMode, setIsDarkMode] = useState(() => {
    const saved = localStorage.getItem('theme')
    return saved === 'dark'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', isDarkMode ? 'dark' : 'light')
    localStorage.setItem('theme', isDarkMode ? 'dark' : 'light')
  }, [isDarkMode])

  const toggleTheme = () => {
    setIsDarkMode(!isDarkMode)
  }

  return (
    <ThemeContext.Provider value={{ isDarkMode, toggleTheme }}>
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: isDarkMode ? '#4096ff' : '#262626',
            borderRadius: 8,
            fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
            fontSize: 14,
          },
          components: {
            Button: {
              borderRadius: 8,
              controlHeight: 40,
            },
            Modal: {
              borderRadiusLG: 12,
            },
            Input: {
              borderRadius: 8,
              controlHeight: 48,
            },
          },
          algorithm: isDarkMode ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        }}
        locale={zhCN}
      >
        {children}
      </ConfigProvider>
    </ThemeContext.Provider>
  )
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ThemeWrapper>
      <AntdApp>
        <App />
      </AntdApp>
    </ThemeWrapper>
  </StrictMode>,
)
