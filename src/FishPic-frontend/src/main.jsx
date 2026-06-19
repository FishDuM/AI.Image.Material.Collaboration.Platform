import { StrictMode, useState, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import {BrowserRouter} from 'react-router-dom'
import { ConfigProvider, App as AntdApp, theme as antdTheme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import './index.css'
import './styles/animations.css'
import './styles/shared.css'
import './styles/carousel.css'
import App from './App.jsx'
import ErrorBoundary from './components/ErrorBoundary.jsx'

import { ThemeContext } from './context/ThemeContext.jsx'

dayjs.locale('zh-cn')

// eslint-disable-next-line react-refresh/only-export-components
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
    setIsDarkMode(prev => !prev)
  }

  return (
    <ThemeContext.Provider value={{ isDarkMode, toggleTheme }}>
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: isDarkMode ? '#E0E0E0' : '#3A3A3A',
            borderRadius: 8,
            fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
            fontSize: 14,
          },
          components: {
            Button: {
              borderRadius: 8,
              controlHeight: 40,
              ...(isDarkMode && {
                colorPrimary: '#D0D0D0',
                colorPrimaryHover: '#E0E0E0',
                colorPrimaryActive: '#BEBEBE',
                colorTextLightSolid: '#1A1A1A',
                defaultBorderColor: '#606060',
                defaultColor: '#D0D0D0',
              }),
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
  <ErrorBoundary>
    <ThemeWrapper>
      <BrowserRouter>
        <AntdApp>
          <App/>
        </AntdApp>
      </BrowserRouter>
    </ThemeWrapper>
  </ErrorBoundary>,
)
