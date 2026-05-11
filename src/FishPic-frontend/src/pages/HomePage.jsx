import { useState, useEffect, useContext, useRef, useCallback, useMemo } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { App as AntApp, Button, Modal, Form, Input, Card, Checkbox, Carousel, Masonry, Image as AntImage, Spin } from 'antd'
import { UserOutlined, LockOutlined, LoginOutlined, LogoutOutlined, QrcodeOutlined, ScanOutlined, SearchOutlined } from '@ant-design/icons'
import { getLoginCheckCode, login, getRegisterCheckCode, register, getMarquee, getPictureList } from '../api'
import api from '../api'
import { AuthContext } from '../context/AuthContext.jsx'
import { ThemeContext } from '../context/ThemeContext.jsx'
import '../App.css'

const LOGIN_USER_PREFIX = 'LOGIN_CHECK_CODE-'

function HomePage() {
  const { message } = AntApp.useApp()
  const { login: authLogin } = useContext(AuthContext);
  const navigate = useNavigate()
  const location = useLocation()
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false)
  const [isRegisterMode, setIsRegisterMode] = useState(false)
  const [loginForm] = Form.useForm()
  const [registerForm] = Form.useForm()
  const [loginLoading, setLoginLoading] = useState(false)
  const [registerLoading, setRegisterLoading] = useState(false)
  const [loginCheckCodeUrl, setLoginCheckCodeUrl] = useState('')
  const [loginKey, setLoginKey] = useState('')
  const [registerCheckCodeUrl, setRegisterCheckCodeUrl] = useState('')
  const [registerKey, setRegisterKey] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [marqueeImages, setMarqueeImages] = useState([])
  const [currentSlide, setCurrentSlide] = useState(0)
  const [imgLoaded, setImgLoaded] = useState({})
  const [searchValue, setSearchValue] = useState('')
  const [categoryList, setCategoryList] = useState([])
  const [selectedCategory, setSelectedCategory] = useState('热门')
  const [pictureList, setPictureList] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureLoading, setPictureLoading] = useState(false)
  const [hasMore, setHasMore] = useState(true)
  const carouselRef = useRef(null)
  const loadMoreRef = useRef(null)
  const PAGE_SIZE = 20
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia('(min-width: 1025px)').matches)
  const [coverflowIndex, setCoverflowIndex] = useState(0)
  const coverflowTimerRef = useRef(null)

  const masonryItems = useMemo(() => pictureList.map((pic) => ({
    key: `pic-${pic.id}`,
    data: pic,
  })), [pictureList])

  const handlePrev = useCallback(() => {
    carouselRef.current?.prev()
  }, [])

  const handleNext = useCallback(() => {
    carouselRef.current?.next()
  }, [])

  const handleSearch = useCallback(() => {
    const trimmed = searchValue.trim()
    if (trimmed) {
      navigate(`/community?search=${encodeURIComponent(trimmed)}`)
    } else {
      navigate('/community')
    }
  }, [searchValue, navigate])

  useEffect(() => {
    const fetchMarquee = async () => {
      try {
        const images = await getMarquee()
        if (Array.isArray(images) && images.length > 0) {
          setMarqueeImages(images)
        }
      } catch (error) {
        void error
      }
    }
    fetchMarquee()
  }, [])

  useEffect(() => {
    const fetchCategoryList = async () => {
      try {
        const result = await api.get('/system/list')
        if (Array.isArray(result)) {
          const merged = ['热门', ...result.filter(c => c !== '推荐' && c !== '热门')]
          setCategoryList(merged)
        }
      } catch {
        setCategoryList(['热门'])
      }
    }
    fetchCategoryList()
  }, [])

  const loadPictures = useCallback(async (page) => {
    setPictureLoading(true)
    try {
      const result = await getPictureList(page, PAGE_SIZE)
      if (result && Array.isArray(result.records)) {
        setPictureList(prev => {
          if (page === 1) return result.records
          return [...prev, ...result.records]
        })
        setHasMore(result.records.length === PAGE_SIZE)
      } else {
        setHasMore(false)
      }
    } catch {
      setHasMore(false)
    } finally {
      setPictureLoading(false)
    }
  }, [])

  useEffect(() => {
    loadPictures(1)
  }, [loadPictures])

  useEffect(() => {
    if (!hasMore || pictureLoading) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !pictureLoading) {
          const nextPage = picturePage + 1
          setPicturePage(nextPage)
          loadPictures(nextPage)
        }
      },
      { threshold: 0.1 }
    )
    const el = loadMoreRef.current
    if (el) observer.observe(el)
    return () => { if (el) observer.unobserve(el) }
  }, [hasMore, pictureLoading, picturePage, loadPictures])

  const useCoverflow = isDesktop && marqueeImages.length >= 3

  useEffect(() => {
    const mql = window.matchMedia('(min-width: 1025px)')
    const handler = (e) => setIsDesktop(e.matches)
    mql.addEventListener('change', handler)
    return () => mql.removeEventListener('change', handler)
  }, [])

  useEffect(() => {
    if (!useCoverflow) return
    coverflowTimerRef.current = setInterval(() => {
      setCoverflowIndex((prev) => (prev + 1) % marqueeImages.length)
    }, 4500)
    return () => clearInterval(coverflowTimerRef.current)
  }, [useCoverflow, marqueeImages.length])

  const handleCoverflowPrev = useCallback(() => {
    clearInterval(coverflowTimerRef.current)
    setCoverflowIndex((prev) => (prev - 1 + marqueeImages.length) % marqueeImages.length)
  }, [marqueeImages.length])

  const handleCoverflowNext = useCallback(() => {
    clearInterval(coverflowTimerRef.current)
    setCoverflowIndex((prev) => (prev + 1) % marqueeImages.length)
  }, [marqueeImages.length])

  const handleCoverflowDot = useCallback((idx) => {
    clearInterval(coverflowTimerRef.current)
    setCoverflowIndex(idx)
  }, [])

  const handleCancel = () => {
    if (isRegisterMode) {
      registerForm.resetFields()
    } else {
      loginForm.resetFields()
    }
    setIsLoginModalOpen(false)
    setLoginCheckCodeUrl('')
    setLoginKey('')
    setRegisterCheckCodeUrl('')
    setRegisterKey('')
    setAgreed(false)
  }

  const fetchLoginCheckCode = async () => {
    try {
      const response = await getLoginCheckCode()
      const data = response?.data ?? response
      const inner = data?.data ?? data
      if (inner?.captchaKey && inner?.base64Image) {
        setLoginKey(inner.captchaKey)
        const imageSrc = inner.base64Image.startsWith('data:')
          ? inner.base64Image
          : `data:image/png;base64,${inner.base64Image}`
        setLoginCheckCodeUrl(imageSrc)
      } else {
        message.error('获取验证码失败')
      }
    } catch {
      message.error('获取验证码失败')
    }
  }

  const fetchRegisterCheckCode = async () => {
    try {
      const response = await getRegisterCheckCode()
      const data = response?.data ?? response
      const inner = data?.data ?? data
      if (inner?.captchaKey && inner?.base64Image) {
        setRegisterKey(inner.captchaKey)
        const imageSrc = inner.base64Image.startsWith('data:')
          ? inner.base64Image
          : `data:image/png;base64,${inner.base64Image}`
        setRegisterCheckCodeUrl(imageSrc)
      } else {
        message.error('获取验证码失败')
      }
    } catch {
      message.error('获取验证码失败')
    }
  }

  const handleRefreshLoginCode = () => {
    fetchLoginCheckCode()
    loginForm.setFieldValue('checkCode', '')
  }

  const handleRefreshRegisterCode = () => {
    fetchRegisterCheckCode()
    registerForm.setFieldValue('checkCode', '')
  }

  const switchToRegister = () => {
    setIsRegisterMode(true)
    fetchRegisterCheckCode()
  }

  const switchToLogin = () => {
    setIsRegisterMode(false)
    fetchLoginCheckCode()
  }

  const handleLoginFinish = async (values) => {
    setLoginLoading(true)
    try {
      if (!loginKey) {
        message.error('验证码已过期，请刷新验证码')
        fetchLoginCheckCode()
        loginForm.setFieldValue('checkCode', '')
        setLoginLoading(false)
        return
      }
      
      const loginData = {
        ...values,
        captchaKey: loginKey
      }
      const result = await login(loginData)
      authLogin(result)
      message.success('登录成功')
      setIsLoginModalOpen(false)
      loginForm.resetFields()
      setLoginCheckCodeUrl('')
      setLoginKey('')
      
      const from = location.state?.from?.pathname || '/community'
      navigate(from, { replace: true })
    } catch (error) {
      message.error(error.message || '登录失败，请重试')
      fetchLoginCheckCode()
      loginForm.setFieldValue('checkCode', '')
    } finally {
      setLoginLoading(false)
    }
  }

  const handleRegisterFinish = async (values) => {
    if (!agreed) {
      message.warning('请先阅读并同意用户协议')
      setRegisterLoading(false)
      return
    }
    
    setRegisterLoading(true)
    try {
      if (!registerKey) {
        message.error('验证码已过期，请刷新验证码')
        fetchRegisterCheckCode()
        registerForm.setFieldValue('checkCode', '')
        setRegisterLoading(false)
        return
      }
      
      const registerData = {
        ...values,
        captchaKey: registerKey
      }
      await register(registerData)
      message.success('注册成功，请登录')
      setIsRegisterMode(false)
      registerForm.resetFields()
      setRegisterCheckCodeUrl('')
      setRegisterKey('')
      fetchLoginCheckCode()
    } catch (error) {
      message.error(error.message || '注册失败，请重试')
      fetchRegisterCheckCode()
      registerForm.setFieldValue('checkCode', '')
    } finally {
      setRegisterLoading(false)
    }
  }

  return (
    <>
      {marqueeImages.length > 0 && (
        <div className={`carousel-section${useCoverflow ? ' carousel-section-coverflow' : ''}`}>
          <div className={`carousel-wrapper${useCoverflow ? ' carousel-wrapper-coverflow' : ''}`}>
            {useCoverflow ? (
              <div className="coverflow-container">
                {[-1, 0, 1].map((offset) => {
                  const idx = ((coverflowIndex + offset) % marqueeImages.length + marqueeImages.length) % marqueeImages.length
                  const isActive = offset === 0
                  const url = marqueeImages[idx]
                  return (
                    <div
                      key={`cf-${offset}`}
                      className={`coverflow-slide${isActive ? ' coverflow-slide-active' : ''} coverflow-slide-pos-${offset < 0 ? 'left' : offset === 0 ? 'center' : 'right'}`}
                      onClick={() => {
                        if (offset < 0) handleCoverflowPrev()
                        else if (offset > 0) handleCoverflowNext()
                      }}
                    >
                      {!imgLoaded[idx] && <div className="carousel-skeleton" />}
                      <img
                        src={url}
                        alt={`轮播图 ${idx + 1}`}
                        className="carousel-image coverflow-image"
                        style={{ opacity: imgLoaded[idx] ? 1 : 0 }}
                        onLoad={() => setImgLoaded(prev => ({ ...prev, [idx]: true }))}
                        onError={(e) => {
                          e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwMCIgaGVpZ2h0PSI1MDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEyMDAiIGhlaWdodD0iNTAwIiBmaWxsPSIjMWYxZjFmIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIyMCIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPua2ieWPiuWfuuinpuWNoOe6qTwvdGV4dD48L3N2Zz4='
                          setImgLoaded(prev => ({ ...prev, [idx]: true }))
                        }}
                      />
                      {isActive && marqueeImages.length > 1 && (
                        <>
                          <button
                            type="button"
                            className="coverflow-arrow coverflow-arrow-left"
                            onClick={(e) => {
                              e.stopPropagation()
                              handleCoverflowPrev()
                            }}
                          >
                            ‹
                          </button>
                          <button
                            type="button"
                            className="coverflow-arrow coverflow-arrow-right"
                            onClick={(e) => {
                              e.stopPropagation()
                              handleCoverflowNext()
                            }}
                          >
                            ›
                          </button>
                        </>
                      )}
                    </div>
                  )
                })}
              </div>
            ) : (
              <Carousel
                ref={carouselRef}
                autoplay
                autoplaySpeed={4500}
                dots
                arrows={false}
                speed={600}
                effect="fade"
                fade
                afterChange={(current) => setCurrentSlide(current)}
                pauseOnHover
              >
                {marqueeImages.map((url, index) => (
                  <div key={index} className="carousel-slide">
                    {!imgLoaded[index] && <div className="carousel-skeleton" />}
                    <img
                      src={url}
                      alt={`轮播图 ${index + 1}`}
                      className="carousel-image"
                      style={{ opacity: imgLoaded[index] ? 1 : 0 }}
                      onLoad={() => setImgLoaded(prev => ({ ...prev, [index]: true }))}
                      onError={(e) => {
                        e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwMCIgaGVpZ2h0PSI1MDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEyMDAiIGhlaWdodD0iNTAwIiBmaWxsPSIjMWYxZjFmIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJhcmlhbCIgZm9udC1zaXplPSIyMCIgZmlsbD0iIzZiNmI2YiIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPua2ieWPiuWfuuinpuWNoOe6qTwvdGV4dD48L3N2Zz4='
                        setImgLoaded(prev => ({ ...prev, [index]: true }))
                      }}
                    />
                  </div>
                ))}
              </Carousel>
            )}

            {marqueeImages.length > 1 && (
              <>
                <button
                  type="button"
                  className="carousel-arrow-btn carousel-arrow-left"
                  onClick={useCoverflow ? handleCoverflowPrev : handlePrev}
                >
                  ‹
                </button>
                <button
                  type="button"
                  className="carousel-arrow-btn carousel-arrow-right"
                  onClick={useCoverflow ? handleCoverflowNext : handleNext}
                >
                  ›
                </button>
              </>
            )}

            {marqueeImages.length > 1 && (
              <div className="carousel-counter-badge">
                {(useCoverflow ? coverflowIndex : currentSlide) + 1} / {marqueeImages.length}
              </div>
            )}

            {useCoverflow && (
              <div className="coverflow-dots">
                {marqueeImages.map((_, idx) => (
                  <span
                    key={idx}
                    className={`coverflow-dot${idx === coverflowIndex ? ' coverflow-dot-active' : ''}`}
                    onClick={() => handleCoverflowDot(idx)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      <div className="home-search-section">
        <div className="home-search-bar">
          <Input
            className="home-search-input"
            placeholder="搜索图片、帖子..."
            prefix={<SearchOutlined />}
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
          />
          <Button
            type="primary"
            className="home-search-button"
            icon={<SearchOutlined />}
            onClick={handleSearch}
          >
            搜索
          </Button>
        </div>
      </div>

      {categoryList.length > 0 && (
        <div className="home-category-section">
          <div className="home-category-bar">
            {categoryList.map((cat) => (
              <span
                key={cat}
                className={`home-category-tag ${selectedCategory === cat ? 'home-category-tag-active' : ''}`}
                onClick={() => setSelectedCategory(cat)}
              >
                {cat}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="home-masonry-section">
        {masonryItems.length > 0 && (
          <Masonry
            columns={{ xs: 2, sm: 3, md: 4, lg: 5 }}
            gutter={[12, 12]}
            fresh
            items={masonryItems}
            itemRender={(item) => (
              <div className="home-masonry-item">
                <AntImage
                  src={item.data.url}
                  alt=""
                  className="home-masonry-image"
                />
              </div>
            )}
          />
        )}
        {hasMore && <div ref={loadMoreRef} className="home-load-more" />}
        {pictureLoading && (
          <div className="home-loading-spinner">
            <Spin />
          </div>
        )}
        {!hasMore && masonryItems.length > 0 && (
          <div className="home-no-more">已加载全部图片</div>
        )}
      </div>

      <Modal
        open={isLoginModalOpen}
        onCancel={handleCancel}
        footer={null}
        centered
        className="xhs-modal"
        destroyOnHidden
        width={800}
      >
        <div className="xhs-modal-content">
          <div className="xhs-left-panel">
            <div className="scan-hint">登录后推荐更懂你的笔记</div>
            <div className="qr-card">
              <Card className="qr-code-card" variant="borderless">
                <div className="qr-code-wrapper">
                  <div className="qr-code-bg">
                    <img 
                      src="/qrcode.jpg" 
                      alt="二维码" 
                      className="qr-placeholder"
                      onError={(e) => {
                        e.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQwIiBoZWlnaHQ9IjI0MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjQwIiBoZWlnaHQ9IjI0MCIgZmlsbD0iI2ZmZiIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iYXJpYWwiIGZvbnQtc2l6ZT0iMTQiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj7mlrnlnLbfkYZcL3RleHQ+PC9zdmc+';
                      }}
                    />
                  </div>
                </div>
                <div className="scan-status">
                  <ScanOutlined className="scan-icon" />
                  <span>暂未实现该功能，敬请期待</span>
                </div>
              </Card>
            </div>
            <div className="scan-tips">
              <span>可用</span>
              <span className="app-name">FishPics</span>
              <span>或</span>
              <span className="app-name-wechat">微信</span>
              <span>扫码</span>
            </div>
          </div>
          
          <div className="xhs-right-panel">
            {!isRegisterMode ? (
              <div className="form-container">
                <h2 className="form-title">账号登录</h2>
                <Form
                  form={loginForm}
                  name="login"
                  onFinish={handleLoginFinish}
                  autoComplete="off"
                  size="large"
                  requiredMark={false}
                  layout="vertical"
                >
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入账号' },
                      { min: 6, message: '账号至少 6 个字符' },
                    ]}
                  >
                    <Input
                      prefix={<UserOutlined className="input-icon" />}
                      placeholder="请输入账号"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 8, message: '密码至少 8 个字符' },
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="input-icon" />}
                      placeholder="请输入密码"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="checkCode"
                    rules={[{ required: true, message: '请输入验证码' }]}
                  >
                    <div className="check-code-row xhs">
                      <Input
                        prefix={<LockOutlined className="input-icon" />}
                        placeholder="请输入验证码"
                        className="xhs-input check-code-input"
                        maxLength={5}
                      />
                      <Button 
                        className="get-code-btn" 
                        onClick={handleRefreshLoginCode}
                        type="link"
                      >
                        {loginCheckCodeUrl && (
                          <img src={loginCheckCodeUrl} alt="验证码" className="check-code-img-btn" />
                        )}
                      </Button>
                    </div>
                  </Form.Item>

                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={loginLoading}
                      block
                      className="xhs-submit-btn"
                    >
                      登录
                    </Button>
                  </Form.Item>

                  <div className="switch-form">
                    <span>没有账号？</span>
                    <Button type="link" className="switch-link" onClick={switchToRegister}>
                      立即注册
                    </Button>
                  </div>
                </Form>
              </div>
            ) : (
              <div className="form-container">
                <h2 className="form-title">注册账号</h2>
                <Form
                  form={registerForm}
                  name="register"
                  onFinish={handleRegisterFinish}
                  autoComplete="off"
                  size="large"
                  requiredMark={false}
                  layout="vertical"
                >
                  <Form.Item
                    name="username"
                    rules={[
                      { required: true, message: '请输入账号' },
                      { min: 6, message: '账号至少 6 个字符' },
                    ]}
                  >
                    <Input
                      prefix={<UserOutlined className="input-icon" />}
                      placeholder="请输入账号"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="password"
                    rules={[
                      { required: true, message: '请输入密码' },
                      { min: 8, message: '密码至少 8 个字符' },
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="input-icon" />}
                      placeholder="请输入密码"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="checkPassword"
                    dependencies={['password']}
                    rules={[
                      { required: true, message: '请确认密码' },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (!value || getFieldValue('password') === value) {
                            return Promise.resolve()
                          }
                          return Promise.reject(new Error('两次输入的密码不一致'))
                        },
                      }),
                    ]}
                  >
                    <Input.Password
                      prefix={<LockOutlined className="input-icon" />}
                      placeholder="请确认密码"
                      className="xhs-input"
                    />
                  </Form.Item>

                  <Form.Item
                    name="checkCode"
                    rules={[{ required: true, message: '请输入验证码' }]}
                  >
                    <div className="check-code-row xhs">
                      <Input
                        prefix={<LockOutlined className="input-icon" />}
                        placeholder="请输入验证码"
                        className="xhs-input check-code-input"
                        maxLength={5}
                      />
                      <Button 
                        className="get-code-btn" 
                        onClick={handleRefreshRegisterCode}
                        type="link"
                      >
                        {registerCheckCodeUrl ? (
                          <img src={registerCheckCodeUrl} alt="验证码" className="check-code-img-btn" />
                        ) : null}
                      </Button>
                    </div>
                  </Form.Item>

                  <Form.Item>
                    <div className="agreement-row">
                      <Checkbox checked={agreed} onChange={(e) => setAgreed(e.target.checked)}>
                        <span className="agreement-text">
                          我已阅读并同意《用户协议》《隐私政策》
                        </span>
                      </Checkbox>
                    </div>
                  </Form.Item>

                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={registerLoading}
                      block
                      className="xhs-submit-btn"
                    >
                      注册
                    </Button>
                  </Form.Item>

                  <div className="switch-form">
                    <span>已有账号？</span>
                    <Button type="link" className="switch-link" onClick={switchToLogin}>
                      立即登录
                    </Button>
                  </div>
                </Form>
              </div>
            )}
          </div>
        </div>
      </Modal>
    </>
  )
}

export default HomePage
