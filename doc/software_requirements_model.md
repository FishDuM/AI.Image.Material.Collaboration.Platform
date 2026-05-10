# AI.Image.Material.Collaboration.Platform 软件需求模型

## 1. 系统概述

FishPics（FishPics Image Collaboration Platform）是一个基于前后端分离架构的图片分享与协作社区平台。系统为用户提供图片浏览、帖子发布、评论互动、个人空间管理等功能，同时提供管理员后台进行用户管理和内容审核。

### 1.1 项目目标

- 打造美观流畅的图片分享社区体验
- 构建完整的图片-帖子-评论生态系统
- 实现安全可靠的用户权限管理体系
- 提供高性能的内容展示与交互

### 1.2 技术栈

- **前端**: React 19 + Vite 8 + Ant Design 6 + React Router v7 + Context API + Axios + dayjs
- **后端**: Spring Boot 2.7.6 + MyBatis-Plus 3.5.15 + MySQL 8 + Redis + Redisson + Knife4j + Hutool + Lombok + 腾讯云 COS
- **认证**: HTTP Session + Cookie（Spring Session + Redis机制，依赖CORS allowCredentials）
- **密码安全**: MD5 + 盐值"fish"

### 1.3 系统架构

- 前后端分离架构
- 后端RESTful API设计，基础路径：`/api`
- 前端单页应用（SPA），默认端口：5173
- 后端服务端口：8080
- MySQL数据库：FishPics
- Redis缓存：6379端口（用于Session管理、验证码存储、用户信息缓存、分类标签缓存、跑马灯配置缓存）
- 腾讯云COS对象存储：海量图片资源存储
- Vite代理：开发环境通过`/api`代理转发请求至后端

---

## 2. 功能需求分析

### 2.1 核心功能模块

#### 2.1.1 用户认证模块

- **用户注册**
  - 输入：用户名（6-11字符）、密码（8-20字符）、确认密码、图形验证码
  - 校验：用户名唯一性、密码一致性、验证码正确性
  - 默认值：昵称"小鱼籽\_+随机字符串"、角色"user"
- **用户登录**
  - 输入：用户名、密码、图形验证码
  - 流程：验证码校验 → 用户查询 → 密码比对（MD5+Salt） → 将用户ID存入HTTP Session → 将用户信息缓存到Redis → 返回用户信息
  - Session机制：Spring Session + Redis管理Session ID通过Cookie传输
  - Redis缓存：登录用户信息以JSON格式存储在Redis中（KEY: USER_ID:{userId}），后续请求通过Session获取userId后从Redis读取用户信息
- **退出登录**
  - 清除本地localStorage中的用户信息
  - 前端状态重置为未登录
- **验证码生成**
  - 类型：圆圈图形验证码（Hutool CircleCaptcha）
  - 长度：5位
  - 有效期：5分钟
  - 存储：Redis
  - 返回：Base64编码图片 + captchaKey

#### 2.1.2 用户信息管理模块

- **查看个人信息**
  - 获取：用户基本信息 + 发布帖子列表 + 收藏帖子列表 + 点赞帖子列表
  - 权限：仅本人可访问（Session验证）
- **编辑个人信息**
  - 可修改：头像、邮箱、手机号、昵称、密码
  - 权限：仅本人可修改（isMe校验）
- **获取用户信息**
  - 接口：GET /api/user/getUser
  - 功能：获取当前登录用户的基本信息
- **管理员用户管理**
  - 查询用户列表（分页、多条件搜索）
  - 修改用户状态（封禁/解封）
  - 编辑用户信息（包括角色、状态）
  - 获取单个用户详情
  - 权限：仅管理员（role=admin）

#### 2.1.3 图片管理模块

- **头像上传**
  - 接口：POST /api/picture/avatar
  - 功能：用户头像上传，文件大小限制5MB
  - 参数：file（图片文件）、id（用户ID）
- **帖子图片上传**
  - 接口：POST /api/picture/post
  - 功能：帖子相关图片上传，文件大小限制5MB
  - 参数：file（图片文件）
  - 返回：PicturePostVO（图片URL、ID等信息）
- **图片信息管理**
  - 图片名称、URL、尺寸（宽、高）、大小
  - 图片状态管理（1-正常、0-禁用、2-待审核）
  - 图片隐私设置（isPrivate：0-不公开到首页，1-公开到首页）
  - 图片关联用户和帖子
- **图片与帖子关联**
  - 设置图片的postId关联
  - 支持多图片关联到同一帖子

#### 2.1.4 帖子管理模块

- **帖子发布**
  - 接口：POST /api/post/post
  - 请求：UploadPostRequest（标题、内容、图片ID列表等）
  - 功能：创建新帖子，关联图片
- **帖子展示**
  - 获取帖子详情：GET /api/post/getPost?id={id}
  - 获取帖子列表：POST /api/post/postList
  - 支持分页查询和多条件筛选
- **帖子编辑**
  - 接口：POST /api/post/editPost
  - 请求：EditPostRequest
  - 权限：仅帖子作者可编辑
- **帖子统计**
  - 点赞数（likesNum）
  - 收藏数（collectsNum）
  - 评论数（commentNum）
  - 查看数（viewsNum）
- **帖子状态管理**
  - 状态：1-正常、0-禁用、2-待审核
  - 隐私设置：isPrivate（0-公开，1-仅自己可见）
  - 逻辑删除机制（@TableLogic）
  - 封面图片（cover字段）

#### 2.1.5 评论互动模块

- **评论功能**
  - 对帖子发表评论
  - 支持二级评论/回复（parentId、toUserId）
  - 评论状态管理（1-正常、0-禁用、2-待审核）
  - 评论关联帖子和用户

#### 2.1.6 社交关系模块

- **粉丝功能**
  - 粉丝关系记录（user_fans表：userId、fanId）
  - 粉丝列表管理
  - 关注关系通过粉丝表双向维护
- **收藏功能**
  - 收藏帖子（user_post_collect表：userId、postId）
  - 收藏列表管理
  - 接口：POST /api/post/myCollects（获取本人收藏的帖子列表）
- **点赞功能**
  - 点赞帖子（user_post_likes表：userId、postId）
  - 点赞列表管理
  - 接口：POST /api/post/like?id={id}
  - 接口：POST /api/post/myLikes（获取本人点赞的帖子列表）

#### 2.1.7 隐私设置模块

- 关注列表隐私：isPrivateFollows（0-公开，1-不公开）
- 收藏列表隐私：isPrivatePostCollect（0-公开，1-不公开）
- 点赞列表隐私：isPrivateLikes（0-公开，1-不公开）
- 粉丝列表隐私：isPrivateFans（0-公开，1-不公开）

#### 2.1.8 后台管理模块

- 用户列表查询（分页+多条件搜索）
- 用户状态管理（封禁/解封）
- 用户信息编辑（包括角色和状态）
- 获取单个用户详情
- 图片管理（分页查看、按状态筛选、审核通过/拒绝、设置精选）
- 空间管理（管理所有空间配置和资源）
- 团队管理（管理团队成员和配置）
- AI管理（AI相关功能配置，开发中）
- 系统管理（帖子分类标签管理、首页跑马灯图片管理）
- 权限控制（@AuthCheck + AuthInterceptor AOP）
- API文档自动生成（Knife4j）

#### 2.1.9 系统功能模块

- **分类标签管理**
  - 获取标签列表：GET /api/system/list
  - 添加标签：POST /api/system/addList
  - 删除标签：POST /api/system/deleteType
  - 存储：Redis（KEY: type_list_key）
- **跑马灯图片管理**
  - 获取跑马灯图片列表：GET /api/system/marquee
  - 添加跑马灯图片：POST /api/system/addMarquee
  - 删除跑马灯图片：POST /api/system/deleteMarquee
  - 存储：Redis（KEY: marquees_key）

#### 2.1.10 空间管理模块

- **空间创建**
  - 接口：POST /api/space/create
  - 功能：创建私人空间或团队空间
  - 参数：name（空间名）、introduction（空间介绍）、type（0-私人空间，1-团队空间）
  - 默认存储大小：512MB（普通用户）/ 5GB（VIP）/ 10GB（SVIP）
