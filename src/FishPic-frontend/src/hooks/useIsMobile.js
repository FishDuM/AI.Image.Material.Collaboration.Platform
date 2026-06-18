import { useState, useEffect } from 'react'

const MOBILE_BREAKPOINT = 768

export function useIsMobile() {
  const [isMobile, setIsMobile] = useState(window.innerWidth <= MOBILE_BREAKPOINT)

  useEffect(() => {
    let timer = null
    const handleResize = () => {
      window.clearTimeout(timer)
      timer = window.setTimeout(() => {
        setIsMobile(prev => {
          const next = window.innerWidth <= MOBILE_BREAKPOINT
          return prev === next ? prev : next
        })
      }, 120)
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  return isMobile
}
