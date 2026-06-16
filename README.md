# FishPics — AI 图片素材协作平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6db33f?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61dafb?logo=react)](https://react.dev/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/)
[![Vite](https://img.shields.io/badge/Vite-8-646cff?logo=vite)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479a1?logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-dc382d?logo=redis)](https://redis.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

FishPics 是一个面向团队的图片素材管理与协作平台，集成 AI 智能标注与文生图能力。采用前后端分离架构，支持大文件分片上传、实时协同编辑、分享链接、后台审核管理等完整功能。

## 功能特性

### 图片管理

- **普通上传与分片上传**：小文件直接上传，大文件自动分片传输，支持秒传校验和断点续传
- **MD5 去重**：基于 `file_resource` 表的文件去重机制，相同文件只存储一份，通过引用计数管理生命周期
- **URL 保存**：通过图片 URL 直接保存到平台
- **批量操作**：批量删除、批量编辑、批量审核
- **图片编辑**：在线裁剪、元数据编辑（名称、标签、分类）

### 团队协作

- **私人空间与团队空间**：私人空间供个人管理，团队空间支持多人协作
- **实时协同编辑**：基于 WebSocket 的多人在线编辑，支持缩放、旋转、裁剪操作的实时同步
- **冲突控制**：图片级锁机制，防止多人同时编辑同一张图片，断连自动释放
- **权限管理**：空间所有者 / 成员两级角色

### 分享系统

- **灵活的分享链接**：可配置有效期、下载权限、最大查看次数
- **多图分享**：支持将多张图片打包为一个分享链接
- **免登录访问**：预览和下载接口无需登录，仅允许 image/* 类型，防 XSS

### AI 能力

- **智能标注**：基于通义千问视觉理解模型，自动提取图片标签和描述
- **文生图**：基于 DashScope 万相模型，文本描述生成图片
- **异步任务**：提交后异步处理，通过 SSE 实时推送结果，支持失败重试（最多 3 次，指数退避）
- **卡死任务恢复**：自动回收超过 5 分钟未完成的处理中任务

### 管理后台

- 用户管理（封禁 / 解封、信息编辑）
- 图片审核（审批 / 拒绝、设为精选）
- 空间管理（查看、编辑、启用 / 禁用）
- AI 任务监控与功能开关
- 审计日志（自动脱敏敏感字段）
- 系统数据统计（ECharts 可视化）

### 前端特性

- **响应式布局**：桌面端与移动端独立页面适配
- **暗色模式**：支持明暗主题切换，持久化到 localStorage
- **路由懒加载**：所有页面组件通过 React.lazy + Suspense 按需加载
- **路由级错误边界**：页面异常不影响整体应用

## 技术栈

### 后端

| 组件 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.3.0 |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | MySQL 8 |
| 缓存 | Redis + Caffeine 多级缓存，Redisson 分布式锁与 Pub/Sub 缓存失效 |
| 认证 | JWT (jjwt 0.12.6) + Redis 会话 + BCrypt 密码哈希 |
| 对象存储 | 腾讯云 COS (cos_api 5.6.227) |
| AI | Spring AI Alibaba 1.1.2.3 + DashScope SDK 2.22.18（通义千问 / 万相） |
| 异步任务 | CompletableFuture + 线程池 + MySQL CAS 抢占 + SSE 推送 |
| WebSocket | Spring WebSocket（协同编辑） |
| API 文档 | Knife4j 4.4.0 (OpenAPI 3) |
| 安全 | Jsoup XSS 防御、@RequireAdmin / @RequireLogin AOP 守卫、@AuditLog 审计、限流、安全头过滤 |
| 构建 | Maven |

### 前端

| 组件 | 技术 |
|------|------|
| 框架 | React 19.2.5 |
| UI 库 | Ant Design 6.3.6 |
| 构建工具 | Vite 8.0.9 |
| 路由 | React Router DOM 7.14.2 |
| HTTP 客户端 | Axios 1.15.2 |
| 图表 | ECharts 6.1.0 |
| 图片工具 | Cropper.js 1.6.2（裁剪）、SparkMD5 3.0.2（分片 MD5） |
| 代码规范 | ESLint 9 |

## 项目结构

```
AI.Image.Material.Collaboration.Platform/
├── README.md                          # 项目说明
├── LICENSE                            # MIT 许可证
├── doc/
│   └── software_requirements_model.md # 软件需求与架构设计文档
├── model/
│   └── uml_diagrams.md                # UML 图（用例、类图、ER 图、时序图）
└── src/
    ├── FishPics-backend/              # Spring Boot 后端
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/hk/ljx/fishpicsbackend/
    │       │   ├── ai/                # AI 模块（标注、文生图、配置、SSE）
    │       │   ├── collab/            # 协同编辑模块（WebSocket）
    │       │   ├── common/            # 公共基础设施（注解、AOP、缓存、配置、
    │       │   │                      #   常量、DTO、实体、枚举、异常、拦截器、工具类）
    │       │   ├── mapper/            # MyBatis-Plus Mapper 接口
    │       │   ├── picture/           # 图片模块（上传、分享、CRUD）
    │       │   ├── space/             # 空间模块（私人空间、团队空间）
    │       │   ├── system/            # 系统模块（配置、审计日志、统计）
    │       │   ├── task/              # 异步任务框架（处理器、补偿器）
    │       │   └── user/              # 用户模块（认证、注册、管理）
    │       ├── main/resources/
    │       │   ├── application.yml
    │       │   └── mapper/*.xml       # MyBatis XML 映射文件
    │       └── sql/
    │           └── init.sql           # 建表脚本 + 种子数据
    └── FishPic-frontend/              # React 前端
        ├── package.json
        ├── vite.config.js
        └── src/
            ├── api/                   # Axios API 客户端
            ├── components/            # 通用组件（布局、路由守卫、弹窗、画布）
            ├── context/               # React Context（认证、主题）
            ├── hooks/                 # 自定义 Hooks（AI SSE、协同 WebSocket 等）
            ├── pages/                 # 页面组件（27 个页面）
            ├── styles/                # 全局样式
            └── utils/                 # 工具函数
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

### 生产构建

```bash
# 后端
cd src/FishPics-backend
mvn clean package -DskipTests
java -jar target/FishPics-backend-*.jar

# 前端
cd src/FishPic-frontend
npm run build
# 产物在 dist/ 目录
```

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
| `user` | 用户账户（用户名、密码、头像、等级 0-3、状态） |
| `space` | 空间（私人 / 团队、容量配额、已用空间、乐观锁） |
| `picture` | 图片记录（URL、尺寸、状态、可见性、所属空间） |
| `picture_tag` | 图片-标签关联（多对多） |
| `file_resource` | 物理文件去重表（MD5+Size 联合唯一、引用计数、乐观锁） |
| `picture_share` | 分享链接（UUID Token、SHA-256 哈希、过期时间、下载权限、查看上限） |
| `picture_share_item` | 多图分享关联 |
| `task` | 异步 AI 任务（业务类型、状态、重试逻辑） |
| `space_team_member` | 团队空间成员（空间 ID、用户 ID、角色） |
| `pic_system` | 系统键值配置（分类标签、轮播图） |
| `sys_audit_log` | 审计日志（操作人、操作、模块、详情、IP、自动脱敏） |

### 权限等级

| Level | 角色 | 上传限制 | 空间配额 | 团队空间数 |
|-------|------|----------|----------|------------|
| 0 | 普通用户 | 10 MB | 512 MB | 1 |
| 1 | VIP | 1 GB | 50 GB | 2 |
| 2 | SVIP | 10 GB | 100 GB | 5 |
| 3 | 管理员 | — | — | — |

## API 文档

所有 REST 接口前缀为 `/api`，详细参数说明请启动后访问 Knife4j 文档：

```
http://localhost:8080/api/doc.html
```

### 接口模块

| 模块 | 前缀 | 说明 |
|------|------|------|
| 用户 | `/api/user/*` | 注册、登录、资料、搜索、管理端用户管理 |
| 图片 | `/api/picture/*` | 上传（普通 / 分片）、列表、编辑、删除、管理端审核 |
| 分享 | `/api/share/*` | 创建 / 查看 / 预览 / 下载 / 取消分享 |
| 空间 | `/api/space/*` | 空间 CRUD、图片列表、团队成员管理、管理端空间管理 |
| AI | `/api/ai/*` | 标注 / 文生图任务提交与结果查询、管理端 AI 配置 |
| 系统 | `/api/system/*` | 分类标签、轮播图、审计日志、统计 |
| WebSocket | `/ws/collab` | 实时协同编辑（transform / lock / unlock / resync） |

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
└─────────────┘     │  │Interceptor│  │(L1+L2)  │  │  Storage │      │
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
- **Service**：业务逻辑，事务管理
- **Mapper**：数据访问，MyBatis-Plus 自动生成 + 自定义 XML
- **DTO / VO**：DTO 用于入参，VO 用于出参，Entity 映射数据库

### 缓存策略

- **L1 缓存**：Caffeine 本地缓存，热点数据零网络开销
- **L2 缓存**：Redis 远程缓存，跨节点共享
- **读路径**：L1 未命中 → L2 → 数据库
- **写路径**：更新 Redis → 广播失效本地缓存（Redisson Pub/Sub）

### 认证流程

1. 用户提交用户名 / 密码 / 验证码
2. 服务端校验后签发 JWT（30 分钟有效期）
3. 后续请求经过 `TokenRefreshInterceptor` 解析校验，超过 15 分钟自动续签
4. 登出时 JWT 加入 Redis 黑名单
5. 封禁用户通过 Redis 集合 `banned:users` 即时失效所有 Token

### 异步任务流程

1. 用户提交任务 → 创建 Task（PENDING）→ 返回 taskId
2. 任务分发到线程池
3. Worker 通过条件 UPDATE 原子抢占（CAS）→ 状态变为 PROCESSING
4. 调用 AI 服务 → 更新结果 → 状态变为 DONE
5. 通过 SSE 推送结果到前端
6. 失败任务自动重试（最多 3 次，退避 5s / 10s / 30s）

## 开发指南

### 后端开发

```bash
cd src/FishPics-backend

# 运行测试
mvn test

# 打包
mvn clean package

# 代码规范
# - Controller → Service → Mapper 分层
# - DTO 接参，VO 出参，Entity 映射表
# - 管理端接口使用 @RequireAdmin 注解
# - 需要登录的接口使用 @RequireLogin 注解
# - 需要审计的接口使用 @AuditLog 注解
```

### 前端开发

```bash
cd src/FishPic-frontend

# 开发
npm run dev

# 代码检查
npm run lint

# 生产构建
npm run build

# 预览生产构建
npm run preview
```

### 代理配置

开发模式下 Vite 自动代理请求：

- `/api/*` → `http://localhost:8080`（含 WebSocket 支持）
- `/cos-proxy/*` → COS 存储地址（由 `.env` 中 `VITE_COS_BASE_URL` 配置）

## 相关文档

- [软件需求与架构设计](doc/software_requirements_model.md) — 技术选型、分层设计、权限模型、关键流程
- [UML 图](model/uml_diagrams.md) — 用例图、领域类图、ER 图、时序图（Mermaid 格式）