- **空间列表查询**
  - 接口：GET /api/space/list?type={type}
  - 功能：按类型获取用户的空间列表（0-私人空间，1-团队空间）
- **空间信息更新**
  - 接口：POST /api/space/update
  - 功能：更新空间名称和介绍
  - 参数：id（空间ID）、name、introduction
- **空间图片列表**
  - 接口：POST /api/space/pictureList
  - 功能：获取指定空间下的图片列表（分页）
  - 参数：spaceId（空间ID）、current、pageSize

#### 2.1.11 图片管理扩展模块

- **图片列表查询（公开）**
  - 接口：GET /api/picture/list?current={current}&pageSize={pageSize}
  - 功能：分页获取公开图片列表
- **图片删除**
  - 接口：POST /api/picture/delete
  - 功能：删除图片（支持批量删除）
  - 参数：ids（图片ID列表）
- **管理员图片管理**
  - 获取图片列表：GET /api/picture/admin/list?current={current}&pageSize={pageSize}&status={status}
  - 审核图片：POST /api/picture/admin/review?pictureId={pictureId}&status={status}&selected={selected}
  - 支持按状态筛选（全部/正常/待审核/禁用/精选）

#### 2.1.12 帖子管理扩展模块

- **本人帖子列表**
  - 接口：POST /api/post/myPosts
  - 功能：获取当前登录用户发布的帖子列表（分页）
- **本人收藏列表**
  - 接口：POST /api/post/myCollects
  - 功能：获取当前登录用户收藏的帖子列表（分页）
- **本人点赞列表**
  - 接口：POST /api/post/myLikes
  - 功能：获取当前登录用户点赞的帖子列表（分页）

#### 2.1.13 前端页面模块

- **主页**（HomePage）：平台首页，登录/注册入口
- **社区广场**（CommunitySquare）：社区内容展示，瀑布流布局，支持分类标签筛选、搜索、发帖、编辑、帖子详情弹窗、返回顶部按钮（向下滚动100px后显示，平滑滚动）
- **私人空间**（PrivateSpace）：用户个人私密内容管理
- **团队空间**（TeamSpace）：团队协作内容管理
- **通知中心**（Notifications）：用户通知消息（评论互动、赞和收藏、新增关注、系统通知、私信五个分类）
- **用户资料**（UserProfile）：个人资料查看与编辑
- **用户管理**（UserManagement）：管理员查看和编辑用户信息
- **管理员用户列表**（AdminUserList）：管理员用户列表查询
- **图片管理**（AdminPictureManagement）：管理员图片审核管理
- **团队管理**（TeamManagement）：团队信息管理（开发中）
- **空间管理**（SpaceManagement）：空间配置管理（开发中）
- **AI管理**（AIManagement）：AI相关功能管理（开发中）
- **系统管理**（SystemManagement）：帖子分类标签管理、首页跑马灯图片管理
- **404页面**（NotFound）：未找到页面

#### 2.1.14 前端通用组件

- **GlobalLayout**：全局布局（导航栏、侧边栏、登录模态框，支持滚动隐藏导航栏）
- **ProtectedRoute**：路由权限保护（支持requireAdmin属性）
- **ErrorBoundary**：错误边界处理
- **FunnyBackground**：趣味背景动画（浮动emoji）
- **PostDetailModal**：帖子详情弹窗（小红书风格，左右分栏，左侧图片轮播，右侧内容与互动数据）
- **CreateEditPostModal**：帖子发布/编辑弹窗（左右分栏布局，支持多图片上传、封面选择、隐私设置）
- **AuthContext**：认证状态管理（登录、登出、用户信息）
- **ThemeContext**：主题状态管理（明暗主题切换）
- **API封装**：Axios请求配置（请求/响应拦截器，统一错误处理）
- **Storage工具**：本地存储管理（用户信息）

---

## 3. 数据模型分析

### 3.1 实体清单

系统包含 **10个核心实体**：

1. **User** (用户表) - 系统用户信息
2. **Post** (帖子表) - 用户发布的帖子
3. **Picture** (图片表) - 图片资源信息
4. **PictureChild** (子图片表) - 帖子与图片的关联关系（含排序序号）
5. **Comment** (评论表) - 帖子评论
6. **Space** (空间表) - 私人空间和团队空间
7. **PicSystem** (系统表) - 系统配置信息（分类标签、跑马灯等）
8. **UserFans** (用户粉丝表) - 粉丝关系记录
9. **UserPostCollect** (用户帖子收藏表) - 收藏关系记录
10. **UserPostLikes** (用户点赞帖子表) - 点赞关系记录

### 3.2 实体关系图 (ERD)

```
User (1) ───< (N) Post
User (1) ───< (N) Picture
User (1) ───< (N) Comment
User (1) ───< (N) Space
Post (1) ───< (N) Comment
Post (1) ───< (N) PictureChild (通过postId关联，含排序)
PictureChild (N) >── (1) Picture (通过pictureId关联)
Post (1) ───< (N) Picture (通过postId关联)
Post (1) ───< (N) UserPostCollect
Post (1) ───< (N) UserPostLikes
Space (1) ───< (N) Picture (通过spaceId关联)
User (1) ───< (N) UserFans
User (1) ───< (N) UserPostCollect
User (1) ───< (N) UserPostLikes
```

### 3.3 数据库表结构详情

#### 3.3.1 User 表

| 字段                    | 类型         | 描述                              | 约束                                |
| ----------------------- | ------------ | --------------------------------- | ----------------------------------- |
| id                      | bigint       | 用户ID                            | PRIMARY KEY, AUTO_INCREMENT         |
| username                | varchar(32)  | 用户名（登录用）                  | UNIQUE                              |
| password                | varchar(128) | 密码（MD5+Salt）                  | NOT NULL                            |
| avatar                  | varchar(256) | 头像URL                           | -                                   |
| email                   | varchar(64)  | 邮箱                              | -                                   |
| phone                   | varchar(16)  | 手机号                            | -                                   |
| nickname                | varchar(32)  | 昵称（展示用）                    | UNIQUE                              |
| status                  | tinyint      | 状态 (1-正常, 0-禁用, 2-待审核)   | DEFAULT 1                           |
| is_delete               | tinyint      | 逻辑删除标记 (0-未删除, 1-已删除) | DEFAULT 0, @TableLogic              |
| role                    | varchar(32)  | 用户权限 (admin/user)             | DEFAULT 'user'                      |
| create_time             | datetime     | 创建时间                          | DEFAULT CURRENT_TIMESTAMP           |
| update_time             | datetime     | 更新时间                          | DEFAULT CURRENT_TIMESTAMP ON UPDATE |
| like_num                | bigint       | 点赞数                            | -                                   |
| collect_num             | bigint       | 收藏数                            | -                                   |
| is_private_follows      | tinyint      | 关注列表隐私 (0-公开, 1-不公开)   | -                                   |
| is_private_post_collect | tinyint      | 收藏列表隐私 (0-公开, 1-不公开)   | -                                   |
| is_private_likes        | tinyint      | 点赞列表隐私 (0-公开, 1-不公开)   | -                                   |
| is_private_fans         | tinyint      | 粉丝列表隐私 (0-公开, 1-不公开)   | -                                   |

#### 3.3.2 Post 表

| 字段         | 类型         | 描述                            | 约束                                |
| ------------ | ------------ | ------------------------------- | ----------------------------------- |
| id           | bigint       | 主键                            | PRIMARY KEY, AUTO_INCREMENT         |
| user_id      | bigint       | 关联用户ID                      | NOT NULL, FOREIGN KEY               |
| title        | varchar(256) | 标题                            | NOT NULL                            |
| content      | text         | 内容                            | NOT NULL                            |
| status       | tinyint      | 状态 (1-正常, 0-禁用, 2-待审核) | DEFAULT 1                           |
| is_delete    | tinyint      | 逻辑删除标记                    | DEFAULT 0, @TableLogic              |
| create_time  | datetime     | 创建时间                        | DEFAULT CURRENT_TIMESTAMP           |
| update_time  | datetime     | 更新时间                        | DEFAULT CURRENT_TIMESTAMP ON UPDATE |
| likes_num    | bigint       | 点赞数                          | -                                   |
| collects_num | bigint       | 收藏数                          | -                                   |
| comment_num  | int          | 评论数                          | -                                   |
| is_private   | tinyint      | 隐私设置 (0-公开, 1-仅自己可见) | -                                   |
| cover        | bigint       | 封面图片的ID                    | -                                   |
| views_num    | bigint       | 查看数                          | -                                   |
| hot          | decimal      | 热度值                          | DEFAULT 0                           |

