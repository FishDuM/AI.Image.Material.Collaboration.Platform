import { Modal } from 'antd'
import PostLayout from './PostLayout'

/**
 * 共享帖子Modal框组件
 *
 * 统一 Modal 样式（post-detail-modal、80vw、75vh max），内部使用 PostLayout
 *
 * Props:
 *  open          - Modal 是否打开
 *  onClose       - 关闭回调
 *  images        - 图片URL数组 → PostLayout
 *  currentIndex  - 当前图片索引 → PostLayout
 *  onIndexChange - 切换图片回调 → PostLayout
 *  onRemove      - 删除图片回调（编辑模式）→ PostLayout
 *  onAddImage    - 添加图片回调（编辑模式）→ PostLayout
 *  showAddSlide  - 是否显示添加图片占位 → PostLayout
 *  maxImages     - 最大图片数 → PostLayout
 *  touchProps    - 触摸滑动事件 → PostLayout
 *  children      - 右侧内容区 → PostLayout
 */
export default function PostModal({
  open,
  onClose,
  images,
  currentIndex,
  onIndexChange,
  onRemove,
  onAddImage,
  showAddSlide,
  maxImages,
  touchProps,
  children,
  onAddMore,
}) {
  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      className="post-detail-modal"
      closable={false}
      destroyOnHidden
    >
      <PostLayout
        images={images}
        currentIndex={currentIndex}
        onIndexChange={onIndexChange}
        onRemove={onRemove}
        onAddImage={onAddImage}
        showAddSlide={showAddSlide}
        maxImages={maxImages}
        touchProps={touchProps}
        onAddMore={onAddMore}
      >
        {children}
      </PostLayout>
    </Modal>
  )
}
