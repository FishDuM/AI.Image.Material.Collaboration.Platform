# AI.Image.Material.Collaboration.Platform 软件需求模型

## 1. 系统概述

FishPics（FishPics Image Collaboration Platform）是一个基于前后端分离架构的图片分享与协作社区平台。系统为用户提供图片浏览、帖子发布、评论互动、个人空间管理等功能，同时提供管理员后台进行用户管理和内容审核。

### 1.1 项目目标
- 打造美观流畅的图片分享社区体验
- 构建完整的图片-帖子-评论生态系统
- 实现安全可靠的用户权限管理体系
- 提供高性能的内容展示与交互

### 1.2 技术栈
- **前端**: React 19 + Vite + Ant Design 6 + React Router v7 + Context API + Axios
- **后端**: Spring Boot 2.7.6 + MyBatis-Plus 3.5.15 + MySQL 8 + Redis + Knife4j + Hutool + Lombok
- **认证**: HTTP Session + Cookie（Spring Session机制，依赖CORS allowCredentials）
- **密码安全**: MD5 + 盐值"fish"

### 1.3 系统架构
- 前后端分离架构
- 后端RESTful API设计，基础路径：`/api`
- 前端单页应用（SPA），默认端口：5173
- 后端服务端口：8080
- MySQL数据库：FishPics
- Redis缓存：6379端口（用于验证码存储）

---

## 2. 功能需求分析

### 2.1 核心功能模块

#### 2.1.1 用户认证模块
- **用户注册**
  - 输入：用户名（6-11字符）、密码（8-20字符）、确认密码、图形验证码
  - 校验：用户名唯一性、密码一致性、验证码正确性
  - 默认值：昵称"小鱼籽_+随机字符串"、角色"user"
  
- **用户登录**
  - 输入：用户名、密码、图形验证码
  - 流程：验证码校验 → 用户查询 → 密码比对（MD5+Salt） → 存储到HTTP Session → 返回用户信息
  - Session机制：Spring自动管理Session ID通过Cookie传输
  
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
- **关注功能**
  - 用户关注其他用户（userFollows表：userId、beFollowedUserId）
  - 关注/取关操作
  - 关注列表管理
  
- **粉丝功能**
  - 粉丝关系记录（user_fans表：userId、fanId）
  - 粉丝列表管理
  
- **收藏功能**
  - 收藏帖子（user_post_collect表：userId、postId）
  - 收藏列表管理
  
- **点赞功能**
  - 点赞帖子（user_post_likes表：userId、postId）
  - 点赞列表管理
  - 接口：POST /api/post/like?id={id}

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
- 权限控制（@AuthCheck + AuthInterceptor AOP）
- API文档自动生成（Knife4j）

#### 2.1.9 系统功能模块
- **分类列表**
  - 接口：GET /api/system/list
  - 功能：返回系统分类列表（推荐、穿搭、美食、旅行、宠物、运动）

#### 2.1.10 前端页面模块
- **主页**（HomePage）：平台首页，登录/注册入口
- **社区广场**（CommunitySquare）：社区内容展示
- **私人空间**（PrivateSpace）：用户个人私密内容
- **团队空间**（TeamSpace）：团队协作内容
- **通知中心**（Notifications）：用户通知消息
- **用户资料**（UserProfile）：个人资料查看与编辑
- **用户管理**（UserManagement）：用户账户管理
- **管理员用户列表**（AdminUserList）：管理员查看用户列表
- **团队管理**（TeamManagement）：团队信息管理
- **空间管理**（SpaceManagement）：空间配置管理
- **AI管理**（AIManagement）：AI相关功能管理
- **404页面**（NotFound）：未找到页面

#### 2.1.11 前端通用组件
- **GlobalLayout**：全局布局（导航栏、侧边栏、登录模态框）
- **ProtectedRoute**：路由权限保护（支持requireAdmin属性）
- **ErrorBoundary**：错误边界处理
- **FunnyBackground**：趣味背景动画（浮动emoji）
- **AuthContext**：认证状态管理（登录、登出、用户信息）
- **API封装**：Axios请求配置（请求/响应拦截器）
- **Storage工具**：本地存储管理（用户信息）

---

## 3. 数据模型分析

### 3.1 实体清单

