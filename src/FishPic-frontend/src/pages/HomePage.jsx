import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Carousel, Masonry, Image as AntImage, Spin, Form } from 'antd'
import { getPictureList, getRecommendPictures } from '../api'
import { isCanceledError } from '../utils/error'
import { FALLBACK_BANNER, FALLBACK_THUMB } from '../utils/fallbacks'
import { useAuthModal } from '../hooks/useAuthModal.js'
import { useFetchWithCleanup, useMarquee } from '../hooks/useRequestUtils'
import { useSystemTypes } from '../hooks/useSystemTypes'
import { useMasonryItems } from '../hooks/useMasonryItems'
import { PAGE_SIZE } from '../utils/constants'
import { getThumbnailUrl } from '../utils/image'
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
  const [searchTag, setSearchTag] = useState('')
  const [categoryList, setCategoryList] = useState(['全部'])
  const [selectedCategory, setSelectedCategory] = useState('全部')
  const [pictureList, setPictureList] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureLoading, setPictureLoading] = useState(true)
  const [hasMore, setHasMore] = useState(true)
  const carouselRef = useRef(null)
  const loadMoreRef = useRef(null)
  const requestIdRef = useRef(0)
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia('(min-width: 1025px)').matches)
  const [coverflowIndex, setCoverflowIndex] = useState(0)
  const [coverflowTick, setCoverflowTick] = useState(0)
  const carouselWrapperRef = useRef(null)
  const touchStartXRef = useRef(null)

  const { createSignal } = useFetchWithCleanup()
  const systemTypes = useSystemTypes()
  const { fetchMarquee } = useMarquee()

  const authModal = useAuthModal(() => {
    const fromLocation = location.state?.from
    const from = fromLocation
      ? `${fromLocation.pathname || '/'}${fromLocation.search || ''}`
      : '/'
    navigate(from, { replace: true })
  })

  useEffect(() => {
    if (location.state?.showLogin) {
      authModal.openLogin()
      navigate(location.pathname, { replace: true, state: {} })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handlePrev = () => carouselRef.current?.prev()
  const handleNext = () => carouselRef.current?.next()

  const handleSearch = useCallback(() => {
    const trimmed = searchValue.trim()
    setSearchTag(trimmed)
    setPicturePage(1)
  }, [searchValue])

  const handleCategorySelect = useCallback((cat) => {
    setSelectedCategory(cat)
    setSearchTag(cat === '全部' ? '' : cat)
    setPicturePage(1)
  }, [])

  useEffect(() => {
    fetchMarquee().then((images) => {
      if (Array.isArray(images) && images.length > 0) setMarqueeImages(images)
    }).catch(() => {})
  }, [fetchMarquee])

  useEffect(() => {
    if (systemTypes.length > 0) {
      setCategoryList(['全部', ...systemTypes.filter(c => c !== '推荐')])
    }
  }, [systemTypes])

  const loadPictures = useCallback(async (page) => {
    const requestId = ++requestIdRef.current
    setPictureLoading(true)
    try {
      const signal = createSignal()
      const isRecommend = selectedCategory === '推荐'
      const result = isRecommend
        ? await getRecommendPictures({ current: page, pageSize: PAGE_SIZE }, { signal })
        : await getPictureList(page, PAGE_SIZE, { signal }, searchTag)
      if (requestId !== requestIdRef.current) return
      if (result && Array.isArray(result.records)) {
        setPictureList(prev => page === 1 ? result.records : [...prev, ...result.records])
        setHasMore(result.records.length === PAGE_SIZE)
      } else {
        setHasMore(false)
      }
    } catch (err) {
      if (isCanceledError(err)) return
      if (requestId !== requestIdRef.current) return
      setHasMore(false)
    }
    finally {
      if (requestId === requestIdRef.current) {
        setPictureLoading(false)
      }
    }
  }, [createSignal, searchTag, selectedCategory])

  useEffect(() => {
    loadPictures(1)
  }, [searchTag, selectedCategory, loadPictures])

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

  const handleDragStart = (clientX) => {
    touchStartXRef.current = clientX
  }

  const handleDragEnd = (clientX) => {
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
  }

  const handleTouchStart = (e) => handleDragStart(e.touches[0].clientX)
  const handleTouchEnd = (e) => handleDragEnd(e.changedTouches[0].clientX)
  const handleMouseDown = (e) => { e.preventDefault(); handleDragStart(e.clientX) }
  const handleMouseUp = (e) => handleDragEnd(e.clientX)

  const masonryItems = useMasonryItems(pictureList)

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
                        onError={(e) => { e.target.src = FALLBACK_BANNER; setImgLoaded(prev => ({ ...prev, [idx]: true })) }}
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
                      onError={(e) => { e.target.src = FALLBACK_BANNER; setImgLoaded(prev => ({ ...prev, [index]: true })) }}
                    />
                  </div>
                ))}
              </Carousel>
            )}

            {marqueeImages.length > 1 && (
              <>
                <button type="button" className="carousel-arrow-btn carousel-arrow-left" aria-label="上一张" onClick={useCoverflow ? handleCoverflowPrev : handlePrev}>&lsaquo;</button>
                <button type="button" className="carousel-arrow-btn carousel-arrow-right" aria-label="下一张" onClick={useCoverflow ? handleCoverflowNext : handleNext}>&rsaquo;</button>
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
            <div className="home-masonry-item"><AntImage src={getThumbnailUrl(item.data.url, 400)} alt={item.data.pictureName || '图片'} className="home-masonry-image" preview={{ src: item.data.url }} fallback={FALLBACK_THUMB} /></div>
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
