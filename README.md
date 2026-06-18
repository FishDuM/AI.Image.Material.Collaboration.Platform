# FishPics — AI 图片素材协作平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6db33f?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61dafb?logo=react)](https://react.dev/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/)
[![Vite](https://img.shields.io/badge/Vite-8-646cff?logo=vite)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479a1?logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-dc382d?logo=redis)](https://redis.io/)

FishPics 是一个面向团队的图片素材管理与协作平台，集成 AI 智能标注与文生图能力。采用前后端分离架构，支持大文件分片上传、实时协同编辑、分享链接、后台审核管理等完整功能。

## 功能特性

### 图片管理

- **普通上传与分片上传**：小文件直接上传，大文件自动分片传输，支持秒传校验和断点续传
- **MD5 去重**：基于 `file_resource` 表的文件去重机制，相同文件只存储一份，通过引用计数管理生命周期
- **URL 保存**：通过图片 URL 直接保存到平台
- **批量操作**：批量删除、批量编辑、批量审核
- **图片编辑**：在线裁剪、元数据编辑（名称、标签、分类）
- **图片替换**：替换已有图片的文件内容，保留元数据
- **图片推荐**：基于 AI 推荐引擎的智能推荐（可通过功能开关控制）

### 团队协作

- **私人空间与团队空间**：私人空间供个人管理，团队空间支持多人协作
- **实时协同编辑**：基于 WebSocket 的多人在线编辑，支持缩放、旋转、裁剪操作的实时同步
- **冲突控制**：空间级单编辑锁机制，同一空间同时只允许编辑一张图片，断连自动释放
- **权限管理**：空间所有者 / 成员 / 编辑者 / 查看者四级角色，细粒度控制查看、编辑、删除、邀请、踢出、转让权限

### 分享系统

- **灵活的分享链接**：可配置有效期、下载权限、最大查看次数
- **多图分享**：支持将多张图片打包为一个分享链接
- **免登录访问**：预览和下载接口无需登录，仅允许 image/* 类型，防 XSS
- **安全哈希**：分享 Token 使用 SHA-256 哈希存储，创建时仅返回一次明文

### AI 能力

- **智能标注**：基于通义千问视觉理解模型（qwen3.5-plus），自动提取图片标签和描述
- **文生图**：基于 DashScope 万相模型（qwen-image-2.0），文本描述生成图片
- **异步任务**：提交后异步处理，通过 SSE 实时推送结果，支持失败重试（最多 3 次，指数退避 5s/10s/30s）
- **卡死任务恢复**：自动回收超过 5 分钟未完成的处理中任务
- **配额管理**：按用户等级分配月度 AI 使用配额（Redis 管理）
- **功能开关**：标注、生图、推荐三项能力均可独立开关（存储于 `pic_system` 配置表）

### 管理后台

- 用户管理（封禁 / 解封、信息编辑）
- 图片审核（审批 / 拒绝、设为精选）
- 空间管理（查看、编辑、启用 / 禁用）
- AI 任务监控与功能开关
- 审计日志（自动脱敏敏感字段）
- 系统数据统计（ECharts 可视化）
- 分类标签管理（图片分类的增删）
- 轮播图管理（首页轮播内容配置）

### 前端特性

- **响应式布局**：桌面端与移动端独立页面适配（16 个桌面页面 + 7 个移动端页面）
- **暗色模式**：支持明暗主题切换，持久化到 localStorage
- **路由懒加载**：所有页面组件通过 React.lazy + Suspense 按需加载
- **路由级错误边界**：页面异常不影响整体应用
- **国际化基础**：中文本地化（antd zh_CN + dayjs zh-cn）

## 技术栈

### 后端

| 组件 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.3.0 |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | MySQL 8 |
| 缓存 | Redis + Redisson（分布式锁、Pub/Sub 缓存失效） |
| 认证 | JWT (jjwt 0.12.6) + Redis 会话 + BCrypt 密码哈希 |
| 对象存储 | 腾讯云 COS (cos_api 5.6.227) |
| AI | Spring AI Alibaba 1.1.2.3 + DashScope SDK 2.22.18（通义千问 / 万相） |
| 异步任务 | CompletableFuture + 线程池 + MySQL CAS 抢占 + SSE 推送 |
| WebSocket | Spring WebSocket（协同编辑） |
| API 文档 | Knife4j 4.4.0 (OpenAPI 3) |
| 工具库 | Hutool 5.8.38、Jsoup 1.17.2（XSS 防御） |
| 安全 | @RequireAdmin / @RequireLogin AOP 守卫、@AuditLog 审计、限流、安全头过滤 |
| 构建 | Maven |

### 前端

| 组件 | 技术 |
|------|------|
| 框架 | React 19.2.5 |
| UI 库 | Ant Design 6.3.6 |
| 构建工具 | Vite 8.0.9 |
| 路由 | React Router DOM 7.14.2 |
| HTTP 客户端 | Axios 1.15.2 |
| 日期处理 | Day.js 1.11.20 |
| 图表 | ECharts 6.1.0 + echarts-for-react 3.0.6 |
| 图片工具 | Cropper.js 1.6.2（裁剪）、SparkMD5 3.0.2（分片 MD5） |
| 代码规范 | ESLint 9 |

## 项目结构

```
AI.Image.Material.Collaboration.Platform/
├── doc/                            # 项目文档
├── model/                          # UML 图
└── src/
    ├── FishPics-backend/           # Spring Boot 后端
    │   └── src/main/java/hk/ljx/fishpicsbackend/
    │       ├── ai/                 # AI 模块（标注、文生图、配额管理、SSE）
    │       ├── collab/             # 协同编辑模块（WebSocket、状态存储）
    │       ├── common/             # 公共基础设施（注解、AOP、缓存、配置、常量、DTO/VO/Entity、枚举、异常、拦截器、工具类）
    │       ├── mapper/             # MyBatis-Plus Mapper 接口
    │       ├── picture/            # 图片模块（上传、去重、CRUD、分享）
    │       ├── space/              # 空间模块（私人空间、团队空间、四级角色）
    │       ├── system/             # 系统模块（配置、审计日志、统计）
    │       ├── task/               # 异步任务框架（处理器、补偿器、可插拔 Handler）
    │       └── user/               # 用户模块（认证、验证码、管理）
    └── FishPic-frontend/           # React 前端
        └── src/
            ├── api/                # Axios API 客户端
            ├── components/         # 通用组件（布局、路由守卫、共享组件）
            ├── context/            # React Context（认证、主题）
            ├── hooks/              # 自定义 Hooks（AI SSE、协同 WebSocket、验证码等）
            ├── pages/              # 页面组件（16 个桌面 + 7 个移动端）
            ├── styles/             # 全局样式
            └── utils/              # 工具函数
```

## 快速开始

### 环境要求

| 依赖 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 7.0+ |
| Maven | 3.8+ |

### 后端

```bash
cd src/FishPics-backend

# 1. 创建数据库并初始化
mysql -u root -p -e "CREATE DATABASE FishPics DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p FishPics < src/sql/init.sql

# 2. 配置环境变量（见下方「环境变量」章节）

# 3. 启动
mvn spring-boot:run
```

后端启动后运行在 `http://localhost:8080`，上下文路径 `/api`。

### 前端

```bash
cd src/FishPic-frontend

# 1. 安装依赖
npm install

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 设置 VITE_COS_BASE_URL

# 3. 启动开发服务器
npm run dev
```

前端启动后运行在 `http://localhost:5173`，开发模式下 API 请求自动代理到后端。


## 环境变量

### 后端

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | `local` |
| `DB_URL` | MySQL 连接地址 | — |
| `DB_USERNAME` | MySQL 用户名 | — |
| `DB_PASSWORD` | MySQL 密码 | — |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节） | — |
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | — |
| `COS_SECRET_ID` | 腾讯云 COS SecretId | — |
| `COS_SECRET_KEY` | 腾讯云 COS SecretKey | — |
| `COS_REGION` | COS 存储桶地域 | — |
| `COS_BUCKET` | COS 存储桶名称 | — |
| `COS_URL` | COS 访问域名 | — |
| `COLLAB_WS_ALLOWED_ORIGINS` | WebSocket 允许的来源 | — |

### 前端

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `VITE_COS_BASE_URL` | COS 代理目标地址 | — |

## 数据库

共 11 张核心表，建表脚本位于 `src/FishPics-backend/src/sql/init.sql`。

| 表名 | 说明 |
|------|------|
| `user` | 用户账户（用户名、密码、头像、邮箱、手机号、昵称、等级 0-2、角色 0-1、状态） |
| `space` | 空间（私人 / 团队、容量配额、已用空间、乐观锁） |
| `picture` | 图片记录（URL、尺寸、状态、可见性、所属空间、资源 ID、描述、类型、精选标记） |
| `picture_tag` | 图片-标签关联（多对多） |
| `file_resource` | 物理文件去重表（MD5+Size 联合唯一、引用计数、乐观锁） |
| `picture_share` | 分享链接（UUID Token、SHA-256 哈希、过期时间、下载权限、查看上限） |
| `picture_share_item` | 多图分享关联 |
| `task` | 异步 AI 任务（业务类型、状态、重试逻辑） |
| `space_team_member` | 团队成员（空间 ID、用户 ID、角色：所有者/成员/编辑者/查看者） |
| `pic_system` | 系统键值配置（分类标签、轮播图、AI 功能开关） |
| `sys_audit_log` | 审计日志（操作人、操作、模块、详情、IP、自动脱敏） |

### 权限等级

| Level | 角色 | 上传限制 | 空间配额 | 团队空间数 |
|-------|------|----------|----------|------------|
| 0 | 普通用户 | 10 MB | 512 MB | 1 |
| 1 | VIP | 1 GB | 50 GB | 2 |
| 2 | SVIP | 10 GB | 100 GB | 5 |

### 用户角色

| Role | 说明 |
|------|------|
| 0 | 普通用户 |
| 1 | 管理员 |

### 团队成员角色

| RoleId | 角色 | 权限 |
|--------|------|------|
| 1 | 所有者 (OWNER) | 全部权限，可转让空间 |
| 2 | 成员 (MEMBER) | 查看、编辑 |
| 3 | 编辑者 (EDITOR) | 查看、编辑 |
| 4 | 查看者 (VIEWER) | 仅查看 |

## 架构设计

### 整体架构

```
┌─────────────┐     ┌─────────────────────────────────────────────────┐
│  React SPA  │────▶│              Spring Boot Application            │
│  (Vite)     │     │                                                 │
│             │     │  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  Ant Design │     │  │Controller│─▶│ Service  │─▶│ Mapper   │──▶ MySQL
│  Axios      │     │  └──────────┘  └──────────┘  └──────────┘      │
│  React      │     │       │              │                           │
│  Router     │     │  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│             │     │  │JWT Auth  │  │  Cache   │  │  COS     │      │
└─────────────┘     │  │Interceptor│  │(Redis)  │  │  Storage │      │
                    │  └──────────┘  └──────────┘  └──────────┘      │
                    │       │              │                           │
                    │  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
                    │  │WebSocket │  │  Task    │  │  AI      │      │
                    │  │Collab    │  │ Processor│  │ Provider │      │
                    │  └──────────┘  └──────────┘  └──────────┘      │
                    └─────────────────────────────────────────────────┘
```

### 分层架构

- **Controller**：接收请求，参数校验，调用 Service
- **Component**：复杂业务逻辑的独立管理器（如 PictureUploadManager、SpaceQuotaManager）
- **Service**：业务编排，事务管理
- **Mapper**：数据访问，MyBatis-Plus 自动生成 + 自定义 XML
- **DTO / VO**：DTO 用于入参，VO 用于出参，Entity 映射数据库

### 缓存策略

- **Redis TTL 缓存**：三级缓存键空间，分别管理用户信息（60 分钟）、用户权限（60 分钟）、系统配置（1440 分钟）
- **读路径**：Redis 未命中 → 数据库 → 回填 Redis
- **写路径**：更新数据库 → 清除 Redis 缓存
- **分布式锁**：Redisson 实现分布式锁，保障并发安全
- **Pub/Sub 失效**：通过 Redisson 广播实现跨节点缓存失效

### 认证流程

1. 用户提交用户名 / 密码 / 验证码（图形验证码防刷）
2. 服务端校验后签发 JWT（30 分钟有效期）
3. 后续请求经过 `TokenRefreshInterceptor` 解析校验，超过 15 分钟自动续签（响应头 `X-New-Token`）
4. 登出时 JWT 加入 Redis 黑名单
5. 封禁用户通过 Redis 集合即时失效所有 Token
6. 支持 Token 批量失效（`USER_TOKEN_INVALID_BEFORE` 时间戳机制）

### 异步任务流程

1. 用户提交任务 → 创建 Task（PENDING）→ 返回 taskId
2. 任务分发到线程池
3. Worker 通过条件 UPDATE 原子抢占（CAS）→ 状态变为 PROCESSING
4. 调用 AI 服务 → 更新结果 → 状态变为 DONE
5. 通过 SSE 推送结果到前端
6. 失败任务自动重试（最多 3 次，退避 5s / 10s / 30s）
7. 卡死任务自动回收（PROCESSING 超过 5 分钟）
8. `TaskDispatchCompensator` 补偿分发失败的任务

### 协同编辑流程

1. 用户通过 WebSocket 连接 `/ws/collab?token=...&spaceId=...`（JWT 认证）
2. 加入空间后，通过 Redis 存储编辑状态（锁 TTL 30 分钟，状态 TTL 2 小时）
3. 同一空间同时只允许锁定一张图片进行编辑（空间级单编辑锁）
4. 编辑操作（transform）通过 WebSocket 广播给同空间其他用户
5. 解锁、替换文件、重同步等事件通过消息协议通知
6. 断连自动释放锁，Lua 脚本保证原子 CAS 解锁

### 代理配置

开发模式下 Vite 自动代理请求：

- `/api/*` → `http://localhost:8080`（含 WebSocket 支持）
- `/cos-proxy/*` → COS 存储地址（由 `.env` 中 `VITE_COS_BASE_URL` 配置，去掉 `/cos-proxy` 前缀）

## 相关文档

- [软件需求与架构设计](doc/software_requirements_model.md) — 技术选型、分层设计、权限模型、关键流程
- [UML 图](model/uml_diagrams.md) — 用例图、领域类图、ER 图、时序图（Mermaid 格式）