系统包含 **8个核心实体**：
1. **User** (用户表) - 系统用户信息
2. **Post** (帖子表) - 用户发布的帖子
3. **Picture** (图片表) - 图片资源信息
4. **Comment** (评论表) - 帖子评论
5. **UserFans** (用户粉丝表) - 粉丝关系记录
6. **UserPostCollect** (用户帖子收藏表) - 收藏关系记录
7. **UserPostLikes** (用户点赞帖子表) - 点赞关系记录
8. **UserFollows** (用户关注表) - 关注关系记录（表名：likes_user_by_id）

### 3.2 实体关系图 (ERD)

```
User (1) ───< (N) Post
User (1) ───< (N) Picture
User (1) ───< (N) Comment
Post (1) ───< (N) Comment
User (1) ───< (N) UserFans
User (1) ───< (N) UserPostCollect
User (1) ───< (N) UserPostLikes
User (1) ───< (N) UserFollows
Post (1) ───< (N) UserPostCollect
Post (1) ───< (N) UserPostLikes
Post (1) ───< (N) Picture (通过postId关联)
```

### 3.3 数据库表结构详情

#### 3.3.1 User 表
| 字段 | 类型 | 描述 | 约束 |
|------|------|------|------|
| id | bigint | 用户ID | PRIMARY KEY, AUTO_INCREMENT |
| username | varchar(32) | 用户名（登录用） | UNIQUE |
| password | varchar(128) | 密码（MD5+Salt） | NOT NULL |
| avatar | varchar(256) | 头像URL | - |
| email | varchar(64) | 邮箱 | - |
| phone | varchar(16) | 手机号 | - |
| nickname | varchar(32) | 昵称（展示用） | UNIQUE |
| status | tinyint | 状态 (1-正常, 0-禁用, 2-待审核) | DEFAULT 1 |
| is_delete | tinyint | 逻辑删除标记 (0-未删除, 1-已删除) | DEFAULT 0, @TableLogic |
| role | varchar(32) | 用户权限 (admin/user) | DEFAULT 'user' |
| create_time | datetime | 创建时间 | DEFAULT CURRENT_TIMESTAMP |
| update_time | datetime | 更新时间 | DEFAULT CURRENT_TIMESTAMP ON UPDATE |
| like_num | bigint | 点赞数 | - |
| collect_num | bigint | 收藏数 | - |
| is_private_follows | tinyint | 关注列表隐私 (0-公开, 1-不公开) | - |
| is_private_post_collect | tinyint | 收藏列表隐私 (0-公开, 1-不公开) | - |
| is_private_likes | tinyint | 点赞列表隐私 (0-公开, 1-不公开) | - |
| is_private_fans | tinyint | 粉丝列表隐私 (0-公开, 1-不公开) | - |

#### 3.3.2 Post 表
| 字段 | 类型 | 描述 | 约束 |
|------|------|------|------|
| id | bigint | 主键 | PRIMARY KEY, AUTO_INCREMENT |
| user_id | bigint | 关联用户ID | NOT NULL, FOREIGN KEY |
| title | varchar(256) | 标题 | NOT NULL |
| content | text | 内容 | NOT NULL |
| status | tinyint | 状态 (1-正常, 0-禁用, 2-待审核) | DEFAULT 1 |
| is_delete | tinyint | 逻辑删除标记 | DEFAULT 0, @TableLogic |
| create_time | datetime | 创建时间 | DEFAULT CURRENT_TIMESTAMP |
| update_time | datetime | 更新时间 | DEFAULT CURRENT_TIMESTAMP ON UPDATE |
| likes_num | bigint | 点赞数 | - |
| collects_num | bigint | 收藏数 | - |
| comment_num | int | 评论数 | - |
| is_private | tinyint | 隐私设置 (0-公开, 1-仅自己可见) | - |
| cover | bigint | 封面图片的ID | - |
| views_num | bigint | 查看数 | - |

**注意**: Post表不包含picture_ids字段，图片通过Picture表的postId字段关联

