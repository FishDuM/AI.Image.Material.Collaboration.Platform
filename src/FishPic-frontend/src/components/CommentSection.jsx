import { useState, useEffect, useCallback, useContext } from 'react'
import { App, Button, Spin, Empty, Popconfirm, Popover } from 'antd'
import { DeleteOutlined, CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { getCommentList, createComment, deleteComment, reviewComment, adminDeleteComment } from '../api'
import { AuthContext } from '../context/AuthContext'
import { formatTime } from '../utils/constants'
import { logError } from '../utils/logger'
import './CommentSection.css'

const PAGE_SIZE = 10

function CommentItem({ comment, isReply, isAdmin, currentUserId, onReplySubmit, onDelete, onReview, onAdminDelete }) {
  const isOwner = currentUserId != null && currentUserId === comment.userId
  const showAdminActions = isAdmin && !isReply
  const [replyOpen, setReplyOpen] = useState(false)
  const [replyValue, setReplyValue] = useState('')
  const [replySubmitting, setReplySubmitting] = useState(false)

  const handleReplySubmit = async () => {
    const content = replyValue.trim()
    if (!content || replySubmitting) return
    setReplySubmitting(true)
    try {
      await onReplySubmit(content, comment.id, comment.userId)
      setReplyValue('')
      setReplyOpen(false)
    } finally {
      setReplySubmitting(false)
    }
  }

  const handleReplyOpenChange = (open) => {
    if (open) {
      setReplyValue('')
    }
    setReplyOpen(open)
  }

  const replyForm = (
    <div className="comment-reply-popover">
      <div className="comment-reply-popover-header">
        回复 <strong>@{comment.username}</strong>
      </div>
      <div className="comment-reply-popover-body">
        <input
          className="comment-reply-input"
          placeholder={`回复 @${comment.username}...`}
          value={replyValue}
          onChange={(e) => setReplyValue(e.target.value)}
          maxLength={500}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleReplySubmit()
            }
          }}
          autoFocus
        />
        <button
          type="button"
          className="comment-reply-submit-btn"
          disabled={!replyValue.trim() || replySubmitting}
          onClick={handleReplySubmit}
        >
          {replySubmitting ? '...' : '发送'}
        </button>
        <button
          type="button"
          className="comment-reply-cancel-btn"
          onClick={() => setReplyOpen(false)}
        >
          取消
        </button>
      </div>
    </div>
  )

  return (
    <div className={`comment-item ${isReply ? 'comment-item-reply' : ''}`}>
      <div className="comment-item-avatar">
        {comment.avatar ? (
          <img src={comment.avatar} alt={comment.username} />
        ) : (
          <div className="comment-item-avatar-default">
            {comment.username?.charAt(0)?.toUpperCase()}
          </div>
        )}
      </div>
      <div className="comment-item-body">
        <div className="comment-item-header">
          <span className="comment-item-username">{comment.username}</span>
          {comment.toUsername && (
            <span className="comment-item-reply-to">
              回复 <span className="comment-item-reply-target">@{comment.toUsername}</span>
            </span>
          )}
          <span className="comment-item-time">{formatTime(comment.createTime)}</span>
        </div>
        <div className="comment-item-content">{comment.content}</div>
        <div className="comment-item-actions">
          {currentUserId && !isReply && (
            <Popover
              content={replyForm}
              trigger="click"
              open={replyOpen}
              onOpenChange={handleReplyOpenChange}
              placement="bottomLeft"
              overlayClassName="comment-reply-popover-overlay"
              destroyOnHidden
            >
              <button type="button" className="comment-item-action-btn">
                回复
              </button>
            </Popover>
          )}
          {isOwner && (
            <Popconfirm
              title="确定删除这条评论吗？"
              onConfirm={() => onDelete(comment.id)}
              okText="确定"
              cancelText="取消"
            >
              <button type="button" className="comment-item-action-btn comment-item-action-danger">
                删除
              </button>
            </Popconfirm>
          )}
          {showAdminActions && comment.status !== 1 && (
            <button
              type="button"
              className="comment-item-action-btn comment-item-action-approve"
              onClick={() => onReview(comment.id, 1)}
            >
              <CheckOutlined /> 通过
            </button>
          )}
          {showAdminActions && comment.status !== 0 && (
            <button
              type="button"
              className="comment-item-action-btn comment-item-action-reject"
              onClick={() => onReview(comment.id, 0)}
            >
              <CloseOutlined /> 驳回
            </button>
          )}
          {showAdminActions && (
            <Popconfirm
              title="确定强制删除这条评论吗？"
              onConfirm={() => onAdminDelete(comment.id)}
              okText="确定"
              cancelText="取消"
            >
              <button type="button" className="comment-item-action-btn comment-item-action-danger">
                <DeleteOutlined /> 强制删除
              </button>
            </Popconfirm>
          )}
        </div>
        {comment.replies && comment.replies.length > 0 && (
          <div className="comment-item-replies">
            {comment.replies.map((reply) => (
              <CommentItem
                key={reply.id}
                comment={reply}
                isReply
                isAdmin={isAdmin}
                currentUserId={currentUserId}
                onReplySubmit={onReplySubmit}
                onDelete={onDelete}
                onReview={onReview}
                onAdminDelete={onAdminDelete}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export default function CommentSection({ postId, onCommentCountChange, totalCommentCount }) {
  const { message } = App.useApp()
  const { userInfo } = useContext(AuthContext)
  const currentUserId = userInfo?.id
  const isAdmin = userInfo?.role === 'admin'

  const [comments, setComments] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [inputValue, setInputValue] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const fetchComments = useCallback(async (pageNum = 1, append = false) => {
    if (!postId) return
    setLoading(true)
    try {
      const result = await getCommentList({ postId, current: pageNum, pageSize: PAGE_SIZE }, { dedup: true })
      const records = result?.records || []
      const totalCount = result?.total || 0
      if (append) {
        setComments((prev) => [...prev, ...records])
      } else {
        setComments(records)
      }
      setTotal(totalCount)
      setCurrentPage(pageNum)
    } catch (error) {
      logError('fetchComments', error)
    } finally {
      setLoading(false)
    }
  }, [postId])

  useEffect(() => {
    fetchComments(1)
  }, [fetchComments])

  const handleSubmit = useCallback(async () => {
    const content = inputValue.trim()
    if (!content) {
      message.warning('请输入评论内容')
      return
    }
    if (content.length > 500) {
      message.warning('评论内容不能超过500字')
      return
    }
    setSubmitting(true)
    try {
      await createComment({ postId, content })
      message.success('评论成功，等待审核')
      setInputValue('')
      onCommentCountChange?.(1)
      fetchComments(1)
    } catch (err) {
      message.error(err?.message || '评论失败')
    } finally {
      setSubmitting(false)
    }
  }, [inputValue, postId, message, onCommentCountChange, fetchComments])

  const handleReplySubmit = useCallback(async (content, parentId, toUserId) => {
    if (content.length > 500) {
      message.warning('回复内容不能超过500字')
      return
    }
    await createComment({ postId, content, parentId, toUserId })
    message.success('回复成功，等待审核')
    onCommentCountChange?.(1)
    fetchComments(1)
  }, [postId, message, onCommentCountChange, fetchComments])

  const handleDelete = useCallback(async (commentId) => {
    try {
      await deleteComment(commentId)
      message.success('评论已删除')
      onCommentCountChange?.(-1)
      fetchComments(1)
    } catch (err) {
      message.error(err?.message || '删除失败')
    }
  }, [message, onCommentCountChange, fetchComments])

  const handleReview = useCallback(async (commentId, status) => {
    try {
      await reviewComment(commentId, status)
      message.success(status === 1 ? '审核通过' : '已驳回')
      fetchComments(currentPage)
    } catch (err) {
      message.error(err?.message || '操作失败')
    }
  }, [message, fetchComments, currentPage])

  const handleAdminDelete = useCallback(async (commentId) => {
    try {
      await adminDeleteComment(commentId)
      message.success('评论已强制删除')
      onCommentCountChange?.(-1)
      fetchComments(1)
    } catch (err) {
      message.error(err?.message || '删除失败')
    }
  }, [message, onCommentCountChange, fetchComments])

  const handleLoadMore = useCallback(() => {
    fetchComments(currentPage + 1, true)
  }, [fetchComments, currentPage])

  const hasMore = comments.length < total

  return (
    <div className="comment-section">
      <div className="comment-section-header">
        <h3 className="comment-section-title">评论</h3>
        <span className="comment-section-count">{totalCommentCount ?? total}</span>
      </div>

      {currentUserId ? (
        <div className="comment-input-wrapper">
          <div className="comment-input-avatar">
            {userInfo?.avatar ? (
              <img src={userInfo.avatar} alt={userInfo.username} />
            ) : (
              <div className="comment-item-avatar-default">
                {userInfo?.username?.charAt(0)?.toUpperCase()}
              </div>
            )}
          </div>
          <div className="comment-input-area">
            <textarea
              className="comment-input-textarea"
              placeholder="写下你的评论..."
              aria-label="写下你的评论"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              maxLength={500}
              rows={3}
            />
            <div className="comment-input-footer">
              <span className="comment-input-count">{inputValue.length}/500</span>
              <button
                type="button"
                className="comment-submit-btn"
                disabled={!inputValue.trim() || submitting}
                onClick={handleSubmit}
              >
                {submitting ? '发布中...' : '发布'}
              </button>
            </div>
          </div>
        </div>
      ) : (
        <div className="comment-login-hint">登录后参与评论</div>
      )}

      <div className="comment-list">
        {loading && comments.length === 0 ? (
          <div className="comment-list-loading">
            <Spin size="small" />
          </div>
        ) : comments.length === 0 ? (
          <Empty description="暂无评论，快来抢沙发吧" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          comments.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              isReply={false}
              isAdmin={isAdmin}
              currentUserId={currentUserId}
              onReplySubmit={handleReplySubmit}
              onDelete={handleDelete}
              onReview={handleReview}
              onAdminDelete={handleAdminDelete}
            />
          ))
        )}
      </div>

      {hasMore && (
        <div className="comment-load-more">
          <Button
            type="link"
            loading={loading}
            onClick={handleLoadMore}
          >
            加载更多评论
          </Button>
        </div>
      )}

    </div>
  )
}
