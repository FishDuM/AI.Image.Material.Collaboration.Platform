# FishPics AI 图片素材协作平台

<div align="center">

![Java](https://img.shields.io/badge/Java-21-007396) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.0-6DB33F) ![MyBatis Plus](https://img.shields.io/badge/MyBatis_Plus-3.5.14-C6534B) ![MySQL](https://img.shields.io/badge/MySQL-8-4479A1) ![Redis](https://img.shields.io/badge/Redis-6-DC382D) ![Redisson](https://img.shields.io/badge/Redisson-3.27.0-B31B1B) ![Knife4j](https://img.shields.io/badge/Knife4j-4.4.0-009688) ![腾讯云 COS](https://img.shields.io/badge/腾讯云_COS-5.6.227-0052D9) ![Spring AI Alibaba](https://img.shields.io/badge/Spring_AI_Alibaba-1.1.2.0-00A1D6)

![React](https://img.shields.io/badge/React-19.2-61DAFB) ![Vite](https://img.shields.io/badge/Vite-8.0-646CFF) ![Ant Design](https://img.shields.io/badge/Ant_Design-6.3-1677FF) ![React Router](https://img.shields.io/badge/React_Router-7.14-CA4245) ![Axios](https://img.shields.io/badge/Axios-1.15-5A29E4)

[GitHub](https://github.com/FishDuM/AI.Image.Material.Collaboration.Platform) | [Gitee](https://gitee.com/dumhfdy/AI.Image.Material.Collaboration.Platform) | [头歌](https://code.educoder.net/pfqxsyecz/AI.Image.Material.Collaboration.Platform)

</div>

## 项目简介

FishPics 是一个面向图片素材管理、社区分享与团队协作的前后端分离平台，围绕图片上传、素材归档、内容发布、用户互动、空间协作、后台审核和 AI 图片处理构建完整业务流程。系统既支持普通用户进行图片上传、帖子发布、评论点赞收藏等社区行为，也支持管理员对用户、图片、帖子、评论、空间和系统配置进行统一治理。

后端以 Spring Boot 为核心，承担认证授权、业务规则校验、数据持久化、对象存储对接、缓存管理、异步事件处理和 AI 任务编排等职责。图片文件统一上传至腾讯云 COS，数据库保存图片元数据和业务关联；用户登录态通过 Token + Redis 管理；管理员权限通过自定义注解与 AOP 切面控制；图片标注、图片生成、图片编辑、图片推荐等 AI 能力通过任务表和异步处理链路统一调度。

平台在数据模型上覆盖用户、图片、帖子、评论、空间、社交关系、系统配置和 AI 任务等核心领域；在工程实现上采用 DTO/VO 分层、统一响应、全局异常处理、MyBatis-Plus 分页、Redis Stream 事件消费和 Knife4j 接口文档，保证接口结构清晰、业务边界明确、后续扩展成本可控。前端主要提供社区广场、私人空间、团队空间、后台管理和移动端页面，用于承载完整的业务操作闭环。

## 技术栈

### 后端

| 分类       | 技术                                                              |
| ---------- | ----------------------------------------------------------------- |
| 基础框架   | Java 21, Spring Boot 3.3.0, Spring MVC                            |
| 数据访问   | MyBatis-Plus 3.5.14, MyBatis XML Mapper, MySQL 8                  |
| 缓存与并发 | Redis, `setIfAbsent` 短锁, Redisson RStream, Redis Stream         |
| 权限与安全 | Token + Redis, Spring Interceptor, AOP, 自定义 `@AuthCheck`       |
| 文件存储   | 腾讯云 COS, 图片元数据落库                                        |
| AI 能力    | Spring AI Alibaba, DashScope, 通义千问视觉理解, 万相图像生成/编辑 |
| 工程能力   | Knife4j OpenAPI3, Lombok, Hutool, 全局异常处理, 统一响应模型      |

### 前端

| 分类       | 技术                                            |
| ---------- | ----------------------------------------------- |
| 基础框架   | React 19, Vite 8, Ant Design 6                  |
| 路由与状态 | React Router v7, Context API                    |
| 请求与工具 | Axios, dayjs, ESLint                            |
| 页面形态   | PC 管理后台、社区页面、空间页面、移动端独立页面 |

## 项目亮点

- **Token + Redis 登录体系**：登录后生成 UUID Token，Redis 维护 Token 与用户信息映射；`RefreshTokenInterceptor` 恢复用户上下文，`LoginInterceptor` 统一拦截登录态接口。
- **注解式权限控制**：通过自定义 `@AuthCheck` + AOP 切面实现管理员接口权限校验，业务接口只声明角色要求，降低鉴权代码侵入。
- **图片对象存储与空间容量管理**：图片文件上传到腾讯云 COS，数据库保存图片元数据；上传时按用户等级校验文件大小，并根据私人/团队空间容量扣减使用量。
- **内容审核闭环**：图片、帖子、评论均具备正常、禁用、待审核状态；管理员可在后台完成用户封禁、图片审核、帖子审核、评论审核和空间状态管理。
- **互动并发控制**：点赞、收藏等高频互动使用 Redis `setIfAbsent` 短锁限制同一用户重复操作，配合统计字段条件更新降低并发写异常。
- **异步事件处理**：通过 Redisson RStream 封装 AI 标注、AI 任务、COS 清理和社交通知事件；Stream 不可用时降级到异步或同步处理，保证核心链路可用。
- **AI 图片能力接入**：封装图片标注、图片编辑、图片生成、图片推荐接口，任务统一落到 `ai_task` 表，支持用户查询任务进度与管理员统计管理。
- **DTO/VO 分层与统一响应**：请求参数、数据库实体和响应视图分离，统一 `Response<T>` 出参和全局异常处理，便于前后端联调和接口文档生成。
- **系统配置缓存**：分类标签、跑马灯等系统配置持久化在 `pic_system`，并通过 Redis 做热点配置缓存。

## 主要功能

### 用户与权限

- 用户注册、登录、退出登录，支持图形验证码。
- 当前用户信息、个人主页、公开主页、资料编辑和头像上传。
- 关注/取消关注、粉丝列表、关注列表。
- 用户隐私设置：关注、粉丝、点赞、收藏列表可见性控制。
- 管理员用户管理：用户列表、多条件筛选、封禁/解封、资料编辑。

### 图片与空间

- 图片上传、头像上传、图片信息编辑、批量删除。
- 图片文件存储到腾讯云 COS，图片名称、URL、宽高、大小、标签、简介、状态等元数据落库。
- 普通/VIP/SVIP 用户上传大小限制：3MB / 5MB / 20MB。
- 图片默认进入待审核状态，管理员上传可自动通过审核。
- 图片公开到首页由 `picture.is_private` 控制：`0` 不公开，`1` 公开。
- 私人空间与团队空间管理，支持空间创建、详情、更新、图片分页、搜索和容量统计。
- 空间容量按等级分配：私人空间 512MB/5GB/10GB，团队空间 512MB/30GB/50GB。

### 帖子与互动

- 帖子发布、编辑、详情和分页列表。
- 帖子支持标题、内容、多图片、封面图、隐私配置。
- 帖子与图片通过 `picture_child` 维护有序关联，支持最多 15 张图片。
- 点赞、收藏、评论、二级回复。
- 我的帖子、我的收藏、我的点赞分页查询。
- 帖子统计字段：点赞数、收藏数、评论数、浏览数、热度值。
- 管理员帖子管理：列表、审核、删除。

### AI 能力

- VIP/SVIP 用户可提交图片标注、图片编辑、图片生成、图片推荐任务。
- 图片上传后可通过 Redis Stream 异步触发 AI 自动标注。
- AI 任务统一存储在 `ai_task` 表，包含任务类型、输入 JSON、输出 JSON、状态和错误信息。
- 管理员可查询 AI 任务列表、任务统计和 AI 功能开关配置。

### 系统与后台

- 分类标签管理：查询、添加、删除。
- 首页跑马灯管理：查询、添加、删除。
- 评论后台管理：评论列表、审核、删除。
- 空间后台管理：空间列表、配置更新、删除、启用/禁用。
- Knife4j 自动生成接口文档，访问路径：`http://localhost:8080/api/doc.html`。

## 后端设计说明

### 认证链路

```text
用户登录 -> 校验验证码和密码 -> 生成 Token -> Redis 保存 token:userId 与 userId:user
       -> 请求携带 Authorization -> RefreshTokenInterceptor 恢复 UserHolder
       -> LoginInterceptor 校验登录态 -> Controller/Service 获取当前用户
```

### 图片上传链路

```text
上传文件 -> 校验登录态和等级大小限制 -> 上传 COS -> 读取图片元数据
       -> 校验目标空间容量 -> 更新空间已用容量 -> 写入 picture
       -> 投递 AI 标注事件 -> 返回图片 id/url
```

### AI 任务链路

```text
用户提交 AI 请求 -> 创建 ai_task(status=0) -> Redis Stream 投递任务
              -> Consumer/AsyncProcessor 调用模型服务
              -> 成功写 output_data，失败写 error_msg -> 更新任务状态
```

### 核心数据表

| 表名                | 说明                                             |
| ------------------- | ------------------------------------------------ |
| `user`              | 用户账号、资料、角色、状态、等级、隐私配置       |
| `picture`           | 图片元数据、COS URL、空间归属、审核状态、AI 标签 |
| `post`              | 帖子内容、封面、隐私、审核状态和互动统计         |
| `picture_child`     | 帖子与图片的有序关联                             |
| `comment`           | 评论、二级回复和审核状态                         |
| `space`             | 私人/团队空间、容量、成员和状态                  |
| `user_fans`         | 关注和粉丝关系                                   |
| `user_post_likes`   | 用户点赞帖子关系                                 |
| `user_post_collect` | 用户收藏帖子关系                                 |
| `pic_system`        | 分类标签、跑马灯、AI 开关等系统配置              |
| `ai_task`           | AI 标注、编辑、生成、推荐任务                    |

## 项目结构

### 后端包路径

```text
src/FishPics-backend/src/main/java/hk/ljx/fishpicsbackend
├── FishPicsBackendApplication.java     # Spring Boot 启动类
├── user/                               # 用户模块
│   ├── UserController.java             #   用户、关注、粉丝、管理员接口
│   ├── UserService.java                #   业务接口
│   ├── UserServiceImpl.java            #   业务实现
│   ├── User.java, UserFans.java,       #   数据库实体
│   │   UserPostCollect.java,
│   │   UserPostLikes.java
│   ├── dto/                            #   登录、注册、编辑、隐私、查询
│   └── vo/                             #   登录态、主页、粉丝关注、管理员
├── picture/                            # 图片模块
│   ├── PictureController.java          #   上传、删除、更新、审核接口
│   ├── PictureService.java
│   ├── PictureServiceImpl.java
│   ├── Picture.java, PictureChild.java #   数据库实体
│   ├── dto/                            #   删除、更新、元数据
│   └── vo/                             #   列表、后台、空间图片、编辑
├── post/                               # 帖子模块
│   ├── PostController.java             #   发帖、详情、编辑、互动、管理接口
│   ├── PostService.java
│   ├── PostServiceImpl.java
│   ├── Post.java                       #   数据库实体
│   ├── dto/                            #   发帖、编辑、列表查询
│   └── vo/                             #   列表、详情
├── comment/                            # 评论模块
│   ├── CommentController.java          #   创建、列表、删除、审核接口
│   ├── CommentService.java
│   ├── CommentServiceImpl.java
│   ├── Comment.java                    #   数据库实体
│   ├── dto/                            #   创建与查询
│   └── vo/                             #   评论展示
├── space/                              # 空间模块
│   ├── SpaceController.java            #   创建、列表、详情、更新、管理接口
│   ├── SpaceService.java
│   ├── SpaceServiceImpl.java
│   ├── Space.java                      #   数据库实体
│   ├── dto/                            #   创建、更新、后台管理
│   └── vo/                             #   空间展示、团队成员
├── system/                             # 系统配置模块
│   ├── SystemController.java           #   分类标签、跑马灯接口
│   ├── PicSystemService.java
│   ├── PicSystemServiceImpl.java
│   ├── PicSystem.java                  #   数据库实体
│   ├── dto/                            #   标签、跑马灯配置
│   └── vo/                             #   系统展示
├── ai/                                 # AI 能力模块
│   ├── AiController.java               #   标注、生成、编辑、推荐接口
│   ├── AiService.java
│   ├── AiServiceImpl.java
│   ├── dto/                            #   AI 请求模型
│   ├── temp/                           #   临时 DTO
│   └── vo/                             #   AI 响应模型
├── mapper/                             # 所有 MyBatis-Plus Mapper 接口
│   ├── UserMapper.java
│   ├── PictureMapper.java
│   ├── PictureChildMapper.java
│   ├── PostMapper.java
│   ├── CommentMapper.java
│   ├── SpaceMapper.java
│   ├── UserFansMapper.java
│   ├── UserPostCollectMapper.java
│   ├── UserPostLikesMapper.java
│   └── PicSystemMapper.java
└── common/                             # 通用基础设施
    ├── annotation/                     #   AuthCheck 权限注解
    ├── aop/                            #   AuthInterceptor 权限切面
    ├── config/                         #   CORS、COS、JSON、MyBatis、Async 配置
    ├── constants/                      #   Redis、User、Space、Sys 常量
    ├── dto/                            #   DeleteById、PageRequest 基础请求
    ├── enums/                          #   UserRoleEnum 角色枚举
    ├── exception/                      #   BaseException、ExceptionCode、ExcUtils、GlobalExceptionHandler
    ├── interceptor/                    #   LoginInterceptor、RefreshTokenInterceptor、MvcConfig
    ├── response/                       #   Response、ResUtils 统一响应
    └── utils/                          #   CosService、UserHolder
```

### 后端资源路径

```text
src/FishPics-backend/src/main/resources
├── application.yml                     # 通用配置，启用 local profile
├── application-local.yml               # 本地数据库、Redis、COS、AI 密钥配置
└── mapper/                             # MyBatis XML，自定义 SQL 映射

src/FishPics-backend/src/sql/create.sql # 数据库建表脚本
```

### 前端包路径

```text
src/FishPic-frontend/src
├── api/                                # Axios 请求封装
│   └── index.js                        # 拦截器、请求去重、所有 API 函数
├── assets/                             # 静态资源（空目录）
├── components/                         # 通用组件
│   ├── CommentSection.jsx              #   评论列表与回复
│   ├── CreateEditPostModal.jsx         #   创建/编辑帖子弹窗
│   ├── ErrorBoundary.jsx               #   React 错误边界
│   ├── FollowUserList.jsx              #   关注/粉丝列表
│   ├── GlobalLayout.jsx                #   主布局：头部、侧边栏、底部导航
│   ├── MobilePageWrapper.jsx           #   移动端页面壳
│   ├── PostDetailModal.jsx             #   帖子详情弹窗
│   ├── ProtectedRoute.jsx              #   路由守卫
│   └── shared/                         #   共享 UI 组件
│       ├── AuthModals.jsx              #     认证弹窗包装器
│       ├── CategoryBar.jsx             #     分类标签栏
│       ├── ImageUploadModal.jsx        #     图片上传弹窗
│       ├── LoginModal.jsx              #     登录/注册/设置弹窗
│       ├── MobileBottomNav.jsx         #     移动端底部导航
│       ├── PostCard.jsx                #     帖子卡片
│       ├── PostLayout.jsx              #     帖子图文布局
│       ├── PostModal.jsx               #     帖子弹窗包装器
│       ├── SearchBar.jsx               #     搜索栏
│       ├── SpacePickerModal.jsx        #     空间图片选择器
│       ├── UpgradeContent.jsx          #     升级方案内容
│       └── UpgradeModal.jsx            #     升级弹窗
├── context/                            # React Context
│   ├── AuthContext.jsx                 #   认证状态：用户信息、登录态
│   └── ThemeContext.jsx                #   主题切换：深色/浅色
├── hooks/                              # 自定义 Hooks
│   ├── useAuthModal.js                 #   登录/注册弹窗状态管理
│   ├── useIsMobile.js                  #   移动端断点检测（768px）
│   └── useRequestUtils.js              #   AbortController、系统分类缓存、防抖节流
├── pages/                              # 页面组件
│   ├── HomePage.jsx                    #   首页：轮播、瀑布流、搜索、分类
│   ├── CommunitySquare.jsx             #   社区广场：帖子瀑布流
│   ├── UserProfile.jsx                 #   用户主页：帖子/收藏/点赞、头像上传
│   ├── PrivateSpace.jsx                #   私人空间：图片网格、上传、批量操作
│   ├── TeamSpace.jsx                   #   团队空间列表
│   ├── TeamSpaceDetail.jsx             #   团队空间详情
│   ├── Notifications.jsx               #   消息通知（占位）
│   ├── AIImageTools.jsx                #   AI 工具：图片生成、编辑
│   ├── NotFound.jsx                    #   404 页面
│   ├── UserManagement.jsx              #   管理员：用户管理
│   ├── AdminUserList.jsx               #   管理员：用户列表
│   ├── AdminPictureManagement.jsx      #   管理员：图片审核
│   ├── AdminCommentManagement.jsx      #   管理员：评论管理
│   ├── AdminPostManagement.jsx         #   管理员：帖子管理
│   ├── SpaceManagement.jsx             #   管理员：空间管理（占位）
│   ├── TeamManagement.jsx              #   管理员：团队管理
│   ├── SystemManagement.jsx            #   管理员：标签和跑马灯管理
│   ├── AIManagement.jsx                #   管理员：AI 任务监控
│   ├── MobileLoginPage.jsx             #   移动端：登录
│   ├── MobileRegisterPage.jsx          #   移动端：注册
│   ├── MobilePostCreatePage.jsx        #   移动端：创建帖子
│   ├── MobilePostDetailPage.jsx        #   移动端：帖子详情
│   ├── MobileEditPicturePage.jsx       #   移动端：编辑图片
│   ├── MobileEditProfilePage.jsx       #   移动端：编辑资料
│   ├── MobileUpgradePage.jsx           #   移动端：升级空间
│   ├── MobileFollowListPage.jsx        #   移动端：关注列表
│   └── MobileUserProfilePage.jsx       #   移动端：用户主页
├── styles/                             # 全局样式
│   ├── animations.css                  #   动画定义
│   ├── carousel.css                    #   轮播样式
│   └── shared.css                      #   共享样式变量
├── utils/                              # 工具函数
│   ├── constants.js                    #   分页配置、等级映射、格式化函数
│   ├── storage.js                      #   localStorage Token 和用户信息
│   └── uploadConstraints.js            #   文件类型/大小校验
├── App.jsx                             # 路由与页面入口
├── App.css                             # 全局布局样式
├── index.css                           # CSS 自定义属性、深色模式变量
└── main.jsx                            # React 挂载入口
```

## 快速启动

### 后端

```bash
cd src/FishPics-backend
```

1. 创建 MySQL 数据库 `FishPics`，执行 `src/sql/create.sql`。
2. 配置 `src/main/resources/application-local.yml` 中的 MySQL、Redis、COS 和 DashScope 参数。
3. 启动 `FishPicsBackendApplication.java`。
4. 访问接口文档：`http://localhost:8080/api/doc.html`。

### 前端

```bash
cd src/FishPic-frontend
npm install
npm run dev
```

- 前端地址：`http://localhost:5173`
- 后端地址：`http://localhost:8080/api`
- API 文档：`http://localhost:8080/api/doc.html`

## 相关文档

- [软件需求模型](doc/software_requirements_model.md)
- [UML 与数据模型](model/uml_diagrams.md)
