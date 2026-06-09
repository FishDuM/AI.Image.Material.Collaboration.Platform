<div align="center">

# FishPics AI 图片素材协作平台

![Java](https://img.shields.io/badge/Java-21-007396) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.0-6DB33F) ![MyBatis Plus](https://img.shields.io/badge/MyBatis_Plus-3.5.14-C6534B) ![MySQL](https://img.shields.io/badge/MySQL-8-4479A1) ![Redis](https://img.shields.io/badge/Redis-6-DC382D) ![Redisson](https://img.shields.io/badge/Redisson-3.27.0-B31B1B) ![JWT](https://img.shields.io/badge/JWT-0.12.6-000000) ![Knife4j](https://img.shields.io/badge/Knife4j-4.4.0-009688) ![腾讯云 COS](https://img.shields.io/badge/腾讯云_COS-5.6.227-0052D9) ![Spring AI Alibaba](https://img.shields.io/badge/Spring_AI_Alibaba-1.1.2.3-00A1D6) ![RocketMQ](https://img.shields.io/badge/RocketMQ-2.3.1-D77310)

![React](https://img.shields.io/badge/React-19.2-61DAFB) ![Vite](https://img.shields.io/badge/Vite-8.0-646CFF) ![Ant Design](https://img.shields.io/badge/Ant_Design-6.3-1677FF) ![React Router](https://img.shields.io/badge/React_Router-7.14-CA4245) ![Axios](https://img.shields.io/badge/Axios-1.15-5A29E4)

[GitHub](https://github.com/FishDuM/AI.Image.Material.Collaboration.Platform) | [Gitee](https://gitee.com/dumhfdy/AI.Image.Material.Collaboration.Platform) | [头歌](https://code.educoder.net/pfqxsyecz/AI.Image.Material.Collaboration.Platform)

</div>

## 项目简介

FishPics 是一个面向图片素材管理与团队协作的前后端分离平台，围绕图片上传、素材归档、空间协作、分享、后台审核和 AI 图片处理构建完整业务流程。系统支持普通用户进行图片上传、空间管理、图片分享等操作，也支持管理员对用户、图片、空间和系统配置进行统一治理。

后端以 Spring Boot 为核心，承担认证授权、业务规则校验、数据持久化、对象存储对接、缓存管理、消息队列和 AI 能力编排等职责。图片文件统一上传至腾讯云 COS，数据库保存图片元数据和业务关联；用户登录态通过 JWT + Redis 管理，支持自动续签和黑名单登出；管理员权限通过简化 RBAC 模型（user.level 字段）+ @RequireAdmin 注解 + AOP 切面控制；图片上传支持普通上传和分片上传两种方式，文件通过 MD5 去重；AI 图片标注通过 Spring AI Alibaba Agent 驱动，文生图通过 DashScope SDK 调用万相模型。

平台在数据模型上覆盖用户、图片、空间、分享、系统配置和 AI 任务等核心领域；在工程实现上采用 DTO/VO 分层、统一响应、全局异常处理、MyBatis-Plus 分页、RocketMQ 消息队列和 Knife4j 接口文档，保证接口结构清晰、业务边界明确、后续扩展成本可控。

## 项目亮点

- **简化 RBAC 权限体系**：user.level 字段（0=普通/1=VIP/2=SVIP/3=管理员）+ @RequireAdmin 注解，无需复杂角色权限表
- **JWT 认证体系**：JJWT 签发/解析 + Redis 黑名单登出 + 超过 15 分钟自动续签，安全性与体验兼顾
- **统一 VO 设计**：UserVO/PictureVO 通过静态工厂方法 + @JsonInclude(NON_NULL) 灵活控制返回字段
- **分片上传与文件去重**：支持大文件分片上传、秒传校验（MD5+size）、断点续传，file_resource 表基于 MD5 物理去重
- **图片分享**：生成带过期时间和下载权限控制的分享链接，支持预签名 URL 直接访问
- **多级缓存架构**：Caffeine L1 + Redis L2 双级缓存，自动回源
- **图片对象存储**：腾讯云 COS + 元数据落库，空间容量扣减
- **异步任务通知**：RocketMQ 异步 + 任务状态轮询
- **审计日志系统**：@AuditLog + AOP 自动记录操作日志
- **AI 图片能力**：Spring AI + DashScope 驱动标注与生成

## 主要功能

### 用户与权限

- 用户注册、登录、退出登录，支持图形验证码。
- 当前用户信息、个人主页、公开主页、资料编辑和头像上传。
- 用户搜索：按用户名或昵称模糊搜索。
- 用户等级体系：普通用户（level=0）、VIP（level=1）、SVIP（level=2）、管理员（level=3）。
- 管理员用户管理：用户列表、多条件筛选、封禁/解封、资料编辑。

### 图片与空间

- 图片上传（普通上传和分片上传）、头像上传、图片信息编辑、批量删除。
- 分片上传：大文件分片上传、秒传校验（MD5+size 匹配则直接完成）、断点续传（跳过已上传分片）。
- 文件去重：file_resource 表基于 MD5 + 文件大小去重，相同文件仅存储一份，通过引用计数管理生命周期。
- 支持通过 URL 保存图片到空间（自动下载、校验魔数、上传 COS）。
- 图片文件存储到腾讯云 COS，图片名称、URL、宽高、大小、标签、简介、状态等元数据落库。
- 普通/VIP/SVIP 用户上传大小限制：10MB / 1GB / 10GB。
- 图片默认进入待审核状态，管理员上传可自动通过审核。
- 图片公开到首页由 `picture.is_private` 控制：`0` 公开，`1` 私有。
- 图片分享：生成分享链接，支持设置有效期（最长 7 天）和是否允许下载，通过预签名 URL 访问。
- 私人空间与团队空间管理，支持空间创建、详情、更新、图片分页、搜索和容量统计。
- 空间容量按等级分配：私人空间 512MB/50GB/100GB，团队空间 512MB/50GB/100GB。
- 团队成员管理：邀请、移除成员，变更成员角色（OWNER/MEMBER）。

### AI 能力

- VIP/SVIP 用户可使用 AI 图片标注（自动识别标签、名称和描述）和 AI 文生图功能。
- AI 图片标注：基于 Spring AI Alibaba Agent，通过通义千问视觉理解模型自动提取图片标签、名称和介绍，支持异步任务提交和结果查询。
- AI 文生图：通过 DashScope SDK 调用万相图像生成模型，支持多种绘图风格和参数配置，支持异步任务提交和结果查询。
- AI 管理后台：任务列表分页查询、任务统计（按状态和类型）、AI 功能开关动态配置（标注/编辑/生成）。

### 系统与后台

- 分类标签管理：查询、添加、删除。
- 首页跑马灯管理：查询、添加、删除。
- 空间后台管理：空间列表、配置更新、删除、启用/禁用。
- AI 后台管理：任务列表、任务统计、AI 功能开关配置。
- 审计日志：操作日志自动记录，管理员可查询和筛选。
- 系统统计：用户、图片等核心指标概览。
- Knife4j 自动生成接口文档，访问路径：`http://localhost:8080/api/doc.html`。

## 后端设计说明

### 权限体系设计

本项目采用**简化 RBAC 模型**，通过 `user.level` 字段实现权限分级：

```
user.level 权限等级：
  0 = 普通用户（基础功能）
  1 = VIP 用户（基础功能 + AI 能力 + 大文件上传）
  2 = SVIP 用户（基础功能 + AI 能力 + 更大存储配额）
  3 = 管理员（所有功能 + 后台管理）
```

**权限校验方式：**
- 登录校验：JWT 解析 + 黑名单检查 + Redis 权限上下文恢复
- 管理员校验：`@RequireAdmin` 注解 + AOP 切面，检查 `level >= 3`
- VIP 校验：代码中检查 `level >= 1`

**对比传统 RBAC 的优势：**
- 无需维护多张权限表（role、permission、role_permission、sys_user_role）
- 无需复杂的权限码解析和匹配逻辑
- 一个字段搞定权限分级，简单直观

### 认证链路

```text
用户登录 -> 校验验证码和密码 -> JWT 签发（30 分钟有效）-> Redis 保存权限上下文
       -> 请求携带 Authorization: Bearer <JWT>
       -> TokenRefreshInterceptor：JWT 解析 -> 黑名单检查 -> 权限上下文加载 -> 自动续签
       -> LoginInterceptor：校验登录态 -> Controller/Service 获取当前用户
       -> 退出登录：JWT 加入 Redis 黑名单，立即失效
```

### 图片上传链路

支持两种上传方式：

**普通上传：**
```text
上传文件 -> 校验登录态和等级大小限制 -> 计算 MD5 -> 检查 file_resource 去重
       -> 去重则复用 COS 文件（引用计数+1），否则上传 COS 并写入 file_resource
       -> 校验目标空间容量 -> 更新空间已用容量 -> 写入 picture
       -> 返回图片 id/url
```

**分片上传：**
```text
前端计算文件 MD5 -> POST /picture/check 秒传校验（MD5+size 匹配则直接完成）
       -> 未命中则逐片上传 POST /picture/upload-chunk（支持并发、断点续传）
       -> 全部分片上传完成后 POST /picture/merge 合并分片 -> 写入 file_resource + picture
```

### AI 调用链路

```text
用户提交 AI 请求 -> 创建 Task(PENDING) -> RocketMQ 发送异步消息
              -> TaskConsumer 消费消息 -> 分发到对应 Handler
              -> 标签识别：AiTagTaskHandler 通过 Spring AI Alibaba Agent 分析图片
              -> 文生图：AiDrawTaskHandler 通过 DashScope SDK 调用万相模型
              -> 更新 Task 状态(DONE/FAILED) -> 前端轮询查询任务结果
```

### 核心数据表

| 表名                | 说明                                             |
| ------------------- | ------------------------------------------------ |
| `user`              | 用户账号、资料、状态、等级（level 字段实现权限分级） |
| `picture`           | 图片元数据、COS URL、空间归属、审核状态、AI 标签 |
| `space`             | 私人/团队空间、容量、等级和状态                  |
| `space_team_member` | 团队空间成员关系和角色（OWNER/MEMBER）           |
| `file_resource`     | 物理文件去重（MD5 + 文件大小唯一、引用计数）     |
| `picture_share`     | 图片分享（分享令牌、过期时间、下载权限）         |
| `pic_system`        | 分类标签、跑马灯、AI 开关等系统配置              |
| `task`              | 异步任务（AI 标注、文生图等）                    |
| `sys_audit_log`     | 审计操作日志                                     |

**已删除的表：** 社区功能精简后，以下表已从数据库中移除：
- ~~post~~（帖子表）
- ~~picture_child~~（帖子图片关联表）
- ~~comment~~（评论表）
- ~~user_fans~~（关注粉丝关系表）
- ~~user_post_likes~~（点赞关系表）
- ~~user_post_collect~~（收藏关系表）

**已删除的权限表：** 从传统 RBAC 迁移为简化 RBAC 后，以下表已不存在：
- ~~role~~（角色表）
- ~~permission~~（权限表）
- ~~role_permission~~（角色权限关联表）
- ~~sys_user_role~~（用户角色关联表）

## 项目结构

### 后端包路径

```text
src/FishPics-backend/src/main/java/hk/ljx/fishpicsbackend
├── FishPicsBackendApplication.java     # Spring Boot 启动类
├── user/                               # 用户模块（controller, dto, entity, service, vo）
├── picture/                            # 图片模块（含分片上传、文件去重、分享功能）
├── space/                              # 空间模块（私人/团队空间、成员管理）
├── system/                             # 系统配置模块（分类标签、跑马灯、审计日志）
├── ai/                                 # AI 能力模块（标注、文生图、配置管理）
├── task/                               # 异步任务模块（consumer, entity, handler, message, service）
├── mapper/                             # MyBatis-Plus Mapper 接口（UserMapper、PictureMapper 等）
└── common/                             # 通用基础设施
    ├── config/                         # CORS、COS、JSON、MyBatis、Async、RocketMQ 等配置
    ├── cache/                          # 多级缓存（Caffeine L1 + Redis L2）
    ├── enums/                          # 绘图风格、图片尺寸枚举
    ├── exception/                      # 全局异常处理
    ├── interceptor/                    # Token 刷新拦截器、登录拦截器、MVC 配置
    ├── response/                       # 统一响应模型
    ├── annotation/                     # 自定义注解（@RequireAdmin、@AuditLog）
    ├── aop/                            # AOP 切面（管理员权限校验、审计日志）
    ├── context/                        # LoginContext 登录上下文（含团队权限）
    ├── constants/                      # 常量（Redis、空间、系统、用户、缓存）
    ├── dto/                            # 通用 DTO
    └── utils/                          # 工具类（JwtUtils、CosService、FileTypeUtils、PermissionUtils 等）
```

### 后端资源路径

```text
src/FishPics-backend/src/main/resources
├── application.yml                     # 通用配置
├── application-local.yml               # 本地密钥配置
└── mapper/                             # MyBatis XML 映射

src/FishPics-backend/src/sql/init.sql   # 数据库建表脚本
```

### 前端包路径

```text
src/FishPic-frontend/src
├── api/                                # Axios 请求封装
├── assets/                             # 静态资源（图片等）
├── components/                         # 通用组件
│   ├── ErrorBoundary.jsx               #   错误边界
│   ├── GlobalLayout.jsx                #   全局布局（导航栏、侧边栏）
│   ├── ProtectedRoute.jsx              #   路由守卫
│   ├── MobilePageWrapper.jsx / .css    #   移动端页面包装器
│   └── shared/                         #   共享 UI 组件
│       ├── AuthModals.jsx              #     登录/注册弹窗容器
│       ├── CategoryBar.jsx             #     分类标签栏
│       ├── CropperEditor.jsx           #     图片裁剪编辑器
│       ├── ImageEditorModal.jsx        #     图片编辑弹窗
│       ├── ImageUploadModal.jsx / .css #     图片上传弹窗（含分片上传）
│       ├── LoginModal.jsx              #     登录弹窗
│       ├── MobileBottomNav.jsx / .css  #     移动端底部导航
│       ├── SaveToSpaceModal.jsx        #     保存到空间弹窗
│       ├── SearchBar.jsx               #     搜索栏
│       ├── SpacePickerModal.jsx        #     空间选择弹窗
│       ├── UpgradeContent.jsx          #     升级内容组件
│       └── UpgradeModal.jsx            #     升级弹窗
├── context/                            # React Context（AuthContext、ThemeContext）
├── hooks/                              # 自定义 Hooks（useAuthModal、useIsMobile、useRequestUtils）
├── pages/                              # 页面组件
│   ├── 首页与个人：HomePage、UserProfile、SharePage、NotFound、Notifications
│   ├── 空间管理：PrivateSpace、TeamSpace、TeamSpaceDetail
│   │   └── PrivateSpace/               #   私人空间子目录
│   │       └── useSpacePictures.js     #     空间图片列表 Hook
│   ├── AI 工具：AIImageTools
│   ├── 后台管理：AdminDashboard、UserManagement、AdminPictureManagement、SpaceManagement、AIManagement、SystemManagement、AuditLogManagement
│   └── 移动端：MobileLoginPage、MobileRegisterPage、MobileEditPicturePage、MobileEditProfilePage、MobileUpgradePage、MobileUserProfilePage、MobileSaveToSpacePage
├── styles/                             # 全局样式（animations、carousel、shared CSS）
├── utils/                              # 工具函数（clipboard、constants、storage、upload、uploadConstraints、image、logger）
├── App.jsx                             # 路由入口
├── App.css / index.css                 # 全局样式
└── main.jsx                            # React 挂载入口
```

## 快速启动

### 后端

```bash
cd src/FishPics-backend
```

1. 创建 MySQL 数据库 `FishPics`，执行 `src/sql/init.sql`。
2. 配置 `src/main/resources/application-local.yml` 中的 MySQL、Redis、COS、JWT 密钥和 DashScope 参数。
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

- [后端开发指南（零基础版）](BACKEND.md)
- [软件需求模型](doc/software_requirements_model.md)
- [UML 与数据模型](model/uml_diagrams.md)