**注意**: Post表不包含picture_ids字段，图片通过Picture表的postId字段关联，同时通过PictureChild表维护帖子与图片的有序关联关系

#### 3.3.3 Picture 表

| 字段         | 类型         | 描述                                    | 约束                                |
| ------------ | ------------ | --------------------------------------- | ----------------------------------- |
| id           | bigint       | 主键                                    | PRIMARY KEY, AUTO_INCREMENT         |
| user_id      | bigint       | 用户ID                                  | NOT NULL, FOREIGN KEY               |
| picture_name | bigint       | 图片名称                                | NOT NULL                            |
| url          | varchar(512) | 图片地址                                | NOT NULL                            |
| width        | varchar(32)  | 宽度                                    | -                                   |
| height       | varchar(32)  | 高度                                    | -                                   |
| size         | bigint       | 大小（字节）                            | -                                   |
| status       | tinyint      | 状态 (1-正常, 0-禁用, 2-待审核)         | DEFAULT 2                           |
| create_time  | datetime     | 创建时间                                | DEFAULT CURRENT_TIMESTAMP           |
| update_time  | datetime     | 更新时间                                | DEFAULT CURRENT_TIMESTAMP ON UPDATE |
| is_private   | tinyint      | 隐私设置 (0-不公开到首页, 1-公开到首页) | DEFAULT 0, NOT NULL                 |
| post_id      | bigint       | 帖子ID（关联到Post表）                  | -                                   |
| space_id     | bigint       | 空间ID（关联到Space表）                 | -                                   |
| introduction | varchar(256) | 图片介绍                                | -                                   |

**注意**: Picture表包含postId字段用于关联帖子，包含spaceId字段用于关联空间

#### 3.3.4 PictureChild 表

| 字段       | 类型    | 描述                | 约束                        |
| ---------- | ------- | ------------------- | --------------------------- |
| id         | bigint  | 主键                | PRIMARY KEY, AUTO_INCREMENT |
| picture_id | bigint  | 关联图片ID          | NOT NULL, FOREIGN KEY       |
| post_id    | bigint  | 关联帖子ID          | NOT NULL, FOREIGN KEY       |
| sort_num   | int     | 在帖子中的排序序号  | -                           |

**注意**: PictureChild表用于维护帖子与图片的多对多有序关联，getPost时按sortNum排序获取图片列表

#### 3.3.5 Comment 表

| 字段        | 类型     | 描述                            | 约束                        |
| ----------- | -------- | ------------------------------- | --------------------------- |
| id          | bigint   | 主键                            | PRIMARY KEY, AUTO_INCREMENT |
| user_id     | bigint   | 关联用户ID                      | NOT NULL, FOREIGN KEY       |
| post_id     | bigint   | 关联帖子ID                      | NOT NULL, FOREIGN KEY       |
| content     | text     | 评论内容                        | NOT NULL                    |
| parent_id   | bigint   | 父评论ID（支持二级评论）        | -                           |
| to_user_id  | tinyint  | 回复给谁                        | -                           |
| status      | tinyint  | 状态 (1-正常, 0-禁用, 2-待审核) | DEFAULT 1                   |
| create_time | datetime | 创建时间                        | DEFAULT CURRENT_TIMESTAMP   |

**注意**: Comment表不包含update_time字段

#### 3.3.6 Space 表

| 字段          | 类型          | 描述                               | 约束                        |
| ------------- | ------------- | ---------------------------------- | --------------------------- |
| id            | bigint        | 空间ID                             | PRIMARY KEY, AUTO_INCREMENT |
| introduction  | varchar(256)  | 空间介绍                           | -                           |
| type          | tinyint       | 空间类型 (0-私人空间, 1-团队空间)  | -                           |
| team_users_id | varchar(1024) | 团队空间的用户ID（JSON数组）       | -                           |
| user_id       | bigint        | 创建者用户ID                       | -                           |
| storage_size  | bigint        | 空间存储大小(KB)，默认512MB=524288 | DEFAULT 524288              |
| level         | tinyint       | 空间级别 (0-普通, 1-VIP, 2-SVIP)   | DEFAULT 0, NOT NULL         |
| name          | varchar(246)  | 空间名                             | NOT NULL                    |
| size          | bigint        | 当前已使用大小                     | -                           |

#### 3.3.7 PicSystem 表

| 字段     | 类型          | 描述 | 约束                        |
| -------- | ------------- | ---- | --------------------------- |
| id       | bigint        | 主键 | PRIMARY KEY, AUTO_INCREMENT |
| syskey   | varchar(256)  | 键名 | NOT NULL, UNIQUE            |
| sysvalue | varchar(1024) | 值   | -                           |

#### 3.3.8 UserFans 表

| 字段    | 类型   | 描述   |
| ------- | ------ | ------ |
| id      | bigint | 主键   |
| user_id | bigint | 用户ID |
| fan_id  | bigint | 粉丝ID |

#### 3.3.9 UserPostCollect 表

| 字段    | 类型   | 描述   |
| ------- | ------ | ------ |
| id      | bigint | 主键   |
| user_id | bigint | 用户ID |
| post_id | bigint | 帖子ID |

#### 3.3.10 UserPostLikes 表

| 字段    | 类型   | 描述   |
| ------- | ------ | ------ |
| id      | bigint | 主键   |
| user_id | bigint | 用户ID |
| post_id | bigint | 帖子ID |

### 3.4 数据库索引

| 表                | 索引名                          | 字段            | 说明           |
| ----------------- | ------------------------------- | --------------- | -------------- |
| user              | uk_username                     | username        | 用户名唯一索引 |
| user              | uk_nickname                     | nickname        | 昵称唯一索引   |
| post              | idx_user_id                     | user_id         | 用户ID索引     |
| post              | idx_title                       | title           | 标题索引       |
| post              | post_content_index              | content(100)    | 内容前缀索引   |
| post              | post_status_index               | status          | 状态索引       |
| picture           | idx_user_id                     | user_id         | 用户ID索引     |
| picture           | idx_picture_name                | picture_name    | 图片名称索引   |
| picture           | picture_introduction_index      | introduction    | 图片介绍索引   |
| picture           | picture_space_id_index          | space_id        | 空间ID索引     |
| picture           | picture_update_time_index       | update_time     | 更新时间索引   |
| comment           | idx_user_id                     | user_id         | 用户ID索引     |
| comment           | idx_post_id                     | post_id         | 帖子ID索引     |
| space             | space_user_id_index             | user_id         | 创建者ID索引   |
| space             | space_type_index                | type            | 空间类型索引   |
| user_fans         | user_fans_user_id_fan_id_index  | user_id, fan_id | 联合索引       |
| user_post_collect | user_post_collect_user_id_index | user_id         | 用户ID索引     |
| user_post_likes   | user_post_likes_user_id_index   | user_id         | 用户ID索引     |

---

## 4. 接口定义

### 4.1 API基础信息

- **基础路径**: `/api`
- **请求格式**: JSON (application/json) 或 multipart/form-data（文件上传）
- **认证方式**: HTTP Session（Spring Session机制，通过Cookie自动传输Session ID）
- **响应格式**: 统一 Response<T> 结构 { code, message, data }
  - code: 1-成功，其他-失败
  - message: 响应信息
  - data: 响应数据

### 4.2 公开接口（无需登录）

#### 4.2.1 获取登录验证码

- **接口**: `GET /api/user/checkCode/login`
- **描述**: 生成登录用图形验证码
- **返回**: CheckCodeVO (captchaKey, base64Image)

