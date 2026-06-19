import { useContext } from 'react'
import { useNavigate } from 'react-router-dom'
import { LeftOutlined } from '@ant-design/icons'
import { ThemeContext } from '../context/ThemeContext'
import { useIsMobile } from '../hooks/useIsMobile'
import './MobilePageWrapper.css'

export default function MobilePageWrapper({ title, titleContent, children, onClose, rightContent, showBack = true }) {
  const navigate = useNavigate()
  const { isDarkMode } = useContext(ThemeContext)
  const theme = isDarkMode ? 'dark' : 'light'
  const isMobile = useIsMobile()

  const handleBack = () => {
    if (onClose) {
      onClose()
    } else {
      navigate(-1)
    }
  }

  if (!isMobile) return null

  return (
    <div className={`mobile-page-wrapper ${theme}`}>
      <div className="mobile-page-header">
        <div className="mobile-page-header-left">
          {showBack && (
            <button className="mobile-page-back-btn" onClick={handleBack}>
              <LeftOutlined />
            </button>
          )}
          {titleContent ? (
            <div className="mobile-page-title-custom">{titleContent}</div>
          ) : (
            <h1 className="mobile-page-title">{title}</h1>
          )}
        </div>
        {rightContent && (
          <div className="mobile-page-header-right">
            {rightContent}
          </div>
        )}
      </div>
      <div className="mobile-page-content">
        {children}
      </div>
    </div>
  )
}
