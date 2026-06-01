import { Upload, Image as AntImage } from 'antd'
import { PlusOutlined, DeleteOutlined, LeftOutlined, RightOutlined, FileTextOutlined } from '@ant-design/icons'

/**
 * 共享帖子布局组件
 * 左侧图片轮播 + 右侧自定义内容区
 *
 * Props:
 *  images        - 图片URL数组
 *  currentIndex  - 当前图片索引
 *  onIndexChange - 切换图片回调
 *  onRemove      - 删除图片回调（编辑模式）
 *  onAddImage    - 添加图片回调（编辑模式）
 *  showAddSlide  - 是否显示添加图片占位（编辑模式）
 *  maxImages     - 最大图片数（编辑模式）
 *  touchProps    - 触摸滑动事件 { onTouchStart, onTouchMove, onTouchEnd }
 *  children      - 右侧内容区
 *  emptyImage    - 无图片时显示的提示文字
 *  onAddMore     - 点击右箭头追加图片的回调（编辑模式）
 */
export default function PostLayout({
  images = [],
  currentIndex = 0,
  onIndexChange,
  onRemove,
  onAddImage,
  showAddSlide = false,
  maxImages = 15,
  touchProps = {},
  children,
  emptyImage = '暂无图片',
  onAddMore,
}) {
  const hasImages = images.length > 0
  const isLastImage = currentIndex === images.length - 1
  const canAddMore = images.length < maxImages

  const handlePrevImage = () => {
    if (currentIndex > 0) {
      onIndexChange?.(currentIndex - 1)
    }
  }

  const handleNextImage = () => {
    if (isLastImage && canAddMore) {
      onAddMore?.()
    } else if (currentIndex < images.length - 1) {
      onIndexChange?.(currentIndex + 1)
    }
  }

  const renderLeftSide = () => {
    if (!hasImages) {
      return (
        <div className="no-image-placeholder">
          <FileTextOutlined style={{ fontSize: 64, color: 'rgba(255,255,255,0.3)' }} />
          <p>{emptyImage}</p>
        </div>
      )
    }

    return (
      <div className="carousel-main" {...touchProps}>
        {/* 添加图片占位（编辑模式） */}
        {showAddSlide && canAddMore && (
          <Upload
            listType="picture-card"
            className="carousel-upload"
            customRequest={onAddImage}
            fileList={[]}
            maxCount={maxImages}
            showUploadList={false}
            accept=".jpeg,.png,.jpg,.gif,.webp,.heic"
          >
            <button type="button" className="carousel-upload-btn">
              <PlusOutlined />
              <div className="upload-text">继续上传</div>
            </button>
          </Upload>
        )}

        {/* 图片显示 */}
        {!showAddSlide && currentIndex < images.length && (
          <AntImage
            src={images[currentIndex]}
            alt=""
            className="carousel-main-image"
            preview={true}
          />
        )}

        {/* 删除按钮（编辑模式） */}
        {onRemove && !showAddSlide && currentIndex < images.length && (
          <button
            type="button"
            className="carousel-remove-btn"
            onClick={() => onRemove(images[currentIndex], currentIndex)}
          >
            <DeleteOutlined />
          </button>
        )}

        {/* 左箭头 - 不在第一张图时显示 */}
        {currentIndex > 0 && !showAddSlide && (
          <button
            type="button"
            className="carousel-arrow carousel-arrow-left"
            onClick={handlePrevImage}
          >
            <LeftOutlined />
          </button>
        )}
        {currentIndex > 0 && showAddSlide && (
          <button
            type="button"
            className="carousel-arrow carousel-arrow-left"
            onClick={() => onIndexChange?.(images.length - 1)}
          >
            <LeftOutlined />
          </button>
        )}

        {/* 右箭头 - 不是最后一张，或最后一张但还可以添加时显示 */}
        {!showAddSlide && (currentIndex < images.length - 1 || (isLastImage && canAddMore)) && (
          <button
            type="button"
            className="carousel-arrow carousel-arrow-right"
            onClick={handleNextImage}
          >
            <RightOutlined />
          </button>
        )}

        {/* 计数器 */}
        {!showAddSlide && (
          <div className="carousel-counter">
            {currentIndex + 1} / {images.length}
          </div>
        )}
        {showAddSlide && (
          <div className="carousel-counter">
            {images.length} / {images.length}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="xiaohongshu-layout">
      <div className="left-image-area">
        {renderLeftSide()}
      </div>
      <div className="right-form-area">
        {children}
      </div>
    </div>
  )
}
