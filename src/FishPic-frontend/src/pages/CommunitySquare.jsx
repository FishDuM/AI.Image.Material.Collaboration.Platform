import { useState, useEffect, useRef, useCallback, useContext } from 'react'
import { useSearchParams } from 'react-router-dom'
import { App, Button, Input, Image as AntImage, Masonry, Empty, Spin } from 'antd'
import { PlusOutlined, LikeOutlined, SearchOutlined, ReloadOutlined, UpOutlined } from '@ant-design/icons'
import api from '../api'
import { AuthContext } from '../context/AuthContext'
import PostDetailModal from '../components/PostDetailModal'
import CreateEditPostModal from '../components/CreateEditPostModal'
import './CommunitySquare.css'

function PostCard({ post, onClick }) {
  return (
    <div className="post-card" onClick={() => onClick(post)}>
      {post.url ? (
        <AntImage 
          src={post.url} 
          alt={post.title} 
          className="post-card-image" 
          preview={false}
          style={{ objectFit: 'cover', borderRadius: '12px 12px 0 0', overflow: 'hidden' }}
        />
      ) : (
        <div className="post-card-image-placeholder" />
      )}
      <div className="post-card-content">
        <div className="post-card-title">{post.title}</div>
        <div className="post-card-footer">
          <div className="post-card-author">
            {post.avatar ? (
              <img src={post.avatar} alt={post.username} className="post-card-avatar" />
            ) : (
              <div className="post-card-avatar post-card-avatar-default">{post.username?.charAt(0)?.toUpperCase()}</div>
            )}
            <span className="post-card-username">{post.username}</span>
          </div>
          <div className="post-card-likes">
            <LikeOutlined />
            <span>{post.likesNum || 0}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

function CommunitySquare() {
  const { message } = App.useApp()
  const { userInfo } = useContext(AuthContext)
  const [searchParams, setSearchParams] = useSearchParams()
  const [loading, setLoading] = useState(false)
  const [postList, setPostList] = useState([])
  const [masonryItems, setMasonryItems] = useState([])
  const [postDetailModalOpen, setPostDetailModalOpen] = useState(false)
  const [postDetail, setPostDetail] = useState(null)
  const [postDetailLoading, setPostDetailLoading] = useState(false)
  const [detailImageIndex, setDetailImageIndex] = useState(0)
  const [categoryList, setCategoryList] = useState([])
  const [selectedCategory, setSelectedCategory] = useState(null)
  const [searchText, setSearchText] = useState('')
  const [currentHotPost, setCurrentHotPost] = useState(false)
  const [, setCurrentPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [createEditModalOpen, setCreateEditModalOpen] = useState(false)
  const [editingPostDetail, setEditingPostDetail] = useState(null)
  const pageSize = 20
  const loadMoreRef = useRef(null)
  const currentPageRef = useRef(1)
  const loadingMoreRef = useRef(false)
  const keyCounter = useRef(0)
  const initialPostIdRef = useRef(searchParams.get('id'))

  const fetchPostList = useCallback(async ({ text, hotPost, page = 1, append = false } = {}) => {
    if (append) {
      if (loadingMoreRef.current) return
      loadingMoreRef.current = true
      setLoadingMore(true)
    } else {
      setLoading(true)
    }
    try {
      const params = {
        current: page,
        size: pageSize,
      }
      if (text && text.trim()) {
        params.text = text.trim()
      }
      if (hotPost) {
        params.hotPost = true
      }
      const result = await api.post('/post/postList', params)
      if (result && result.records) {
        const newRecords = result.records
        const newItems = newRecords.map((post) => ({
          key: `post-${post.id}-${keyCounter.current++}`,
          data: post,
        }))
        if (append) {
          setPostList(prev => {
            const existIds = new Set(prev.map(p => p.id))
            const unique = newRecords.filter(p => !existIds.has(p.id))
            return unique.length > 0 ? [...prev, ...unique] : prev
          })
          setMasonryItems(prev => {
            const existIds = new Set(prev.map(item => item.data.id))
            const unique = newItems.filter(item => !existIds.has(item.data.id))
            return unique.length > 0 ? [...prev, ...unique] : prev
          })
        } else {
          keyCounter.current = 0
          setPostList(newRecords)
          setMasonryItems(newItems)
        }
        const totalPages = result.pages || Math.ceil((result.total || 0) / pageSize)
        setCurrentPage(page)
        currentPageRef.current = page
        setHasMore(page < totalPages)
      }
    } catch (err) {
      message.error(err.message || '获取帖子列表失败')
    } finally {
      setLoading(false)
      setLoadingMore(false)
      loadingMoreRef.current = false
    }
  }, [message, pageSize])

  const fetchPostDetail = useCallback(async (postId) => {
    setPostDetailLoading(true)
    setDetailImageIndex(0)
    try {
      const result = await api.get('/post/getPost', { params: { id: postId } })
      if (result) {
        setPostDetail(result)
        setPostDetailModalOpen(true)
        setSearchParams({ id: String(postId) }, { replace: true })
      }
    } catch (err) {
      message.error(err.message || '获取帖子详情失败')
    } finally {
      setPostDetailLoading(false)
    }
  }, [message, setSearchParams])

  const handlePostClick = useCallback((post) => {
    fetchPostDetail(post.id)
  }, [fetchPostDetail])

  const handlePostDetailClose = () => {
    setPostDetailModalOpen(false)
    setPostDetail(null)
    setDetailImageIndex(0)
    setPostDetailLoading(false)
    if (searchParams.get('id')) {
      setSearchParams({}, { replace: true })
    }
  }

  const handleEditPost = () => {
    if (!postDetail) return
    setPostDetailModalOpen(false)
    setEditingPostDetail(postDetail)
    setCreateEditModalOpen(true)
  }

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    setCurrentPage(1)
    currentPageRef.current = 1
    setHasMore(true)
    fetchPostList({ text: searchText, hotPost: currentHotPost, page: 1, append: false })
  }, [fetchPostList])
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    const handleScroll = () => {
      if (loadingMoreRef.current || !hasMore) return
      const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
      const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
      const clientHeight = document.documentElement.clientHeight || window.innerHeight
      if (scrollTop + clientHeight >= scrollHeight - 200) {
        fetchPostList({
          text: searchText,
          hotPost: currentHotPost,
          page: currentPageRef.current + 1,
          append: true,
        })
      }
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [fetchPostList, hasMore, searchText, currentHotPost])

  useEffect(() => {
    const fetchCategoryList = async () => {
      try {
        const result = await api.get('/system/list')
        if (Array.isArray(result)) {
          setCategoryList(result)
          if (result.length > 0) {
            setSelectedCategory(result[0])
          }
        }
      } catch (e) {
        void e
      }
    }
    fetchCategoryList()
  }, [])

  useEffect(() => {
    const postId = initialPostIdRef.current
    if (!postId) return
    initialPostIdRef.current = null
    ;(async () => {
      setPostDetailLoading(true)
      setDetailImageIndex(0)
      try {
        const result = await api.get('/post/getPost', { params: { id: postId } })
        if (result) {
          setPostDetail(result)
          setPostDetailModalOpen(true)
          setSearchParams({ id: String(postId) }, { replace: true })
        }
      } catch (err) {
        message.error(err.message || '获取帖子详情失败')
      } finally {
        setPostDetailLoading(false)
      }
    })()
  }, [])

  useEffect(() => {
    let lastScrollY = window.scrollY
    const handleScroll = () => {
      const header = document.querySelector('.app-header')
      if (!header) return
      const currentScrollY = window.scrollY
      if (currentScrollY > lastScrollY && currentScrollY > 80) {
        header.classList.add('header-hidden')
      } else if (currentScrollY < lastScrollY) {
        header.classList.remove('header-hidden')
      }
      lastScrollY = currentScrollY
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const handleSearch = () => {
    setCurrentHotPost(false)
    setCurrentPage(1)
    currentPageRef.current = 1
    setHasMore(true)
    fetchPostList({ text: searchText, hotPost: false, page: 1, append: false })
  }

  const handleCategoryClick = (cat) => {
    if (selectedCategory === cat) {
      setSelectedCategory(null)
      setCurrentHotPost(false)
      setCurrentPage(1)
      currentPageRef.current = 1
      setHasMore(true)
      fetchPostList({ text: searchText, hotPost: false, page: 1, append: false })
    } else {
      setSelectedCategory(cat)
      setCurrentPage(1)
      currentPageRef.current = 1
      setHasMore(true)
      if (cat === '推荐') {
        setCurrentHotPost(true)
        fetchPostList({ text: searchText, hotPost: true, page: 1, append: false })
      } else {
        setCurrentHotPost(false)
        fetchPostList({ text: searchText, hotPost: false, page: 1, append: false })
      }
    }
  }

  const handleCreatePost = () => {
    setEditingPostDetail(null)
    setCreateEditModalOpen(true)
  }

  const handleRefresh = () => {
    setCurrentPage(1)
    currentPageRef.current = 1
    setHasMore(true)
    fetchPostList({ text: searchText, hotPost: currentHotPost, page: 1, append: false })
  }

  const handleScrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleCreateEditSuccess = () => {
    setCurrentPage(1)
    currentPageRef.current = 1
    setHasMore(true)
    fetchPostList({ text: searchText, hotPost: currentHotPost, page: 1, append: false })
  }

  return (
    <main className="community-square-container">
      <div className="community-square-header">
        <div className="search-area">
          <Input
            placeholder="搜索帖子..."
            prefix={<SearchOutlined />}
            className="search-input"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
          />
          <Button
            type="primary"
            icon={<SearchOutlined />}
            className="search-button"
            onClick={handleSearch}
          >
            搜索
          </Button>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          className="post-button"
          onClick={handleCreatePost}
        >
          发帖
        </Button>
      </div>

      {categoryList.length > 0 && (
        <div className="category-bar">
          {categoryList.map((cat) => (
            <span
              key={cat}
              className={`category-tag ${selectedCategory === cat ? 'category-tag-active' : ''}`}
              onClick={() => handleCategoryClick(cat)}
            >
              {cat}
            </span>
          ))}
        </div>
      )}

      {loading ? (
        <div className="loading-wrapper">
          <Spin size="large" />
        </div>
      ) : postList.length === 0 ? (
        <div className="empty-state-wrapper">
          <Empty description="暂无帖子，快来发布第一条吧" />
        </div>
      ) : (
        <div className="masonry-wrapper">
          <Masonry
            columns={{ xs: 2, sm: 3, md: 4, lg: 5 }}
            gutter={[12, 12]}
            items={masonryItems}
            itemRender={(item) => <PostCard post={item.data} onClick={handlePostClick} />}
          />
          {loadingMore && (
            <div className="load-more-indicator">
              <Spin size="small" />
              <span>加载中...</span>
            </div>
          )}
          {!hasMore && postList.length > 0 && (
            <div className="load-more-indicator">没有更多了</div>
          )}
          <div ref={loadMoreRef} />
        </div>
      )}

      <PostDetailModal
        open={postDetailModalOpen}
        onClose={handlePostDetailClose}
        loading={postDetailLoading}
        postDetail={postDetail}
        detailImageIndex={detailImageIndex}
        onImageIndexChange={setDetailImageIndex}
        currentUsername={userInfo?.username}
        onEdit={handleEditPost}
      />

      <CreateEditPostModal
        open={createEditModalOpen}
        onClose={() => {
          setCreateEditModalOpen(false)
          setEditingPostDetail(null)
        }}
        editPostDetail={editingPostDetail}
        onSuccess={handleCreateEditSuccess}
      />

      <div className="floating-buttons">
        <button type="button" className="floating-btn" onClick={handleCreatePost} title="发帖">
          <PlusOutlined />
        </button>
        <button type="button" className="floating-btn" onClick={handleRefresh} title="刷新">
          <ReloadOutlined />
        </button>
        <button type="button" className="floating-btn" onClick={handleScrollToTop} title="返回顶部">
          <UpOutlined />
        </button>
      </div>
    </main>
  )
}

export default CommunitySquare