#### 4.2.2 获取注册验证码

- **接口**: `GET /api/user/checkCode/register`
- **描述**: 生成注册用图形验证码
- **返回**: CheckCodeVO (captchaKey, base64Image)

#### 4.2.3 用户登录

- **接口**: `POST /api/user/login`
- **描述**: 用户登录，登录成功后服务端自动创建Session并通过Cookie返回Session ID
- **请求体**: UserLoginRequest
  - username: 用户名
  - password: 密码
  - checkCode: 验证码
  - captchaKey: 验证码Key
- **返回**: UserLoginVO
  - id: 用户ID
  - username: 用户名
  - avatar: 头像
  - email: 邮箱
  - phone: 手机号
  - role: 角色
  - nickname: 昵称

#### 4.2.4 用户注册

- **接口**: `POST /api/user/register`
- **描述**: 新用户注册
- **请求体**: UserRequestRequest
  - username: 用户名（6-11字符）
  - password: 密码（8-20字符）
  - checkPassword: 确认密码
  - checkCode: 验证码
  - captchaKey: 验证码Key
- **返回**: Boolean (成功/失败)

#### 4.2.5 获取系统分类列表

- **接口**: `GET /api/system/list`
- **描述**: 获取系统预设的分类列表
- **返回**: List<String> ["推荐", "穿搭", "美食", "旅行", "宠物", "运动"]

### 4.3 用户私有接口（需要登录，依赖Session）

#### 4.3.1 获取个人信息

- **接口**: `GET /api/user/myself`
- **描述**: 获取当前登录用户的完整信息（基本信息+帖子列表+收藏列表+点赞列表）
- **认证**: HTTP Session（服务端从request.getSession()获取用户信息）
- **返回**: UserMessageVO
  - id, username, avatar, email, phone, nickname, role, createTime
  - postList: 我的发布帖子列表
  - postCollectList: 我的收藏帖子列表
  - postLikeList: 我的点赞帖子列表

#### 4.3.2 获取当前用户信息

- **接口**: `GET /api/user/getUser`
- **描述**: 获取当前登录用户的基本信息
- **认证**: HTTP Session
- **返回**: UserLoginVO

#### 4.3.3 编辑个人信息

- **接口**: `POST /api/user/editUser`
- **描述**: 编辑当前登录用户的信息
- **认证**: HTTP Session + isMe校验（仅可修改自己的信息）
- **请求体**: UserEditRequest
  - id: 用户ID
  - username: 用户名
  - password: 密码（可修改）
  - avatar: 头像
  - email: 邮箱
  - phone: 手机号
  - nickname: 昵称
- **返回**: Boolean (成功/失败)

#### 4.3.4 上传头像

- **接口**: `POST /api/picture/avatar`
- **描述**: 用户上传头像或管理员修改用户头像
- **认证**: HTTP Session
- **请求类型**: multipart/form-data
- **请求参数**:
  - file: 图片文件（最大5MB）
  - id: 用户ID
- **返回**: String (头像URL)

#### 4.3.5 上传帖子图片

- **接口**: `POST /api/picture/post`
- **描述**: 用户上传帖子相关图片
- **认证**: HTTP Session
- **请求类型**: multipart/form-data
- **请求参数**:
  - file: 图片文件（最大5MB）
- **返回**: PicturePostVO

#### 4.3.6 发布帖子

- **接口**: `POST /api/post/post`
- **描述**: 创建新帖子
- **认证**: HTTP Session
- **请求体**: UploadPostRequest
  - title: 标题
  - content: 内容
  - 其他帖子相关字段
- **返回**: Boolean (成功/失败)

#### 4.3.7 获取帖子详情

- **接口**: `GET /api/post/getPost?id={id}`
- **描述**: 获取指定帖子的详细信息
- **请求参数**: id (帖子ID)
- **返回**: PostDetailVO
  - id: 帖子ID
  - userId: 作者ID
  - username: 作者用户名
  - avatar: 作者头像
  - title: 标题
  - content: 内容
  - updateTime: 更新时间
  - likesNum: 点赞数
  - collectsNum: 收藏数
  - commentNum: 评论数
  - pictureUrl: 图片URL列表（按PictureChild.sortNum排序，已同步过滤已删除图片）
  - pictureIds: 图片ID列表（与pictureUrl一一对应）
  - cover: 封面图片ID

#### 4.3.8 编辑帖子

- **接口**: `POST /api/post/editPost`
- **描述**: 编辑帖子内容
- **认证**: HTTP Session（仅作者可编辑）
- **请求体**: EditPostRequest
- **返回**: Boolean (成功/失败)

#### 4.3.9 点赞帖子

- **接口**: `POST /api/post/like?id={id}`
- **描述**: 点赞指定帖子
- **认证**: HTTP Session
- **请求参数**: id (帖子ID)
- **返回**: Boolean (成功/失败)

#### 4.3.10 获取本人帖子列表

- **接口**: `POST /api/post/myPosts`
- **描述**: 获取当前登录用户发布的帖子列表（分页）
- **认证**: HTTP Session
- **请求体**: PageRequest (current, pageSize)
- **返回**: IPage<PostListVO>

#### 4.3.11 获取本人收藏列表

- **接口**: `POST /api/post/myCollects`
- **描述**: 获取当前登录用户收藏的帖子列表（分页）
- **认证**: HTTP Session
- **请求体**: PageRequest (current, pageSize)
- **返回**: IPage<PostListVO>

#### 4.3.12 获取本人点赞列表

- **接口**: `POST /api/post/myLikes`
- **描述**: 获取当前登录用户点赞的帖子列表（分页）
- **认证**: HTTP Session
- **请求体**: PageRequest (current, pageSize)
- **返回**: IPage<PostListVO>

#### 4.3.13 退出登录

- **接口**: `GET /api/user/logout`
- **描述**: 退出登录，清除Session
- **认证**: HTTP Session
- **返回**: Boolean (成功/失败)

### 4.4 公共查询接口

#### 4.4.1 获取帖子列表

- **接口**: `POST /api/post/postList`
- **描述**: 分页查询帖子列表，支持多条件筛选
- **请求体**: PostQueryRequest
  - userId: 用户ID（可选）
  - text: 搜索文本（可选）
  - updateTime: 更新时间（可选）
  - hotPost: 是否热门优先（Boolean，可选）
  - 分页参数（current, pageSize）
- **返回**: IPage<PostListVO> (MyBatis-Plus分页结果)

#### 4.4.2 获取公开图片列表

- **接口**: `GET /api/picture/list?current={current}&pageSize={pageSize}`
- **描述**: 分页获取公开图片列表
- **请求参数**:
  - current: 当前页码（默认1）
  - pageSize: 每页数量（默认20，范围1-100）
- **返回**: IPage<PictureListVO> (MyBatis-Plus分页结果)
  - PictureListVO: id, url

#### 4.4.3 获取系统分类标签列表

- **接口**: `GET /api/system/list`
- **描述**: 获取系统预设的分类标签列表（从Redis缓存读取）
- **返回**: List<String>（标签名称列表）

#### 4.4.4 获取跑马灯图片列表

- **接口**: `GET /api/system/marquee`
- **描述**: 获取首页跑马灯图片列表（从Redis缓存读取）
- **返回**: List<String>（图片URL列表）

### 4.5 管理员接口（需要管理员权限）

#### 4.5.1 获取用户列表

- **接口**: `POST /api/user/admin/userList`
- **描述**: 分页查询用户列表，支持多条件搜索
- **权限**: @AuthCheck(role = "admin")
- **请求体**: UserQueryWrapper
  - id: 用户ID（精确查询）
  - username: 用户名（模糊查询）
  - email: 邮箱（模糊查询）
  - phone: 手机号（模糊查询）
  - nickname: 昵称（模糊查询）
  - status: 状态（精确查询）
  - role: 角色（精确查询）
  - createTime: 创建时间（范围查询）
  - current: 当前页码
  - pageSize: 每页数量
- **返回**: IPage<User> (MyBatis-Plus分页结果)

#### 4.5.2 获取单个用户详情

