import { useState, useEffect, useRef, useCallback } from 'react'

function CategoryBar({ items, selected, onSelect, className = '' }) {
  const scrollRef = useRef(null)
  const [canScrollLeft, setCanScrollLeft] = useState(false)
  const [canScrollRight, setCanScrollRight] = useState(false)

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    setCanScrollLeft(el.scrollLeft > 4)
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 4)
  }, [])

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    updateScrollState()
    el.addEventListener('scroll', updateScrollState, { passive: true })
    const ro = new ResizeObserver(updateScrollState)
    ro.observe(el)
    return () => {
      el.removeEventListener('scroll', updateScrollState)
      ro.disconnect()
    }
  }, [items, updateScrollState])

  // 选中项变化时自动滚动到可见区域
  useEffect(() => {
    const el = scrollRef.current
    if (!el || !selected) return
    const active = el.querySelector('.category-tag-active')
    if (!active) return
    const cr = el.getBoundingClientRect()
    const ar = active.getBoundingClientRect()
    if (ar.left < cr.left) {
      el.scrollBy({ left: ar.left - cr.left - 12, behavior: 'smooth' })
    } else if (ar.right > cr.right) {
      el.scrollBy({ left: ar.right - cr.right + 12, behavior: 'smooth' })
    }
  }, [selected])

  const scrollBy = useCallback((offset) => {
    scrollRef.current?.scrollBy({ left: offset, behavior: 'smooth' })
  }, [])

  if (!items || items.length === 0) return null

  const btn = (dir) => (
    <button
      type="button"
      className={`category-bar-scroll-btn category-bar-scroll-${dir}`}
      onClick={() => scrollBy(dir === 'left' ? -200 : 200)}
      aria-label={dir === 'left' ? '向左滚动' : '向右滚动'}
    >
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d={dir === 'left' ? 'M10 3L5 8l5 5' : 'M6 3l5 5-5 5'} stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
    </button>
  )

  return (
    <div className="category-bar-wrapper">
      {canScrollLeft && btn('left')}
      <div className={`category-bar ${className}`} ref={scrollRef}>
        {items.map((cat) => (
          <span
            key={cat}
            className={`category-tag ${selected === cat ? 'category-tag-active' : ''}`}
            onClick={() => onSelect?.(cat)}
          >
            {cat}
          </span>
        ))}
      </div>
      {canScrollRight && btn('right')}
    </div>
  )
}

export default CategoryBar
