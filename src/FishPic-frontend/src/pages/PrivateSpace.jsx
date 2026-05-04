import { useState, useEffect, useCallback, useMemo } from 'react'
import { App as AntApp, Typography, Button, Modal, Form, Input, Pagination, Masonry, Image as AntImage, Spin, Empty } from 'antd'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import { createSpace, updateSpace, listSpace, spaceListPicture } from '../api'
import './PrivateSpace.css'

const { Title } = Typography

const PAGE_SIZE = 20

const PAGINATION_LOCALE = {
  items_per_page: '条/页',
  jump_to: '跳至',
  jump_to_confirm: '确定',
  page: '页',
  prev_page: '上一页',
  next_page: '下一页',
  prev_5: '向前 5 页',
  next_5: '向后 5 页',
  prev_3: '向前 3 页',
  next_3: '向后 3 页',
  page_size: '页码',
}

function PrivateSpace() {
  const { message } = AntApp.useApp()
  const [spaces, setSpaces] = useState([])
  const [showCreate, setShowCreate] = useState(false)
  const [showEdit, setShowEdit] = useState(false)
  const [createLoading, setCreateLoading] = useState(false)
  const [updateLoading, setUpdateLoading] = useState(false)
  const [form] = Form.useForm()
  const [editForm] = Form.useForm()

  const [pictures, setPictures] = useState([])
  const [picturePage, setPicturePage] = useState(1)
  const [pictureTotal, setPictureTotal] = useState(0)
  const [pictureLoading, setPictureLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')

  const fetchSpaces = useCallback(async () => {
    try {
      const result = await listSpace(0)
      const list = Array.isArray(result) ? result : []
      setSpaces(list)
      setShowCreate(list.length === 0)
    } catch {
      setSpaces([])
      setShowCreate(true)
    }
  }, [])

  const fetchPictures = useCallback(async (spaceId, page, keyword) => {
    setPictureLoading(true)
    setPicturePage(page)
    try {
      const params = {
        spaceId,
        current: page,
        pageSize: PAGE_SIZE,
      }
      if (keyword && keyword.trim()) {
        params.keyword = keyword.trim()
      }
      const result = await spaceListPicture(params)
      const list = Array.isArray(result) ? result : []
      setPictures(list)
      if (list.length < PAGE_SIZE) {
        setPictureTotal((page - 1) * PAGE_SIZE + list.length)
      } else {
        setPictureTotal(page * PAGE_SIZE + 1)
      }
    } catch {
      setPictures([])
    } finally {
      setPictureLoading(false)
    }
  }, [])

  useEffect(() => {
    const init = async () => { await fetchSpaces() }
    init()
  }, [fetchSpaces])

  useEffect(() => {
    const load = async () => {
      if (spaces.length > 0 && spaces[0].id) {
        await fetchPictures(spaces[0].id, 1)
      }
    }
    load()
  }, [spaces, fetchPictures])

  const handlePageChange = useCallback((page) => {
    if (spaces.length > 0 && spaces[0].id) {
      fetchPictures(spaces[0].id, page, searchKeyword)
    }
  }, [spaces, fetchPictures, searchKeyword])

  const handleSearch = useCallback(() => {
    if (spaces.length > 0 && spaces[0].id) {
      fetchPictures(spaces[0].id, 1, searchKeyword)
    }
  }, [spaces, fetchPictures, searchKeyword])

  const handleSearchReset = useCallback(() => {
    setSearchKeyword('')
    if (spaces.length > 0 && spaces[0].id) {
      fetchPictures(spaces[0].id, 1, '')
    }
  }, [spaces, fetchPictures])

  const masonryItems = useMemo(() => pictureListToMasonry(pictures), [pictures])

  const handleCreate = async (values) => {
    setCreateLoading(true)
    try {
      await createSpace({
        name: values.name,
        type: 0,
        introduction: values.introduction || '',
      })
      message.success('私人空间创建成功')
      form.resetFields()
      fetchSpaces()
    } catch (error) {
      message.error(error.message || '创建失败')
    } finally {
      setCreateLoading(false)
    }
  }

  const handleEditOpen = () => {
    if (spaces.length > 0) {
      editForm.setFieldsValue({ name: spaces[0].name, introduction: spaces[0].introduction })
      setShowEdit(true)
    }
  }

  const handleUpdate = async (values) => {
    setUpdateLoading(true)
    try {
      await updateSpace({
        id: spaces[0].id,
        name: values.name,
        introduction: values.introduction || '',
      })
      message.success('修改成功')
      setShowEdit(false)
      editForm.resetFields()
      fetchSpaces()
    } catch (error) {
      message.error(error.message || '修改失败')
    } finally {
      setUpdateLoading(false)
    }
  }

  return (
    <main className="private-space-container">
      <div className="private-space-header">
        <div className="private-space-header-left">
          <Title level={2}>
            私人空间{spaces.length > 0 && ` - ${spaces[0].name}`}
          </Title>
          <p className="header-subtitle">
            {spaces.length > 0 && spaces[0].introduction ? spaces[0].introduction : '你的专属私密存储空间'}
          </p>
        </div>
        {spaces.length > 0 && (
          <Button onClick={handleEditOpen}>
            修改空间
          </Button>
        )}
      </div>

      {spaces.length > 0 && (
        <div className="private-space-search-bar">
          <Input
            className="private-space-search-input"
            placeholder="搜索图片..."
            prefix={<SearchOutlined />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onPressEnter={handleSearch}
            allowClear
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            搜索
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleSearchReset}>
            重置
          </Button>
        </div>
      )}

      {spaces.length > 0 && (
        <div className="private-space-masonry-section">
          {pictureLoading && (
            <div className="private-space-loading">
              <Spin />
            </div>
          )}
          {!pictureLoading && masonryItems.length === 0 && (
            <Empty description="暂无图片" style={{ marginTop: 60 }} />
          )}
          {!pictureLoading && masonryItems.length > 0 && (
            <Masonry
              columns={{ xs: 2, sm: 3, md: 4, lg: 5 }}
              gutter={[12, 12]}
              fresh
              items={masonryItems}
              itemRender={(item) => (
                <div className="private-space-masonry-item">
                  <AntImage
                    src={item.data.url}
                    alt=""
                    preview={false}
                    className="private-space-masonry-image"
                  />
                </div>
              )}
            />
          )}
          {pictureTotal > PAGE_SIZE && (
            <div className="private-space-pagination">
              <Pagination
                current={picturePage}
                total={pictureTotal}
                pageSize={PAGE_SIZE}
                onChange={handlePageChange}
                showSizeChanger={false}
                showQuickJumper
                locale={PAGINATION_LOCALE}
              />
            </div>
          )}
        </div>
      )}

      <Modal
        title="修改空间"
        open={showEdit}
        onCancel={() => { setShowEdit(false); editForm.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowEdit(false); editForm.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => editForm.submit()} loading={updateLoading}>
              保存
            </Button>
          </div>
        }
        closable={false}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="空间名称"
            rules={[
              { required: true, message: '请输入空间名称' },
              { max: 20, message: '空间名称不超过 20 个字符' },
            ]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="introduction"
            label="空间介绍"
          >
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="创建私人空间"
        open={showCreate}
        onCancel={() => { setShowCreate(false); form.resetFields() }}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => { setShowCreate(false); form.resetFields() }} style={{ marginRight: 8 }}>
              取消
            </Button>
            <Button type="primary" onClick={() => form.submit()} loading={createLoading}>
              创建
            </Button>
          </div>
        }
        destroyOnHidden
        closable={false}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="空间名称"
            rules={[
              { required: true, message: '请输入空间名称' },
              { max: 20, message: '空间名称不超过 20 个字符' },
            ]}
          >
            <Input placeholder="请输入空间名称" maxLength={20} />
          </Form.Item>
          <Form.Item
            name="introduction"
            label="空间介绍"
          >
            <Input.TextArea placeholder="请输入空间介绍" maxLength={200} rows={3} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </main>
  )
}

function pictureListToMasonry(pictures) {
  return pictures.map((pic) => ({
    key: `pic-${pic.id}`,
    data: pic,
  }))
}

export default PrivateSpace