- **接口**: `POST /api/user/admin/getUser`
- **描述**: 获取指定用户的详细信息
- **权限**: @AuthCheck(role = "admin")
- **请求体**: UserIdRequest
  - userId: 用户ID
- **返回**: User (用户实体)

#### 4.5.3 设置用户状态

- **接口**: `POST /api/user/admin/setStatus`
- **描述**: 修改用户状态（封禁/解封）
- **权限**: @AuthCheck(role = "admin")
- **请求体**: UserIdRequest
  - userId: 用户ID
- **返回**: Boolean (成功/失败)
- **业务规则**: 管理员不能封禁自己

#### 4.5.4 编辑用户信息

- **接口**: `POST /api/user/admin/editUser`
- **描述**: 管理员编辑任意用户信息
- **权限**: @AuthCheck(role = "admin")
- **请求体**: UserEditByAdminRequest
  - id: 用户ID
  - username: 用户名
  - password: 密码
  - avatar: 头像
  - email: 邮箱
  - phone: 手机号
  - nickname: 昵称
  - status: 状态
  - role: 角色
- **返回**: Boolean (成功/失败)

#### 4.5.5 获取管理员图片列表

- **接口**: `GET /api/picture/admin/list?current={current}&pageSize={pageSize}&status={status}`
- **描述**: 管理员分页查询图片列表，支持按状态筛选
- **权限**: @AuthCheck(role = "admin")
- **请求参数**:
  - current: 当前页码（默认1）
  - pageSize: 每页数量（默认20，范围1-100）
  - status: 状态筛选（3-全部，1-正常，2-待审核，0-禁用）
- **返回**: IPage<PictureAdminVO> (MyBatis-Plus分页结果)
  - PictureAdminVO: id, url, width, height, size, status, createTime, userId, isPrivate

#### 4.5.6 审核图片

- **接口**: `POST /api/picture/admin/review?pictureId={pictureId}&status={status}&selected={selected}`
- **描述**: 管理员审核图片（通过/拒绝）
- **权限**: @AuthCheck(role = "admin")
- **请求参数**:
  - pictureId: 图片ID
  - status: 目标状态（1-通过，0-拒绝）
  - selected: 精选标记
- **返回**: Boolean (成功/失败)

---

## 5. 非功能性需求

### 5.1 性能需求

- **响应时间**: 页面加载时间 ≤ 2秒
- **并发用户**: 支持1000+并发用户
- **图片上传**: 支持文件上传（≤5MB），请求总大小≤100MB
- **缓存策略**:
  - 验证码缓存：5分钟（Redis）
  - 用户信息缓存：登录后缓存到Redis（KEY: USER_ID:{userId}），Session中仅存储userId
  - 分类标签缓存：Redis（KEY: type_list_key）
  - 跑马灯配置缓存：Redis（KEY: marquees_key）
  - Session管理：Spring Session + Redis自动管理

### 5.2 安全需求

- **密码安全**: MD5 + 盐值"fish"加密存储
- **认证机制**: HTTP Session + Redis认证（Session存储userId，Redis存储用户信息JSON，Cookie自动传输Session ID）
- **权限控制**:
  - @AuthCheck注解标记接口权限
  - AuthInterceptor AOP拦截器进行权限校验
  - 基于角色（admin/user）的访问控制
- **防暴力破解**: 图形验证码（圆圈验证码）
- **跨域安全**: CORS配置（allowCredentials=true，allowedOriginPatterns("\*")支持Cookie/Session）
- **逻辑删除**: @TableLogic防止数据误删
- **统一异常处理**: GlobalExceptionHandler统一异常处理，避免敏感信息泄露
- **受限输入流**: LimitedInputStream限制文件上传大小

### 5.3 可用性需求

- **系统可用性**: 99.9%
- **移动端适配**: 响应式设计
- **用户体验**: 直观易用的界面
- **暗色模式**: 支持主题切换

### 5.4 可维护性需求

- **模块化设计**: 前后端分离，职责清晰
- **日志记录**: 完整的操作日志（Slf4j）
- **异常处理**: GlobalExceptionHandler统一异常处理
- **API文档**: Knife4j自动生成
- **代码简化**: Lombok注解减少样板代码

---

## 6. 系统边界与接口

### 6.1 外部系统接口

- **MySQL数据库**: 持久化存储，localhost:3306/FishPics
- **Redis缓存**: Session管理、验证码存储、用户信息缓存、系统配置缓存，192.168.163.101:6379
- **腾讯云COS对象存储**: 图片资源存储，通过COSConfig配置
- **文件存储服务**: 图片上传存储（腾讯云COS）

### 6.2 内部模块接口

- **前端API调用**: Axios封装，统一请求/响应拦截
- **后端服务层**: Service接口定义业务逻辑
- **数据访问层**: MyBatis-Plus BaseMapper实现CRUD
- **统一响应**: Response<T>封装返回结果（code, message, data）

### 6.3 前后端交互流程

```
前端发起请求
→ Axios拦截器处理请求
→ 后端接收请求（Cookie自动携带Session ID）
→ AuthInterceptor校验权限（需要登录的接口，从request.getSession()获取用户）
→ Controller处理请求
→ Service执行业务逻辑
→ Mapper操作数据库
→ 返回Response<T> (code=1表示成功)
→ Axios响应拦截器处理（检查code=1，提取data）
→ 前端更新状态
```

### 6.4 Session认证流程

```
1. 用户登录：
   前端POST /api/user/login
   → 后端校验验证码（Redis比对） + 用户名密码（MD5+Salt比对）
   → 校验通过，将userId存入request.getSession().setAttribute(TOKEN_KEY, userId)
   → 将User对象JSON序列化存入Redis（KEY: USER_ID:{userId}）
   → Spring Session自动通过Set-Cookie响应头返回Session ID（JSESSIONID）
   → 前端localStorage保存用户基本信息用于展示

2. 后续请求：
   浏览器自动在请求头Cookie中携带JSESSIONID
   → 后端通过request.getSession().getAttribute(TOKEN_KEY)获取userId
   → LoginUser组件从Redis读取用户信息JSON并反序列化为User对象
   → AuthInterceptor从Session获取用户进行权限校验

3. 退出登录：
   前端清除localStorage中的用户信息
   → 前端状态重置为未登录
   → 后端Session通过session.invalidate()失效
```

---

## 7. 业务规则

### 7.1 用户相关规则

- **用户名**: 6-11个字符，必须唯一
- **密码**: 8-20个字符，注册时需确认密码一致
- **昵称**: 5-11个字符，必须唯一，默认"小鱼籽\_+随机字符串"
- **新用户**: 默认角色为"user"（普通用户）
- **管理员**: 可封禁/解封用户，但不能封禁自己
- **删除操作**: 采用逻辑删除（isDelete字段，@TableLogic）

### 7.2 内容相关规则

- **帖子**: 必须包含标题和内容
- **图片**: 必须关联到用户，可通过postId关联到帖子，可通过spaceId关联到空间
- **评论**: 必须关联到帖子和用户，支持二级评论
- **审核机制**: 所有内容默认需要审核（status=2-待审核）
- **隐私设置**: 帖子/图片可设置公开/私密
- **帖子统计**: 自动维护点赞数、收藏数、评论数、查看数、热度值

### 7.3 权限相关规则

- **普通用户**: 只能管理自己的内容和信息
- **管理员**: 可以管理所有用户和内容
- **接口权限**: 通过@AuthCheck注解控制
- **权限校验**: AuthInterceptor AOP拦截器统一处理
- **Session管理**: 用户登录后userId存储在Session中，用户信息缓存在Redis中，Cookie自动传输Session ID

### 7.4 验证码规则

- **验证码类型**: 圆圈图形验证码（Hutool CircleCaptcha）
- **验证码长度**: 5位
- **验证码有效期**: 5分钟
- **存储位置**: Redis
- **使用场景**: 注册、登录
- **校验机制**: 比对Redis中存储的验证码

### 7.5 社交关系规则