#### 3.3.3 Picture 表
| 字段 | 类型 | 描述 | 约束 |
|------|------|------|------|
| id | bigint | 主键 | PRIMARY KEY, AUTO_INCREMENT |
| user_id | bigint | 用户ID | NOT NULL, FOREIGN KEY |
| picture_name | bigint | 图片名称 | NOT NULL |
| url | varchar(512) | 图片地址 | NOT NULL |
| width | varchar(32) | 宽度 | - |
| height | varchar(32) | 高度 | - |
| size | varchar(32) | 大小 | - |
| status | tinyint | 状态 (1-正常, 0-禁用, 2-待审核) | DEFAULT 1 |
| create_time | datetime | 创建时间 | DEFAULT CURRENT_TIMESTAMP |
| update_time | datetime | 更新时间 | DEFAULT CURRENT_TIMESTAMP ON UPDATE |
| is_private | tinyint | 隐私设置 (0-不公开到首页, 1-公开到首页) | - |
| post_id | bigint | 帖子ID（关联到Post表） | FOREIGN KEY |

**注意**: Picture表包含postId字段，用于关联到具体的帖子

#### 3.3.4 Comment 表
| 字段 | 类型 | 描述 | 约束 |
|------|------|------|------|
| id | bigint | 主键 | PRIMARY KEY, AUTO_INCREMENT |
| user_id | bigint | 关联用户ID | NOT NULL, FOREIGN KEY |
| post_id | bigint | 关联帖子ID | NOT NULL, FOREIGN KEY |
| content | text | 评论内容 | NOT NULL |
| parent_id | bigint | 父评论ID（支持二级评论） | - |
| to_user_id | tinyint | 回复给谁 | - |
| status | tinyint | 状态 (1-正常, 0-禁用, 2-待审核) | DEFAULT 1 |
| create_time | datetime | 创建时间 | DEFAULT CURRENT_TIMESTAMP |

**注意**: Comment表不包含update_time字段

#### 3.3.5 UserFans 表
| 字段 | 类型 | 描述 |
|------|------|------|
| id | bigint | 主键 |
| user_id | bigint | 用户ID |
| fan_id | bigint | 粉丝ID |

#### 3.3.6 UserPostCollect 表
| 字段 | 类型 | 描述 |
|------|------|------|
| id | bigint | 主键 |
| user_id | bigint | 用户ID |
| post_id | bigint | 帖子ID |

#### 3.3.7 UserPostLikes 表
| 字段 | 类型 | 描述 |
|------|------|------|
| id | bigint | 主键 |
| user_id | bigint | 用户ID |
| post_id | bigint | 帖子ID |

#### 3.3.8 UserFollows 表（likes_user_by_id）
| 字段 | 类型 | 描述 |
|------|------|------|
| id | bigint | 主键 |
| user_id | bigint | 用户ID |
| be_followed_user_id | bigint | 被关注用户ID |

### 3.4 数据库索引

| 表 | 索引名 | 字段 | 说明 |
|------|------|------|------|
| user | uk_username | username | 用户名唯一索引 |
| user | uk_nickname | nickname | 昵称唯一索引 |
| post | idx_user_id | user_id | 用户ID索引 |
| post | idx_title | title | 标题索引 |
| picture | idx_user_id | user_id | 用户ID索引 |
| picture | idx_picture_name | picture_name | 图片名称索引 |
| picture | idx_post_id | post_id | 帖子ID索引 |
| comment | idx_user_id | user_id | 用户ID索引 |
| comment | idx_post_id | post_id | 帖子ID索引 |

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

### 4.4 公共查询接口

#### 4.4.1 获取帖子列表
- **接口**: `POST /api/post/postList`
- **描述**: 分页查询帖子列表，支持多条件筛选
- **请求体**: PostQueryRequest
  - 查询条件字段
  - 分页参数（current, pageSize）
- **返回**: IPage<PostListVO> (MyBatis-Plus分页结果)

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

---

## 5. 非功能性需求

### 5.1 性能需求
- **响应时间**: 页面加载时间 ≤ 2秒
- **并发用户**: 支持1000+并发用户
- **图片上传**: 支持文件上传（≤5MB）
- **缓存策略**: 
  - 验证码缓存：5分钟（Redis）
  - Session管理：由Spring Session容器自动管理

### 5.2 安全需求
- **密码安全**: MD5 + 盐值"fish"加密存储
- **认证机制**: HTTP Session认证（服务端存储用户状态，Cookie自动传输Session ID）
- **权限控制**: 
  - @AuthCheck注解标记接口权限
  - AuthInterceptor AOP拦截器进行权限校验
  - 基于角色（admin/user）的访问控制
