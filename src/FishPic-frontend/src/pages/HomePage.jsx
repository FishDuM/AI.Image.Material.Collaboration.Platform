import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Carousel, Masonry, Image as AntImage, Spin, Form } from 'antd'
import { getPictureList } from '../api'
import { useAuthModal } from '../hooks/useAuthModal.js'
import { useFetchWithCleanup, useSystemTypes, useMarquee } from '../hooks/useRequestUtils'
import AuthModals from '../components/shared/AuthModals.jsx'
import SearchBar from '../components/shared/SearchBar.jsx'
import CategoryBar from '../components/shared/CategoryBar.jsx'

function HomePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()
  const [marqueeImages, setMarqueeImages] = useState([])
  const [currentSlide, setCurrentSlide] = useState(0)
  const [imgLoaded, setImgLoaded] = useState({})
  const [searchValue, setSearchValue] = useState('')
  const [searchTag, setSearchTag] = useState('热门')
  const [categoryList, setCategoryList] = useState([])
  const [selectedCategory, setSelectedCategory] = useState('热门')
  const [pictureList, setPictureList] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureLoading, setPictureLoading] = useState(true)
  const [hasMore, setHasMore] = useState(true)
  const carouselRef = useRef(null)
  const loadMoreRef = useRef(null)
  const isFirstRender = useRef(true)
  const requestIdRef = useRef(0)
  const PAGE_SIZE = 20
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia('(min-width: 1025px)').matches)
  const [coverflowIndex, setCoverflowIndex] = useState(0)
  const [coverflowTick, setCoverflowTick] = useState(0)
  const carouselWrapperRef = useRef(null)
  const touchStartXRef = useRef(null)

  const { createSignal } = useFetchWithCleanup()
  const { fetchSystemTypes } = useSystemTypes()
  const { fetchMarquee } = useMarquee()

  const authModal = useAuthModal(() => {
    const from = location.state?.from?.pathname || '/community'
    navigate(from, { replace: true })
  })

  const handlePrev = useCallback(() => carouselRef.current?.prev(), [])
  const handleNext = useCallback(() => carouselRef.current?.next(), [])

  const handleSearch = useCallback(() => {
    const trimmed = searchValue.trim()
    setSearchTag(trimmed)
    setPicturePage(1)
  }, [searchValue])

  const handleCategorySelect = useCallback((cat) => {
    setSelectedCategory(cat)
    setSearchTag(cat === '热门' ? '' : cat)
    setPicturePage(1)
  }, [])

  useEffect(() => {
    fetchMarquee().then((images) => {
      if (Array.isArray(images) && images.length > 0) setMarqueeImages(images)
    }).catch(() => {})
  }, [fetchMarquee])

  useEffect(() => {
    fetchSystemTypes().then((result) => {
      if (Array.isArray(result)) {
        setCategoryList(['热门', ...result.filter(c => c !== '推荐' && c !== '热门')])
      } else {
        setCategoryList(['热门'])
      }
    }).catch(() => setCategoryList(['热门']))
  }, [fetchSystemTypes])

  const loadPictures = useCallback(async (page) => {
    const requestId = ++requestIdRef.current
    setPictureLoading(true)
    try {
      const signal = createSignal()
      const result = await getPictureList(page, PAGE_SIZE, { signal }, searchTag)
      if (requestId !== requestIdRef.current) return
      if (result && Array.isArray(result.records)) {
        setPictureList(prev => page === 1 ? result.records : [...prev, ...result.records])
        setHasMore(result.records.length === PAGE_SIZE)
      } else {
        setHasMore(false)
      }
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      if (requestId !== requestIdRef.current) return
      setHasMore(false)
    }
    finally {
      if (requestId === requestIdRef.current) {
        setPictureLoading(false)
      }
    }
  }, [createSignal, searchTag])

  // 组件挂载时加载首屏图片（默认 searchTag = '热门'）
  useEffect(() => {
    loadPictures(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // searchTag 变化时重新加载（跳过首次挂载，避免与上方效果重复）
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false
      return
    }
    loadPictures(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchTag])

  useEffect(() => {
    if (!hasMore || pictureLoading) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && hasMore && !pictureLoading) {
        setPicturePage(prev => prev + 1)
      }
    }, { threshold: 0.1 })
    const el = loadMoreRef.current
    if (el) observer.observe(el)
    return () => observer.disconnect()
  }, [hasMore, pictureLoading])

  useEffect(() => {
    if (picturePage > 1) loadPictures(picturePage)
  }, [picturePage, loadPictures])

  const useCoverflow = isDesktop && marqueeImages.length >= 3

  useEffect(() => {
    const mql = window.matchMedia('(min-width: 1025px)')
    const handler = (e) => setIsDesktop(e.matches)
    mql.addEventListener('change', handler)
    return () => mql.removeEventListener('change', handler)
  }, [])

  useEffect(() => {
    if (!useCoverflow) return
    const id = setInterval(() => setCoverflowIndex(prev => (prev + 1) % marqueeImages.length), 4500)
    return () => clearInterval(id)
  }, [useCoverflow, marqueeImages.length, coverflowTick])

  const handleCoverflowPrev = useCallback(() => {
    setCoverflowIndex(prev => (prev - 1 + marqueeImages.length) % marqueeImages.length)
    setCoverflowTick(t => t + 1)
  }, [marqueeImages.length])

  const handleCoverflowNext = useCallback(() => {
    setCoverflowIndex(prev => (prev + 1) % marqueeImages.length)
    setCoverflowTick(t => t + 1)
  }, [marqueeImages.length])

  const handleCoverflowDot = useCallback((idx) => {
    setCoverflowIndex(idx)
    setCoverflowTick(t => t + 1)
  }, [])

  const handleDragStart = useCallback((clientX) => {
    touchStartXRef.current = clientX
  }, [])

  const handleDragEnd = useCallback((clientX) => {
    const startX = touchStartXRef.current
    touchStartXRef.current = null
    if (startX === null) return
    const deltaX = clientX - startX
    if (Math.abs(deltaX) < 50) return
    if (useCoverflow) {
      if (deltaX < 0) handleCoverflowNext(); else handleCoverflowPrev()
    } else {
      if (deltaX < 0) handleNext(); else handlePrev()
    }
  }, [useCoverflow, handleCoverflowNext, handleCoverflowPrev, handleNext, handlePrev])

  const handleTouchStart = useCallback((e) => handleDragStart(e.touches[0].clientX), [handleDragStart])
  const handleTouchEnd = useCallback((e) => handleDragEnd(e.changedTouches[0].clientX), [handleDragEnd])
  const handleMouseDown = useCallback((e) => { e.preventDefault(); handleDragStart(e.clientX) }, [handleDragStart])
  const handleMouseUp = useCallback((e) => handleDragEnd(e.clientX), [handleDragEnd])

  const masonryItems = useMemo(() => pictureList.map(pic => ({ key: `pic-${pic.id}`, data: pic })), [pictureList])

  return (
    <>
      {marqueeImages.length > 0 && (
        <div className={`carousel-section${useCoverflow ? ' carousel-section-coverflow' : ''}`}>
          <div className={`carousel-wrapper${useCoverflow ? ' carousel-wrapper-coverflow' : ''}`}
            ref={carouselWrapperRef}
            onTouchStart={handleTouchStart}
            onTouchEnd={handleTouchEnd}
            onMouseDown={handleMouseDown}
            onMouseUp={handleMouseUp}
          >
            {useCoverflow ? (
              <div className="coverflow-container">
                {[-1, 0, 1].map((offset) => {
                  const idx = ((coverflowIndex + offset) % marqueeImages.length + marqueeImages.length) % marqueeImages.length
                  const isActive = offset === 0
                  return (
                    <div
                      key={`cf-${offset}`}
                      className={`coverflow-slide${isActive ? ' coverflow-slide-active' : ''} coverflow-slide-pos-${offset < 0 ? 'left' : offset === 0 ? 'center' : 'right'}`}
                      onClick={() => { if (offset < 0) handleCoverflowPrev(); else if (offset > 0) handleCoverflowNext() }}
                    >
                      {!imgLoaded[idx] && <div className="carousel-skeleton" />}
                      <img src={marqueeImages[idx]} alt={`轮播图 ${idx + 1}`} className="carousel-image coverflow-image" style={{ opacity: imgLoaded[idx] ? 1 : 0 }}
                        onLoad={() => setImgLoaded(prev => ({ ...prev, [idx]: true }))}
                        onError={(e) => { e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwMCIgaGVpZ2h0PSI1MDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEyMDAiIGhlaWdodD0iNTAwIiBmaWxsPSIjMWYxZjFmIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIyMCIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPua2ieWPiuWfuuinpuWNoOe6qTwvdGV4dD48L3N2Zz4='; setImgLoaded(prev => ({ ...prev, [idx]: true })) }}
                      />
                    </div>
                  )
                })}
              </div>
            ) : (
              <Carousel ref={carouselRef} autoplay autoplaySpeed={4500} dots arrows={false} speed={600} effect="fade" fade afterChange={setCurrentSlide} pauseOnHover>
                {marqueeImages.map((url, index) => (
                  <div key={index} className="carousel-slide">
                    {!imgLoaded[index] && <div className="carousel-skeleton" />}
                    <img src={url} alt={`轮播图 ${index + 1}`} className="carousel-image" style={{ opacity: imgLoaded[index] ? 1 : 0 }}
                      onLoad={() => setImgLoaded(prev => ({ ...prev, [index]: true }))}
                      onError={(e) => { e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwMCIgaGVpZ2h0PSI1MDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEyMDAiIGhlaWdodD0iNTAwIiBmaWxsPSIjMWYxZjFmIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIyMCIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPua2ieWPiuWfuuinpuWNoOe6qTwvdGV4dD48L3N2Zz4='; setImgLoaded(prev => ({ ...prev, [index]: true })) }}
                    />
                  </div>
                ))}
              </Carousel>
            )}

            {marqueeImages.length > 1 && (
              <>
                <button type="button" className="carousel-arrow-btn carousel-arrow-left" onClick={useCoverflow ? handleCoverflowPrev : handlePrev}>&lsaquo;</button>
                <button type="button" className="carousel-arrow-btn carousel-arrow-right" onClick={useCoverflow ? handleCoverflowNext : handleNext}>&rsaquo;</button>
              </>
            )}

            {marqueeImages.length > 1 && (
              <div className="carousel-counter-badge">{(useCoverflow ? coverflowIndex : currentSlide) + 1} / {marqueeImages.length}</div>
            )}

            {useCoverflow && (
              <div className="coverflow-dots">
                {marqueeImages.map((_, idx) => (
                  <span key={idx} className={`coverflow-dot${idx === coverflowIndex ? ' coverflow-dot-active' : ''}`} onClick={() => handleCoverflowDot(idx)} />
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      <SearchBar
        className="home-search"
        placeholder="搜索图片、壁纸、头像..."
        value={searchValue}
        onChange={setSearchValue}
        onSearch={handleSearch}
      />

      {categoryList.length > 0 && (
        <div className="home-category-section">
          <CategoryBar items={categoryList} selected={selectedCategory} onSelect={handleCategorySelect} className="home-category-bar" />
        </div>
      )}

      <div className="home-masonry-section">
        {masonryItems.length > 0 && (
          <Masonry columns={{ xs: 2, sm: 3, md: 4, lg: 5 }} gutter={[12, 12]} fresh items={masonryItems} itemRender={(item) => (
            <div className="home-masonry-item"><AntImage src={item.data.url} alt="" className="home-masonry-image" fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgZmlsbD0iIzJhMmEyYSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iYXJpYWwiIGZvbnQtc2l6ZT0iMTQiIGZpbGw9IiM2NjYiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj7lm77niYfliqDovb3lpLHotKU8L3RleHQ+PC9zdmc+" /></div>
          )} />
        )}
        {hasMore && <div ref={loadMoreRef} className="home-load-more" />}
        {pictureLoading && <div className="home-loading-spinner"><Spin /></div>}
        {!hasMore && masonryItems.length > 0 && <div className="home-no-more">已加载全部图片</div>}
      </div>

      <AuthModals authModal={authModal} loginForm={loginForm} registerForm={registerForm} showAgreement />
    </>
  )
}

export default HomePage