- **粉丝**: 用户粉丝关系记录（user_fans表）
- **收藏**: 用户可以收藏帖子
- **点赞**: 用户可以点赞帖子
- **隐私控制**: 用户可设置关注/粉丝/收藏/点赞列表的公开性

### 7.6 图片上传规则

- **文件大小限制**: 最大5MB
- **支持格式**: 常见图片格式
- **上传场景**:
  - 头像上传（需指定用户ID）
  - 帖子图片上传（自动关联当前用户）
- **存储方式**: 上传至腾讯云COS对象存储，返回图片URL

### 7.7 空间管理规则

- **空间类型**: 0-私人空间，1-团队空间
- **存储大小**: 普通用户512MB，VIP 5GB，SVIP 10GB
- **空间级别**: 0-普通，1-VIP，2-SVIP
- **团队空间**: 支持多个用户协作，teamUsersId存储用户ID列表

### 7.8 系统配置规则

- **分类标签**: 存储在Redis中（KEY: type_list_key），支持动态增删
- **跑马灯图片**: 存储在Redis中（KEY: marquees_key），首页展示轮播图

---

## 8. 部署需求

### 8.1 环境要求

- **Java**: JDK 11+
- **MySQL**: 8.0+
- **Redis**: 5.0+
- **Node.js**: 18+
- **Maven**: 3.6+

### 8.2 部署配置

- **后端端口**: 8080
- **后端Context Path**: /api
- **前端端口**: 5173（Vite默认）
- **数据库**: FishPics（localhost:3306）
- **数据库账号**: root/123456
- **Redis Host**: 192.168.163.101
- **Redis Port**: 6379
- **Redis Database**: 0
- **Spring Session**: store-type=redis, timeout=86400s（1天）
- **文件上传**: 单文件最大10MB，请求总大小100MB
- **腾讯云COS**: 需配置cos.secretId、cos.secretKey、cos.region、cos.bucket（application.yml）
- **API文档**: Knife4j（http://localhost:8080/api/doc.html）
- **日志级别**: root=info, hk.ljx.fishpicsbackend=debug

### 8.3 部署架构

```
┌─────────────┐
│   用户浏览器  │
└──────┬──────┘
       │ HTTP/HTTPS (自动携带Cookie/Session ID)
┌──────▼──────┐
│  前端(Nginx) │  ← React SPA (端口5173)
└──────┬──────┘
       │ API调用 (/api/*)
┌──────▼──────┐
│  后端服务    │  ← Spring Boot (端口8080, Context Path: /api)
└──┬───┬───┬──┘
   │   │   │
┌──▼┐┌▼──┐┌───┐┌───┐
│MySQL│Redis│COS  │Redisson│
│     │     │     │        │
└─────┘└─────┘└───┘└───────┘
```

### 8.4 可扩展性

- **负载均衡**: 多实例部署（需配置Session共享）
- **数据库**: 主从复制（可选）
- **缓存**: Redis集群（可选）
- **CDN**: 静态资源加速（可选）

---

## 9. 前端架构设计

### 9.1 目录结构

```
FishPic-frontend/src/
├── api/
│   └── index.js              # Axios封装（请求/响应拦截，完整API列表）
├── assets/                   # 静态资源（hero.png, react.svg, vite.svg）
├── components/               # 通用组件
│   ├── CreateEditPostModal.jsx   # 帖子发布/编辑弹窗
│   ├── CreateEditPostModal.css   # 帖子弹窗样式
│   ├── ErrorBoundary.jsx         # 错误边界
│   ├── FunnyBackground.jsx       # 趣味背景动画
│   ├── FunnyBackground.css       # 背景动画样式
│   ├── GlobalLayout.jsx          # 全局布局
│   ├── PostDetailModal.jsx       # 帖子详情弹窗
│   ├── PostDetailModal.css       # 详情弹窗样式
│   └── ProtectedRoute.jsx        # 路由权限保护
├── context/
│   ├── AuthContext.jsx       # 认证状态管理
│   └── ThemeContext.jsx      # 明暗主题状态管理
├── pages/                    # 页面组件
│   ├── AdminPictureManagement.jsx  # 管理员图片管理
│   ├── AdminPictureManagement.css
│   ├── AdminUserList.jsx     # 管理员用户列表
│   ├── AdminUserList.css
│   ├── AIManagement.jsx      # AI管理（开发中）
│   ├── AIManagement.css
│   ├── CommunitySquare.jsx   # 社区广场
│   ├── CommunitySquare.css
│   ├── HomePage.jsx          # 主页
│   ├── NotFound.jsx          # 404页面
│   ├── NotFound.css
│   ├── Notifications.jsx     # 通知中心
│   ├── Notifications.css
│   ├── PrivateSpace.jsx      # 私人空间
│   ├── PrivateSpace.css
│   ├── SpaceManagement.jsx   # 空间管理（开发中）
│   ├── SpaceManagement.css
│   ├── SystemManagement.jsx  # 系统管理（标签/跑马灯）
│   ├── SystemManagement.css
│   ├── TeamManagement.jsx    # 团队管理（开发中）
│   ├── TeamManagement.css
│   ├── TeamSpace.jsx         # 团队空间
│   ├── TeamSpace.css
│   ├── UserManagement.jsx    # 用户管理
│   ├── UserManagement.css
│   ├── UserProfile.jsx       # 用户资料
│   └── UserProfile.css
├── utils/
│   └── storage.js            # 本地存储工具
├── App.jsx                   # 应用入口（路由配置）
├── App.css                   # 应用样式
├── index.css                 # 全局样式
└── main.jsx                  # React挂载点
```

### 9.2 路由配置

- **/** → HomePage（主页）
- **/profile** → UserProfile（用户资料，需登录）
- **/community** → CommunitySquare（社区广场，公开访问）
- **/private-space** → PrivateSpace（私人空间，需登录）
- **/team-space** → TeamSpace（团队空间，需登录）
- **/notifications** → Notifications（通知中心，需登录）
- **/admin/users** → UserManagement（用户管理，需管理员权限）
- **/admin/pictures** → AdminPictureManagement（图片管理，需管理员权限）
- **/admin/spaces** → SpaceManagement（空间管理，需管理员权限）
- **/admin/teams** → TeamManagement（团队管理，需管理员权限）
- **/admin/ai** → AIManagement（AI管理，需管理员权限）
- **/admin/system** → SystemManagement（系统管理，需管理员权限）
- **/admin/user-list** → AdminUserList（管理员用户列表，需管理员权限）
- **/404** → NotFound（404页面）
- **\*** → NotFound（404页面）

### 9.3 状态管理

- **认证状态**: AuthContext（Context API）
  - userInfo: 当前用户信息（存储于localStorage用于前端展示）
  - isAuthenticated: 是否已登录
  - login: 登录函数（保存用户信息到localStorage）
  - logout: 登出函数（清除localStorage中的用户信息）
- **主题状态**: ThemeContext（明暗主题切换）
  - isDarkMode: 是否为暗色模式
  - toggleTheme: 切换主题函数
- **本地存储**: localStorage（用户信息持久化用于前端展示）

### 9.4 API请求封装

- **Axios实例**: 统一配置baseURL='/api'、timeout=10000
- **请求拦截器**: 处理请求配置（Cookie由浏览器自动携带）
- **响应拦截器**:
  - 检查响应格式（必须有code字段）
  - code=1表示成功，提取data返回
  - code≠1抛出异常
  - 特殊处理：验证码接口和blob类型响应
- **错误处理**: 网络错误、401未授权、500服务器错误

### 9.5 前端API列表