- **防暴力破解**: 图形验证码（圆圈验证码）
- **跨域安全**: CORS配置（allowCredentials=true支持Cookie/Session）
- **逻辑删除**: @TableLogic防止数据误删
- **统一异常处理**: GlobalExceptionHandler统一异常处理，避免敏感信息泄露

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
- **Redis缓存**: 验证码存储，192.168.163.101:6379
- **文件存储服务**: 图片上传存储（本地或云存储）

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
   → 后端校验验证码 + 用户名密码
   → 校验通过，将User对象存入request.getSession().setAttribute(TOKEN_KEY, user)
   → Spring自动通过Set-Cookie响应头返回Session ID（JSESSIONID）
   → 前端localStorage保存用户基本信息用于展示

2. 后续请求：
   浏览器自动在请求头Cookie中携带JSESSIONID
   → 后端通过request.getSession().getAttribute(TOKEN_KEY)获取User对象
   → AuthInterceptor从Session获取用户进行权限校验

3. 退出登录：
   前端清除localStorage中的用户信息
   → 前端状态重置为未登录
   → Session在服务端过期后自动失效
```

---

## 7. 业务规则

### 7.1 用户相关规则
- **用户名**: 6-11个字符，必须唯一
- **密码**: 8-20个字符，注册时需确认密码一致
- **昵称**: 5-11个字符，必须唯一，默认"小鱼籽_+随机字符串"
- **新用户**: 默认角色为"user"（普通用户）
- **管理员**: 可封禁/解封用户，但不能封禁自己
- **删除操作**: 采用逻辑删除（isDelete字段，@TableLogic）

### 7.2 内容相关规则
- **帖子**: 必须包含标题和内容
- **图片**: 必须关联到用户，可通过postId关联到帖子
- **评论**: 必须关联到帖子和用户，支持二级评论
- **审核机制**: 所有内容默认需要审核（status=2-待审核）
- **隐私设置**: 帖子/图片可设置公开/私密
- **帖子统计**: 自动维护点赞数、收藏数、评论数、查看数

### 7.3 权限相关规则
- **普通用户**: 只能管理自己的内容和信息
- **管理员**: 可以管理所有用户和内容
- **接口权限**: 通过@AuthCheck注解控制
- **权限校验**: AuthInterceptor AOP拦截器统一处理
- **Session管理**: 用户登录后信息存储在服务端Session中，Cookie自动传输Session ID

### 7.4 验证码规则
- **验证码类型**: 圆圈图形验证码（Hutool CircleCaptcha）
- **验证码长度**: 5位
- **验证码有效期**: 5分钟
- **存储位置**: Redis
- **使用场景**: 注册、登录
- **校验机制**: 比对Redis中存储的验证码

### 7.5 社交关系规则
- **关注**: 用户可以关注其他用户
- **粉丝**: 被关注者成为粉丝
- **收藏**: 用户可以收藏帖子
- **点赞**: 用户可以点赞帖子
- **隐私控制**: 用户可设置关注/粉丝/收藏/点赞列表的公开性

### 7.6 图片上传规则
- **文件大小限制**: 最大5MB
- **支持格式**: 常见图片格式
- **上传场景**: 
  - 头像上传（需指定用户ID）
  - 帖子图片上传（自动关联当前用户）
- **存储方式**: 返回图片URL

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
- **数据库**: FishPics
- **Redis Host**: 192.168.163.101
- **Redis Port**: 6379

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
┌──▼┐┌▼──┐┌▼──┐
│MySQL││Redis││文件 │
│     ││     ││存储 │
└─────┘└─────┘└─────┘
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
│   └── index.js              # Axios封装（请求/响应拦截）
├── assets/                   # 静态资源
├── components/               # 通用组件
│   ├── ErrorBoundary.jsx     # 错误边界
│   ├── FunnyBackground.jsx   # 趣味背景动画
│   ├── GlobalLayout.jsx      # 全局布局
│   └── ProtectedRoute.jsx    # 路由权限保护
├── context/
│   └── AuthContext.jsx       # 认证状态管理
├── pages/                    # 页面组件
│   ├── AdminUserList.jsx     # 管理员用户列表
│   ├── AIManagement.jsx      # AI管理
│   ├── CommunitySquare.jsx   # 社区广场
│   ├── HomePage.jsx          # 主页
│   ├── NotFound.jsx          # 404页面
│   ├── Notifications.jsx     # 通知中心
│   ├── PrivateSpace.jsx      # 私人空间
│   ├── SpaceManagement.jsx   # 空间管理
│   ├── TeamManagement.jsx    # 团队管理
│   ├── TeamSpace.jsx         # 团队空间
│   ├── UserManagement.jsx    # 用户管理
│   └── UserProfile.jsx       # 用户资料
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
- **/community** → CommunitySquare（社区广场，需登录）
- **/private-space** → PrivateSpace（私人空间，需登录）
- **/team-space** → TeamSpace（团队空间，需登录）
- **/notifications** → Notifications（通知中心，需登录）
- **/admin/users** → UserManagement（用户管理，需管理员权限）
- **/admin/spaces** → SpaceManagement（空间管理，需管理员权限）
- **/admin/teams** → TeamManagement（团队管理，需管理员权限）
- **/admin/ai** → AIManagement（AI管理，需管理员权限）
- **/admin/user-list** → AdminUserList（管理员用户列表，需管理员权限）
- **/404** → NotFound（404页面）
- **\*** → NotFound（404页面）

### 9.3 状态管理
- **认证状态**: AuthContext（Context API）
  - userInfo: 当前用户信息（存储于localStorage用于前端展示）
  - isAuthenticated: 是否已登录
  - login: 登录函数（保存用户信息到localStorage）
  - logout: 登出函数（清除localStorage中的用户信息）
- **主题状态**: ThemeProvider（Ant Design主题）
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
- getUserMyself(): 获取个人信息
- getUser(): 获取当前用户信息
- getAdminUser(userId): 管理员获取用户详情
- editUser(data): 编辑个人信息
- uploadAvatar(formData, onProgress): 上传头像
- uploadPostPicture(formData, onProgress): 上传帖子图片

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
│   │   ├── CorsConfig.java     # 跨域配置（allowCredentials=true）
│   │   ├── JsonConfig.java     # JSON配置
│   │   └── MybatisPlusConfig.java # MyBatis-Plus配置
│   ├── constants/
│   │   ├── RedisConstants.java # Redis常量（LOGIN_CODE_KEY, REGISTER_CODE_KEY, TOKEN_KEY）
│   │   └── UserConstants.java  # 用户常量
│   ├── exception/
│   │   ├── BaseException.java  # 基础异常
│   │   ├── ExceptionCode.java  # 异常编码
│   │   └── ExcUtils.java       # 异常工具
│   ├── response/
│   │   ├── Response.java       # 统一响应 {code, message, data}
│   │   └── ResUtils.java       # 响应工具
│   └── utils/                  # 工具类
├── controller/                 # 控制器层
│   ├── UserController.java     # 用户控制器
│   ├── PostController.java     # 帖子控制器
│   ├── PictureController.java  # 图片控制器
│   └── SystemController.java   # 系统控制器
├── dto/                        # 数据传输对象
│   ├── base/
│   │   ├── PageRequest.java    # 分页请求
│   │   └── DeleteById.java     # 删除请求
│   ├── picture/
│   │   └── PictureMessage.java # 图片消息DTO
│   ├── post/
│   │   ├── EditPostRequest.java    # 编辑帖子请求
│   │   ├── PostQueryRequest.java   # 帖子查询请求
│   │   ├── PostQueryWrapper.java   # 帖子查询包装器
│   │   └── UploadPostRequest.java  # 上传帖子请求
│   └── user/
│       ├── UserEditByAdminRequest.java  # 管理员编辑用户请求
│       ├── UserEditRequest.java         # 用户编辑请求
│       ├── UserIdRequest.java           # 用户ID请求
│       ├── UserLoginRequest.java        # 用户登录请求
│       ├── UserQueryWrapper.java        # 用户查询包装器
│       └── UserRequestRequest.java      # 用户注册请求
├── entity/                     # 实体类
│   ├── Comment.java            # 评论实体
│   ├── Picture.java            # 图片实体
│   ├── Post.java               # 帖子实体
│   ├── User.java               # 用户实体
│   ├── UserFans.java           # 粉丝实体
│   ├── UserPostCollect.java    # 收藏实体
│   ├── UserPostLikes.java      # 点赞实体
│   └── userFollows.java        # 关注实体
├── enums/
│   └── UserRoleEnum.java       # 用户角色枚举
├── mapper/                     # 数据访问层
│   ├── CommentMapper.java      # 评论Mapper
│   ├── PictureMapper.java      # 图片Mapper
│   ├── PostMapper.java         # 帖子Mapper
│   ├── UserFansMapper.java     # 粉丝Mapper
│   ├── UserFollowsMapper.java  # 关注Mapper
│   ├── UserMapper.java         # 用户Mapper
│   ├── UserPostCollectMapper.java # 收藏Mapper
│   └── UserPostLikesMapper.java   # 点赞Mapper
├── service/                    # 服务层
│   ├── CommentService.java     # 评论服务接口
│   ├── CosService.java         # COS对象存储服务接口
│   ├── LikesUserByIdService.java # 按用户ID查询点赞服务接口
│   ├── PictureService.java     # 图片服务接口
│   ├── PostService.java        # 帖子服务接口
│   ├── UserFansService.java    # 粉丝服务接口
│   ├── UserPostCollectService.java # 收藏服务接口
│   ├── UserPostLikesService.java   # 点赞服务接口
│   ├── UserService.java        # 用户服务接口
│   └── impl/
│       ├── CommentServiceImpl.java
│       ├── LikesUserByIdServiceImpl.java
│       ├── PictureServiceImpl.java
│       ├── PostServiceImpl.java
│       ├── UserFansServiceImpl.java
│       ├── UserPostCollectServiceImpl.java
│       ├── UserPostLikesServiceImpl.java
│       └── UserServiceImpl.java
└── vo/                         # 视图对象
    ├── picture/
    │   └── PicturePostVO.java  # 帖子图片VO
    ├── post/
    │   ├── PostDetailVO.java   # 帖子详情VO
    │   └── PostListVO.java     # 帖子列表VO
    └── user/
        ├── CheckCodeVO.java    # 验证码VO
        ├── UserLoginVO.java    # 用户登录VO（不含loginToken）
        └── UserMessageVO.java  # 用户信息VO
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
- getCheckCode(): 获取图形验证码
- getLoginUser(): 获取当前登录用户（从Session获取）
- userRegister(): 用户注册
- userLogin(): 用户登录（存储用户到Session）
- newQueryWrapper(): 构造查询条件
- getUserList(): 获取用户列表（管理员）
- setStatus(): 设置用户状态（管理员）
- editUser(): 编辑用户（管理员）
- getMyselfMessage(): 获取个人主页信息
- editMyself(): 编辑个人信息
- isMe(): 判断是否是自己的信息

#### PostService
- uploadPost(): 上传帖子
- getPost(): 获取帖子详情
- editPost(): 编辑帖子
- getPostList(): 获取帖子列表
- newQueryWrapper(): 构造帖子查询条件
- likePost(): 点赞帖子

#### PictureService
- uploadAvatar(): 上传头像
- uploadPicture4Post(): 上传帖子图片
- setPicturePostId(): 设置图片与帖子的关联

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
- **Session管理**: 
  - 用户登录信息存储在服务端Session中
  - 由Spring容器自动管理Session生命周期
- **分页查询**: MyBatis-Plus分页插件
- **索引优化**: 关键字段建立索引（username, nickname, user_id, post_id）
- **逻辑删除**: @TableLogic避免物理删除性能损耗
- **连接池**: MySQL连接池配置

### 12.2 安全措施
- **密码加密**: MD5 + 盐值"fish"
- **Session认证**: 服务端状态管理，Cookie自动传输Session ID
- **CORS配置**: allowCredentials=true支持Cookie/Session跨域传输
- **权限控制**: @AuthCheck + AuthInterceptor
- **验证码防护**: 图形验证码防暴力破解
- **SQL防护**: MyBatis-Plus预编译语句
- **逻辑删除**: 防止数据误删
- **异常处理**: 统一异常处理，避免敏感信息泄露
- **文件大小限制**: 图片上传限制5MB

### 12.3 常量管理
- **RedisConstants**: Redis键名前缀（LOGIN_CODE_KEY, REGISTER_CODE_KEY, TOKEN_KEY）
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
- **昵称**: 5-11字符，唯一，默认"小鱼籽_+随机字符串"
- **验证码**: 5位，5分钟有效
- **Session**: 由Spring容器管理生命周期
- **权限**: admin/user两级
- **状态**: 1-正常, 0-禁用, 2-待审核
- **隐私**: 0-公开, 1-私密
- **文件上传**: 最大5MB
- **响应码**: 1-成功，其他-失败
