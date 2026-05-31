import { useState, useEffect, useRef, useCallback, useContext } from 'react'
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom'
import { App, Button, Masonry, Empty, Spin } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { getPost, getPostList } from '../api'
import { AuthContext } from '../context/AuthContext'
import PostDetailModal from '../components/PostDetailModal'
import CreateEditPostModal from '../components/CreateEditPostModal'
import { useIsMobile } from '../hooks/useIsMobile'
import { useFetchWithCleanup, useSystemTypes } from '../hooks/useRequestUtils'
import SearchBar from '../components/shared/SearchBar.jsx'
import PostCard from '../components/shared/PostCard.jsx'
import CategoryBar from '../components/shared/CategoryBar.jsx'
import './CommunitySquare.css'

function CommunitySquare() {
  const { message } = App.useApp()
  const { userInfo } = useContext(AuthContext)
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const isMobile = useIsMobile()
  const [loading, setLoading] = useState(false)
  const [postList, setPostList] = useState([])
  const [masonryItems, setMasonryItems] = useState([])
  const [postDetailModalOpen, setPostDetailModalOpen] = useState(false)
  const [postDetail, setPostDetail] = useState(null)
  const [postDetailLoading, setPostDetailLoading] = useState(false)
  const [detailImageIndex, setDetailImageIndex] = useState(0)
  const [categoryList, setCategoryList] = useState([])
  const [selectedCategory, setSelectedCategory] = useState('热门')
  const [searchText, setSearchText] = useState('')
  const [currentHotPost, setCurrentHotPost] = useState(true)
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
  const committedSearchRef = useRef({ text: '', hotPost: true, tag: '' })

  const { createSignal } = useFetchWithCleanup()
  const { fetchSystemTypes } = useSystemTypes()

  const fetchPostList = useCallback(async ({ text, hotPost, tag, page = 1, append = false } = {}, signal) => {
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
        pageSize: pageSize,
      }
      if (text && text.trim()) {
        params.text = text.trim()
      }
      if (tag && tag.trim()) {
        params.tag = tag.trim()
      }
      if (hotPost) {
        params.hotPost = true
      }
      const result = await getPostList(params, signal ? { signal } : {})
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
        const totalPages = result.pages ?? Math.ceil((result.total || 0) / pageSize)
        currentPageRef.current = page
        setHasMore(page < totalPages)
      }
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      message.error(err.message || '获取帖子列表失败')
    } finally {
      setLoading(false)
      setLoadingMore(false)
      loadingMoreRef.current = false
    }
  }, [message])

  const fetchPostDetail = useCallback(async (postId) => {
    if (isMobile) {
      navigate(`/mobile/post/detail/${postId}`)
      return
    }
    setPostDetailLoading(true)
    setDetailImageIndex(0)
    try {
      const signal = createSignal()
      const result = await getPost(postId, { signal })
      if (result) {
        setPostDetail(result)
        setPostDetailModalOpen(true)
        setSearchParams({ id: String(postId) }, { replace: true })
      }
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
      message.error(err.message || '获取帖子详情失败')
    } finally {
      setPostDetailLoading(false)
    }
  }, [message, setSearchParams, isMobile, navigate, createSignal])

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

  const handleCommentCountChange = (delta) => {
    setPostDetail((prev) => prev ? { ...prev, commentNum: (prev.commentNum || 0) + delta } : prev)
  }

  const doFetchPostList = useCallback((opts) => {
    const signal = createSignal()
    fetchPostList(opts, signal)
  }, [fetchPostList, createSignal])

  useEffect(() => {
    currentPageRef.current = 1
    setHasMore(true)
    doFetchPostList({ text: searchText, hotPost: currentHotPost, page: 1, append: false })
  }, []) 

  useEffect(() => {
    const handleScroll = () => {
      if (loadingMoreRef.current || !hasMore) return
      const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
      const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
      const clientHeight = document.documentElement.clientHeight || window.innerHeight
      if (scrollTop + clientHeight >= scrollHeight - 200) {
        const { text, hotPost, tag } = committedSearchRef.current
        const signal = createSignal()
        fetchPostList({
          text,
          hotPost,
          tag,
          page: currentPageRef.current + 1,
          append: true,
        }, signal)
      }
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [fetchPostList, hasMore, createSignal])

  useEffect(() => {
    fetchSystemTypes().then((result) => {
      if (Array.isArray(result)) {
        const merged = ['热门', ...result.filter(c => c !== '推荐' && c !== '热门')]
        setCategoryList(merged)
      }
    }).catch(() => {
      setCategoryList(['热门'])
    })
  }, [fetchSystemTypes])

  useEffect(() => {
    const postId = initialPostIdRef.current
    if (!postId) return
    initialPostIdRef.current = null
    if (isMobile) {
      navigate(`/mobile/post/detail/${postId}`)
      return
    }
    ;(async () => {
      setPostDetailLoading(true)
      setDetailImageIndex(0)
      try {
        const signal = createSignal()
        const result = await getPost(postId, { signal })
        if (result) {
          setPostDetail(result)
          setPostDetailModalOpen(true)
          setSearchParams({ id: String(postId) }, { replace: true })
        }
      } catch (err) {
        if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED') return
        message.error(err.message || '获取帖子详情失败')
      } finally {
        setPostDetailLoading(false)
      }
    })()
  }, []) 

  useEffect(() => {
    let lastScrollY = window.scrollY
    const header = document.querySelector('.app-header')
    const handleScroll = () => {
      const currentScrollY = window.scrollY
      if (!header) { lastScrollY = currentScrollY; return }
      if (currentScrollY > lastScrollY && currentScrollY > 80) {
        header.classList.add('header-hidden')
      } else if (currentScrollY < lastScrollY) {
        header.classList.remove('header-hidden')
      }
      lastScrollY = currentScrollY
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', handleScroll)
      if (header) {
        header.classList.remove('header-hidden')
      }
    }
  }, [])

  useEffect(() => {
    if (location.state?.openCreatePost) {
      setCreateEditModalOpen(true)
      navigate('/community', { replace: true, state: {} })
    }
  }, [location.state?.openCreatePost, navigate])

  const handleSearch = () => {
    setCurrentHotPost(false)
    setSelectedCategory(null)
    currentPageRef.current = 1
    setHasMore(true)
    committedSearchRef.current = { text: searchText, hotPost: false, tag: '' }
    doFetchPostList({ text: searchText, hotPost: false, tag: '', page: 1, append: false })
  }

  const handleCategoryClick = (cat) => {
    if (selectedCategory === cat) {
      setSelectedCategory(null)
      setCurrentHotPost(false)
      currentPageRef.current = 1
      setHasMore(true)
      committedSearchRef.current = { text: searchText, hotPost: false, tag: '' }
      doFetchPostList({ text: searchText, hotPost: false, tag: '', page: 1, append: false })
    } else {
      setSelectedCategory(cat)
      currentPageRef.current = 1
      setHasMore(true)
      if (cat === '热门') {
        setCurrentHotPost(true)
        committedSearchRef.current = { text: searchText, hotPost: true, tag: '' }
        doFetchPostList({ text: searchText, hotPost: true, tag: '', page: 1, append: false })
      } else {
        setCurrentHotPost(false)
        committedSearchRef.current = { text: searchText, hotPost: false, tag: cat }
        doFetchPostList({ text: searchText, hotPost: false, tag: cat, page: 1, append: false })
      }
    }
  }

  const handleCreatePost = () => {
    if (isMobile) {
      navigate('/mobile/post/create')
      return
    }
    setEditingPostDetail(null)
    setCreateEditModalOpen(true)
  }

  const handleCreateEditSuccess = () => {
    currentPageRef.current = 1
    setHasMore(true)
    const { text, hotPost, tag } = committedSearchRef.current
    doFetchPostList({ text, hotPost, tag, page: 1, append: false })
  }

  return (
    <main className="community-square-container">
      <div className="community-square-header">
        <SearchBar
          className="community-search"
          placeholder="搜索帖子"
          value={searchText}
          onChange={setSearchText}
          onSearch={handleSearch}
        />
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
        <CategoryBar
          items={categoryList}
          selected={selectedCategory}
          onSelect={handleCategoryClick}
        />
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
        onCommentCountChange={handleCommentCountChange}
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

    </main>
  )
}

export default CommunitySquare