- getLoginCheckCode(): 获取登录验证码
- getRegisterCheckCode(): 获取注册验证码
- login(data): 用户登录
- register(data): 用户注册
- getUserMyself(): 获取个人信息（帖子/收藏/点赞列表）
- logout(): 退出登录
- getUser(): 获取当前用户信息
- getAdminUser(userId): 管理员获取用户详情
- editUser(data): 编辑个人信息
- uploadAvatar(formData, onProgress): 上传头像（带进度回调）
- uploadPostPicture(formData, onProgress): 上传帖子图片（带进度回调）
- getMyPosts(data): 获取本人发布的帖子列表（分页）
- getMyCollects(data): 获取本人收藏的帖子列表（分页）
- getMyLikes(data): 获取本人点赞的帖子列表（分页）
- getMarquee(): 获取首页跑马灯图片列表
- getPictureList(current, pageSize): 获取公开图片列表（分页）
- getAdminPictureList(current, pageSize, status): 管理员获取图片列表（分页，按状态筛选）
- reviewPicture(pictureId, status, selected): 管理员审核图片
- createSpace(data): 创建空间
- updateSpace(data): 更新空间信息
- listSpace(type): 获取空间列表
- spaceListPicture(data): 获取空间图片列表
- deletePicture(ids): 删除图片（支持批量）

---

## 10. 后端架构设计

### 10.1 目录结构

```
FishPics-backend/src/main/java/hk/ljx/fishpicsbackend/
├── common/                     # 公共模块
│   ├── annotation/
│   │   └── AuthCheck.java      # 权限校验注解
│   ├── aop/
│   │   └── AuthInterceptor.java# 权限校验拦截器（从Session获取用户）
│   ├── config/
│   │   ├── COSConfig.java      # 腾讯云COS客户端配置
│   │   ├── CorsConfig.java     # 跨域配置（allowCredentials=true）
│   │   ├── JsonConfig.java     # JSON配置
│   │   ├── MybatisPlusConfig.java # MyBatis-Plus配置（分页插件）
│   │   └── SessionRedisConfig.java # Spring Session Redis序列化配置
│   ├── constants/
│   │   ├── RedisConstants.java # Redis键常量（LOGIN_CODE, REGISTER_CODE, TOKEN, USER_ID, LIKE_POST）
│   │   ├── SpaceConstants.java # 空间存储大小常量（512MB/5GB/10GB）
│   │   ├── SysConstants.java   # 系统常量（type_list_key, marquees_key）
│   │   └── UserConstants.java  # 用户常量（角色、盐值、默认昵称）
│   ├── exception/
│   │   ├── BaseException.java  # 基础异常
│   │   ├── ExceptionCode.java  # 异常编码
│   │   ├── ExcUtils.java       # 异常工具
│   │   └── GlobalExceptionHandler.java # 全局异常处理器
│   ├── response/
│   │   ├── Response.java       # 统一响应 {code, message, data}
│   │   └── ResUtils.java       # 响应工具
│   └── utils/
│       └── LimitedInputStream.java # 受限输入流（文件大小限制）
├── controller/                 # 控制器层
│   ├── UserController.java     # 用户控制器（登录/注册/验证码/退出登录/管理）
│   ├── PostController.java     # 帖子控制器（发布/编辑/列表/点赞/我的帖子/收藏/点赞列表）
│   ├── PictureController.java  # 图片控制器（上传/列表/删除/审核）
│   ├── SpaceController.java    # 空间控制器（创建/列表/更新/图片列表）
│   └── SystemController.java   # 系统控制器（标签/跑马灯管理）
├── dto/                        # 数据传输对象
│   ├── base/
│   │   ├── PageRequest.java    # 分页请求（current, pageSize）
│   │   └── DeleteById.java     # 删除请求
│   ├── picture/
│   │   ├── DeleteByIdList.java # 批量删除图片请求
│   │   └── PictureMessage.java # 图片消息DTO
│   ├── post/
│   │   ├── EditPostRequest.java    # 编辑帖子请求（id, imageId, title, content, cover, isPrivate）
│   │   ├── PostQueryRequest.java   # 帖子查询请求（userId, text, updateTime, hotPost, 分页）
│   │   ├── PostQueryWrapper.java   # 帖子查询包装器
│   │   └── UploadPostRequest.java  # 上传帖子请求（imageId, title, content, cover, isPrivate）
│   ├── space/
│   │   ├── CreateSpace.java        # 创建空间请求（name, introduction, type）
│   │   ├── UpdateSpace.java        # 更新空间请求（id, name, introduction）
│   │   ├── SpacePictureList.java   # 空间图片列表请求（spaceId, 分页）
│   │   └── SpaceQueryWrapper.java  # 空间查询包装器
│   ├── system/
│   │   ├── AddSysMarquee.java      # 添加跑马灯请求
│   │   └── AddSysPicType.java      # 添加分类标签请求
│   └── user/
│       ├── UserEditByAdminRequest.java  # 管理员编辑用户请求
│       ├── UserEditRequest.java         # 用户编辑请求
│       ├── UserIdRequest.java           # 用户ID请求
│       ├── UserLoginRequest.java        # 用户登录请求
│       ├── UserQueryWrapper.java        # 用户查询包装器
│       └── UserRequestRequest.java      # 用户注册请求
├── entity/                     # 实体类
│   ├── Comment.java            # 评论实体
│   ├── PicSystem.java          # 系统配置实体（syskey, sysvalue）
│   ├── Picture.java            # 图片实体（含spaceId, introduction字段）
│   ├── PictureChild.java       # 子图片关联实体（pictureId, postId, sortNum）
│   ├── Post.java               # 帖子实体（含hot热度字段）
│   ├── Space.java              # 空间实体（type, level, storageSize等）
│   ├── User.java               # 用户实体（含level, size字段）
│   ├── UserFans.java           # 粉丝实体
│   ├── UserPostCollect.java    # 收藏实体
│   └── UserPostLikes.java      # 点赞实体
├── enums/
│   └── UserRoleEnum.java       # 用户角色枚举
├── mapper/                     # 数据访问层
│   ├── CommentMapper.java      # 评论Mapper
│   ├── PicSystemMapper.java    # 系统配置Mapper
│   ├── PictureMapper.java      # 图片Mapper
│   ├── PictureChildMapper.java # 子图片关联Mapper
│   ├── PostMapper.java         # 帖子Mapper
│   ├── SpaceMapper.java        # 空间Mapper
│   ├── UserFansMapper.java     # 粉丝Mapper
│   ├── UserMapper.java         # 用户Mapper
│   ├── UserPostCollectMapper.java # 收藏Mapper
│   └── UserPostLikesMapper.java   # 点赞Mapper
├── service/                    # 服务层
│   ├── impl/                   # 服务实现类
│   │   ├── CommentServiceImpl.java
│   │   ├── PicSystemServiceImpl.java
│   │   ├── PictureServiceImpl.java
│   │   ├── PictureChildServiceImpl.java
│   │   ├── PostServiceImpl.java
│   │   ├── SpaceServiceImpl.java
│   │   ├── UserFansServiceImpl.java
│   │   ├── UserPostCollectServiceImpl.java
│   │   ├── UserPostLikesServiceImpl.java
│   │   └── UserServiceImpl.java
│   ├── CommentService.java     # 评论服务接口
│   ├── CosService.java         # 腾讯云COS对象存储服务
│   ├── LoginUser.java          # 登录用户获取工具（Session + Redis）
│   ├── PicSystemService.java   # 系统配置服务（标签/跑马灯）
│   ├── PictureService.java     # 图片服务接口
│   ├── PictureChildService.java # 子图片关联服务接口
│   ├── PostService.java        # 帖子服务接口
│   ├── SpaceService.java       # 空间服务接口
│   ├── UserFansService.java    # 粉丝服务接口
│   ├── UserPostCollectService.java # 收藏服务接口
│   ├── UserPostLikesService.java   # 点赞服务接口
│   └── UserService.java        # 用户服务接口
├── vo/                         # 视图对象
│   ├── picture/
│   │   ├── PictureAdminVO.java # 管理员图片VO（id, url, width, height, size, status, createTime, userId, isPrivate）
│   │   ├── PictureListVO.java  # 图片列表VO（id, url）
│   │   ├── PicturePageVO.java  # 图片分页VO（records, total）
│   │   └── PicturePostVO.java  # 帖子图片VO
│   ├── post/
│   │   ├── PostDetailVO.java   # 帖子详情VO（含pictureUrl/pictureIds同步过滤、cover）
│   │   └── PostListVO.java     # 帖子列表VO
│   └── user/
│       ├── CheckCodeVO.java    # 验证码VO（captchaKey, base64Image）
│       ├── UserLoginVO.java    # 用户登录VO
│       └── UserMessageVO.java  # 用户信息VO（含帖子/收藏/点赞列表）
└── FishPicsBackendApplication.java # 启动类
```

