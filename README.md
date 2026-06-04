<div align="center">

# FishPics AI 图片素材协作平台

![Java](https://img.shields.io/badge/Java-21-007396) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.0-6DB33F) ![MyBatis Plus](https://img.shields.io/badge/MyBatis_Plus-3.5.14-C6534B) ![MySQL](https://img.shields.io/badge/MySQL-8-4479A1) ![Redis](https://img.shields.io/badge/Redis-6-DC382D) ![Redisson](https://img.shields.io/badge/Redisson-3.27.0-B31B1B) ![Knife4j](https://img.shields.io/badge/Knife4j-4.4.0-009688) ![腾讯云 COS](https://img.shields.io/badge/腾讯云_COS-5.6.227-0052D9) ![Spring AI Alibaba](https://img.shields.io/badge/Spring_AI_Alibaba-1.1.2.3-00A1D6) ![RocketMQ](https://img.shields.io/badge/RocketMQ-2.3.2-D77310)

![React](https://img.shields.io/badge/React-19.2-61DAFB) ![Vite](https://img.shields.io/badge/Vite-8.0-646CFF) ![Ant Design](https://img.shields.io/badge/Ant_Design-6.3-1677FF) ![React Router](https://img.shields.io/badge/React_Router-7.14-CA4245) ![Axios](https://img.shields.io/badge/Axios-1.15-5A29E4)

[GitHub](https://github.com/FishDuM/AI.Image.Material.Collaboration.Platform) | [Gitee](https://gitee.com/dumhfdy/AI.Image.Material.Collaboration.Platform) | [头歌](https://code.educoder.net/pfqxsyecz/AI.Image.Material.Collaboration.Platform)

</div>

## 项目简介

FishPics 是一个面向图片素材管理、社区分享与团队协作的前后端分离平台，围绕图片上传、素材归档、内容发布、用户互动、空间协作、后台审核和 AI 图片处理构建完整业务流程。系统既支持普通用户进行图片上传、帖子发布、评论点赞收藏等社区行为，也支持管理员对用户、图片、帖子、评论、空间和系统配置进行统一治理。

后端以 Spring Boot 为核心，承担认证授权、业务规则校验、数据持久化、对象存储对接、缓存管理、消息队列和 AI 能力编排等职责。图片文件统一上传至腾讯云 COS，数据库保存图片元数据和业务关联；用户登录态通过 Token + Redis 管理；管理员权限通过自定义注解与 AOP 切面控制；AI 图片标注通过 Spring AI Alibaba Agent 驱动，文生图通过 DashScope SDK 调用万相模型。

平台在数据模型上覆盖用户、图片、帖子、评论、空间、社交关系、系统配置和 AI 任务等核心领域；在工程实现上采用 DTO/VO 分层、统一响应、全局异常处理、MyBatis-Plus 分页、RocketMQ 消息队列和 Knife4j 接口文档，保证接口结构清晰、业务边界明确、后续扩展成本可控。前端主要提供社区广场、私人空间、团队空间、后台管理和移动端页面，用于承载完整的业务操作闭环。

## 项目亮点

- **Token + Redis 登录体系**：UUID Token + Redis 映射，恢复用户上下文
- **RBAC 权限系统**：三级权限 + AOP 切面，AND/OR 匹配模式
- **多级缓存架构**：Caffeine L1 + Redis L2 双级缓存，自动回源
- **图片对象存储**：腾讯云 COS + 元数据落库，空间容量扣减
- **互动并发控制**：Redisson 分布式锁防重复，条件更新降并发
- **异步任务通知**：RocketMQ 异步 + WebSocket 实时推送
- **用户兴趣画像**：行为权重 + 标签匹配，定时刷新画像
- **审计日志系统**：@AuditLog + AOP 自动记录操作日志
- **AI 图片能力**：Spring AI + DashScope 驱动标注与生成
- **系统配置缓存**：热点配置 Redis 缓存，减少 DB 查询

## 主要功能

### 用户与权限

- 用户注册、登录、退出登录，支持图形验证码。
- 当前用户信息、个人主页、公开主页、资料编辑和头像上传。
- 关注/取消关注、粉丝列表、关注列表。
- 用户搜索：按用户名或昵称模糊搜索。
- 用户隐私设置：关注、粉丝、点赞、收藏列表可见性控制。
- RBAC 权限管理：系统级角色、团队级角色、资源级权限，支持角色继承。
- 管理员用户管理：用户列表、多条件筛选、封禁/解封、资料编辑、角色分配。

### 图片与空间

- 图片上传、头像上传、图片信息编辑、批量删除。
- 支持通过 URL 保存图片到空间（自动下载、校验魔数、上传 COS）。
- 图片文件存储到腾讯云 COS，图片名称、URL、宽高、大小、标签、简介、状态等元数据落库。
- 普通/VIP/SVIP 用户上传大小限制：3MB / 5MB / 20MB。
- 图片默认进入待审核状态，管理员上传可自动通过审核。
- 图片公开到首页由 `picture.is_private` 控制：`0` 不公开，`1` 公开。
- 图片推荐：基于用户兴趣画像的个性化图片推荐。
- 私人空间与团队空间管理，支持空间创建、详情、更新、图片分页、搜索和容量统计。
- 空间容量按等级分配：私人空间 512MB/5GB/10GB，团队空间 512MB/30GB/50GB。
- 团队成员管理：邀请、移除成员，变更成员角色。

### 帖子与互动

- 帖子发布、编辑、详情和分页列表。
- 帖子支持标题、内容、多图片、封面图、隐私配置。
- 帖子与图片通过 `picture_child` 维护有序关联，支持最多 15 张图片。
- 点赞、收藏、评论、二级回复。
- 我的帖子、我的收藏、我的点赞分页查询。
- 帖子推荐：基于用户兴趣画像的个性化帖子推荐。
- 帖子统计字段：点赞数、收藏数、评论数、浏览数、热度值（定时任务每10分钟更新）。
- 用户兴趣画像：根据点赞（权重+3）、收藏（权重+5）行为自动分析标签权重，定时任务每30分钟刷新。
- 管理员帖子管理：列表、审核、删除。

### AI 能力

- VIP/SVIP 用户可使用 AI 图片标注（自动识别标签、名称和描述）和 AI 文生图功能。
- AI 图片标注：基于 Spring AI Alibaba Agent，通过通义千问视觉理解模型自动提取图片标签、名称和介绍，支持异步任务提交和结果查询。
- AI 文生图：通过 DashScope SDK 调用万相图像生成模型，支持多种绘图风格和参数配置，支持异步任务提交和结果查询。
- AI 管理后台：任务列表分页查询、任务统计（按状态和类型）、AI 功能开关动态配置（标注/编辑/生成/推荐）。

### 系统与后台

- 分类标签管理：查询、添加、删除。
- 首页跑马灯管理：查询、添加、删除。
- 评论后台管理：评论列表、审核、删除。
- 空间后台管理：空间列表、配置更新、删除、启用/禁用。
- AI 后台管理：任务列表、任务统计、AI 功能开关配置。
- 审计日志：操作日志自动记录，管理员可查询和筛选。
- 系统统计：用户、图片、帖子等核心指标概览。
- 角色权限管理：系统级角色列表查询。
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
       -> 返回图片 id/url
```

### AI 调用链路

```text
用户提交 AI 请求 -> 创建 Task(PENDING) -> RocketMQ 发送异步消息
              -> TaskConsumer 消费消息 -> 分发到对应 Handler
              -> 标签识别：AiTagTaskHandler 通过 Spring AI Alibaba Agent 分析图片
              -> 文生图：AiDrawTaskHandler 通过 DashScope SDK 调用万相模型
              -> 更新 Task 状态(DONE/FAILED) -> WebSocket 推送完成通知
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
| `space_team_member` | 团队空间成员关系和角色                           |
| `user_fans`         | 关注和粉丝关系                                   |
| `user_post_likes`   | 用户点赞帖子关系                                 |
| `user_post_collect` | 用户收藏帖子关系                                 |
| `user_interest_profile` | 用户兴趣画像（标签权重）                       |
| `pic_system`        | 分类标签、跑马灯、AI 开关等系统配置              |
| `task`              | 异步任务（AI 标注、文生图等）                    |
| `sys_role`          | 系统角色定义（支持继承）                         |
| `sys_permission`    | 系统权限点（系统级/团队级/资源级）               |
| `sys_role_permission` | 角色与权限的关联                               |
| `sys_user_role`     | 用户与角色的关联                                 |
| `sys_audit_log`     | 审计操作日志                                     |

## 项目结构

### 后端包路径

```text
src/FishPics-backend/src/main/java/hk/ljx/fishpicsbackend
├── FishPicsBackendApplication.java     # Spring Boot 启动类
├── user/                               # 用户模块（controller, dto, entity, service, vo）
├── picture/                            # 图片模块
├── post/                               # 帖子模块
├── comment/                            # 评论模块
├── space/                              # 空间模块
├── system/                             # 系统配置模块
├── ai/                                 # AI 能力模块
├── task/                               # 异步任务模块（consumer, handler, message, service）
├── permission/                         # RBAC 权限模块（controller, entity, service）
├── websocket/                          # WebSocket 实时通知
├── mapper/                             # MyBatis-Plus Mapper 接口
└── common/                             # 通用基础设施
    ├── config/                         # CORS、COS、JSON、MyBatis、Async、WebSocket、RocketMQ 等配置
    ├── cache/                          # 多级缓存（Caffeine L1 + Redis L2）
    ├── enums/                          # 异常码、绘图风格、图片尺寸枚举
    ├── exception/                      # 全局异常处理
    ├── interceptor/                    # 登录拦截器、Token 刷新拦截器
    ├── response/                       # 统一响应模型
    ├── scheduled/                      # 定时任务（热度计算、用户画像刷新）
    ├── annotation/                     # 自定义注解（@AuthCheck、@AuditLog）
    ├── aop/                            # AOP 切面（权限校验、审计日志）
    ├── constants/                      # 常量（系统、Redis、空间、用户、缓存）
    ├── dto/                            # 通用 DTO
    └── utils/                          # 工具类
```

### 后端资源路径

```text
src/FishPics-backend/src/main/resources
├── application.yml                     # 通用配置
├── application-local.yml               # 本地密钥配置
└── mapper/                             # MyBatis XML 映射

src/FishPics-backend/src/sql/create.sql # 数据库建表脚本
```

### 前端包路径

```text
src/FishPic-frontend/src
├── api/                                # Axios 请求封装
├── components/                         # 通用组件
│   └── shared/                         #   共享 UI 组件（PostCard、CommentSection、ImageUploadModal 等）
├── context/                            # React Context（AuthContext、ThemeContext）
├── hooks/                              # 自定义 Hooks（useAuthModal、useIsMobile、useRequestUtils、useWebSocket）
├── pages/                              # 页面组件（PC 社区、空间、后台管理、移动端独立页面）
├── styles/                             # 全局样式（animations、carousel、shared CSS）
├── utils/                              # 工具函数（clipboard、constants、storage、upload、logger）
├── App.jsx                             # 路由入口
├── App.css / index.css                 # 全局样式
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