### 10.2 架构模式

- **MVC三层架构**: Controller → Service → Mapper
- **Repository模式**: MyBatis-Plus BaseMapper
- **DTO/VO模式**: 数据传输分离（DTO入参，VO出参）
- **Builder模式**: Lombok @Builder
- **AOP模式**: @AuthCheck + AuthInterceptor权限代理
- **统一响应模式**: Response<T>封装 {code, message, data}

### 10.3 权限控制机制

- **注解**: @AuthCheck(role = "admin/user")
- **拦截器**: AuthInterceptor（Spring AOP @Around）
- **校验流程**:
  1. 获取request.getSession().getAttribute(TOKEN_KEY)
  2. 校验Session中用户信息是否为空（为空则为未登录或登录过期）
  3. 校验用户角色是否匹配@AuthCheck注解要求的角色
  4. 通过/拒绝请求

### 10.4 异常处理机制

- **全局异常处理器**: GlobalExceptionHandler
- **自定义异常**: BaseException
- **异常编码**: ExceptionCode（定义错误码和消息）
- **异常工具**: ExcUtils（throwIfTrue等方法）
- **参数校验**: 使用ExcUtils进行参数非空校验

### 10.5 核心业务服务

#### UserService

- getCheckCode(): 获取图形验证码（CircleCaptcha，5位，5分钟有效期，存入Redis）
- userRegister(): 用户注册（MD5+Salt加密密码，随机昵称）
- userLogin(): 用户登录（校验验证码→查用户→比对密码→Session存储userId→Redis缓存用户信息）
- isMe(): 判断是否是自己的信息
- getMyselfMessage(): 获取个人主页信息（用户信息+帖子列表+收藏列表+点赞列表）
- editMyself(): 编辑个人信息
- newQueryWrapper(): 构造查询条件
- getUserList(): 获取用户列表（管理员，分页+多条件搜索）
- setStatus(): 设置用户状态（管理员）
- editUser(): 编辑用户（管理员）

#### PostService

- uploadPost(): 上传帖子（创建帖子+关联图片）
- getPost(): 获取帖子详情
- editPost(): 编辑帖子
- getPostList(): 获取帖子列表（分页，支持分类标签、搜索、热门排序）
- newQueryWrapper(): 构造帖子查询条件
- likePost(): 点赞帖子
- getMyPosts(): 获取本人发布的帖子列表（分页）
- getMyCollects(): 获取本人收藏的帖子列表（分页）
- getMyLikes(): 获取本人点赞的帖子列表（分页）

#### PictureService

- uploadAvatar(): 上传头像（至腾讯云COS，限制5MB）
- uploadPicture4Post(): 上传帖子图片（至腾讯云COS，限制5MB）
- setPicturePostId(): 设置图片与帖子的关联
- getPictureList(): 获取公开图片列表（分页）
- getAdminPictureList(): 管理员获取图片列表（分页，按状态筛选）
- reviewPicture(): 管理员审核图片（通过/拒绝/设置精选）
- deletePicture(): 删除图片（支持批量）

#### SpaceService

- createSpace(): 创建空间（私人/团队，设置默认存储大小）
- listSpace(): 获取空间列表（按类型查询）
- updateSpace(): 更新空间信息（名称、介绍）
- pictureList(): 获取空间图片列表（分页）
- getSpaceQueryWrapper(): 构造空间查询条件

#### PicSystemService

- getTypeList(): 获取分类标签列表（从Redis缓存读取）
- addTypeList(): 添加分类标签（存入Redis）
- deleteType(): 删除分类标签（从Redis移除）
- getMarquess(): 获取跑马灯图片列表（从Redis缓存读取）
- addMarquee(): 添加跑马灯图片（存入Redis）
- deleteMarquee(): 删除跑马灯图片（从Redis移除）

#### CosService

- 上传图片至腾讯云COS（支持文件名UUID重命名、内容类型检测）
- 限制文件大小（5MB）
- 支持公开/私有访问控制

---

## 11. 设计模式应用

### 11.1 已应用的设计模式

- **单例模式**: Spring Bean单例（@Service, @Component, @RestController）
- **工厂模式**: Spring IoC容器
- **代理模式**: Spring AOP权限代理（AuthInterceptor）
- **策略模式**: MyBatis-Plus条件构造器（QueryWrapper）
- **建造者模式**: Lombok @Builder（DTO类）
- **模板方法模式**: MyBatis-Plus IService接口
- **观察者模式**: Spring事件机制
- **责任链模式**: Spring Interceptor链
- **外观模式**: ServiceImpl封装复杂业务逻辑
- **适配器模式**: Spring MVC HandlerAdapter
- **装饰器模式**: Hutool工具类包装
- **数据映射器模式**: MyBatis-Plus BaseMapper

---

## 12. 性能与安全考虑

### 12.1 性能优化

- **Redis缓存**:
  - 验证码缓存（5分钟）
  - 用户信息缓存（登录后缓存User JSON到Redis，减少数据库查询）
  - 分类标签缓存（从Redis读取，避免频繁查询数据库）
  - 跑马灯配置缓存（从Redis读取）
  - Session存储：仅存userId，减少Session序列化开销
- **Session管理**:
  - Spring Session + Redis自动管理
  - Session中仅存储userId，User对象由LoginUser按需从Redis读取
- **分页查询**: MyBatis-Plus分页插件
- **索引优化**: 关键字段建立索引（username, nickname, user_id, post_id）
- **逻辑删除**: @TableLogic避免物理删除性能损耗
- **连接池**: MySQL连接池配置
- **对象存储**: 腾讯云COS存储图片，减轻服务器存储压力

### 12.2 安全措施

- **密码加密**: MD5 + 盐值"fish"
- **Session认证**: Spring Session + Redis，Session中仅存userId，User对象缓存在Redis
- **CORS配置**: allowCredentials=true，allowedOriginPatterns("\*")支持Cookie/Session跨域传输
- **权限控制**: @AuthCheck + AuthInterceptor
- **验证码防护**: 图形验证码（CircleCaptcha）防暴力破解
- **SQL防护**: MyBatis-Plus预编译语句
- **逻辑删除**: 防止数据误删
- **异常处理**: GlobalExceptionHandler统一异常处理，避免敏感信息泄露
- **文件大小限制**: 图片上传限制5MB（LimitedInputStream）
- **请求大小限制**: multipart.max-file-size=10MB, max-request-size=100MB

### 12.3 常量管理

- **RedisConstants**: Redis键名前缀（LOGIN_CODE, REGISTER_CODE, TOKEN, USER_ID, LIKE_POST）
- **SpaceConstants**: 空间存储大小常量（DEFAULT 512MB, VIP 5GB, SVIP 10GB）
- **SysConstants**: 系统配置常量（type_list_key, marquees_key）
- **UserConstants**: 用户相关常量（盐值、角色等）
- **ExceptionCode**: 异常编码和消息
  - PARAMETER_ERROR: 参数错误
  - DATABASE_ERROR: 数据库错误
  - NOT_LOGIN: 未登录
  - NOT_FOUND: 未找到
  - UNAUTHORIZED: 未授权

### 12.4 业务规则

- **用户名**: 6-11字符，唯一
- **密码**: 8-20字符
- **昵称**: 5-11字符，唯一，默认"小鱼籽\_+随机字符串"
- **验证码**: 5位，5分钟有效
- **Session**: Spring Session + Redis管理，Session中仅存userId
- **权限**: admin/user两级
- **状态**: 1-正常, 0-禁用, 2-待审核
- **隐私**: 0-公开, 1-私密
- **图片默认状态**: 2-待审核
- **空间类型**: 0-私人, 1-团队
- **空间级别**: 0-普通, 1-VIP, 2-SVIP
- **存储限制**: 普通512MB, VIP 5GB, SVIP 10GB
- **文件上传**: 最大5MB
- **响应码**: 1-成功，其他-失败
