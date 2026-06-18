# 软件需求与架构设计文档

> FishPics — AI 图片素材协作平台

## 目录

- [1. 技术选型](#1-技术选型)
- [2. 系统架构](#2-系统架构)
- [3. 分层设计](#3-分层设计)
- [4. 认证与授权](#4-认证与授权)
- [5. 缓存设计](#5-缓存设计)
- [6. 状态码定义](#6-状态码定义)
- [7. 功能模块设计](#7-功能模块设计)
- [8. 数据库设计](#8-数据库设计)
- [9. 关键流程](#9-关键流程)
- [10. 安全设计](#10-安全设计)

## 1. 技术选型

### 后端

| 组件 | 选型 | 版本 | 选型理由 |
|------|------|------|----------|
| 语言 | Java | 21 | LTS 版本，虚拟线程支持 |
| 框架 | Spring Boot | 3.3.0 | 成熟的企业级框架，生态完善 |
| ORM | MyBatis-Plus | 3.5.14 | 单表零 SQL，复杂查询可自定义 XML |
| 数据库 | MySQL | 8.0 | 社区活跃，JSON 支持，窗口函数 |
| 远程缓存 | Redis + Redisson | 3.27.0 | 分布式锁、Pub/Sub 缓存失效 |
| 认证 | JWT (jjwt) | 0.12.6 | 无状态认证，水平扩展友好 |
| 密码哈希 | BCrypt (spring-security-crypto) | 6.3.0 | 自适应哈希，抗彩虹表 |
| 对象存储 | 腾讯云 COS | 5.6.227 | 海量图片存储，CDN 加速 |
| AI 框架 | Spring AI Alibaba | 1.1.2.3 | 统一 AI 接口，支持多模型切换 |
| AI SDK | DashScope SDK | 2.22.18 | 阿里云官方 SDK，直连通义千问 / 万相 |
| WebSocket | Spring WebSocket | — | 原生集成，协同编辑支持 |
| API 文档 | Knife4j | 4.4.0 | OpenAPI 3 + Jakarta，中文友好 |
| XSS 防御 | Jsoup | 1.17.2 | HTML 白名单过滤 |
| 工具库 | Hutool | 5.8.38 | 常用工具集，减少重复代码 |

### 前端

| 组件 | 选型 | 版本 | 选型理由 |
|------|------|------|----------|
| 框架 | React | 19.2.5 | Hooks 原生支持，Server Components 前景 |
| UI 库 | Ant Design | 6.3.6 | 企业级组件库，国际化完善 |
| 构建工具 | Vite | 8.0.9 | 极速 HMR，ESBuild 预构建 |
| 路由 | React Router DOM | 7.14.2 | 声明式路由，懒加载支持 |
| HTTP 客户端 | Axios | 1.15.2 | 拦截器机制，请求 / 响应统一处理 |
| 日期处理 | Day.js | 1.11.20 | 轻量级日期库，antd 日期组件依赖 |
| 图表 | ECharts + echarts-for-react | 6.1.0 / 3.0.6 | 功能丰富的可视化库，React 封装 |
| 图片裁剪 | Cropper.js | 1.6.2 | 轻量级图片裁剪 |
| MD5 计算 | SparkMD5 | 3.0.2 | 浏览器端增量 MD5，支持大文件 |
| 代码规范 | ESLint | 9 | 代码风格统一 |

## 2. 系统架构

### 整体架构

系统采用前后端分离的单体架构，后端按业务领域划分模块，前端为单页应用（SPA）。

```
┌──────────────────────────────────────────────────────────────┐
│                        客户端（浏览器）                         │
│   ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐   │
│   │  桌面端页面   │  │  移动端页面   │  │  管理后台页面      │   │
│   │  (16个)      │  │  (7个)       │  │  (7个)            │   │
│   └──────┬──────┘  └──────┬───────┘  └────────┬──────────┘   │
│          └───────────────┼────────────────────┘               │
│                     React SPA (Vite)                          │
└─────────────────────────┬────────────────────────────────────┘
                          │ HTTP / WebSocket
┌─────────────────────────┼────────────────────────────────────┐
│                   Spring Boot Application                     │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │  拦截器链：SecurityHeaderFilter → TokenRefresh →     │     │
│  │           Controller → @RequireLogin/@RequireAdmin   │     │
│  │           (AOP 切面校验)                              │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ user   │ │picture │ │ space  │ │  ai    │ │ system │    │
│  │(5组件) │ │(11组件)│ │(4组件) │ │(2处理器)│ │        │    │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘    │
│      └──────────┴──────────┴─────┬────┴──────────┘          │
│                              common                          │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ cache  │ │ AOP    │ │ infra  │ │ task   │ │ collab │    │
│  │(Redis) │ │(3切面) │ │(5组件) │ │(框架)  │ │(WebSocket)│ │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘    │
│      └──────────┴──────────┴──────────┴──────────┘          │
└──────┬──────────┬──────────┬──────────┬──────────────────────┘
       │          │          │          │
  ┌────┴───┐ ┌───┴────┐ ┌───┴───┐ ┌───┴────┐
  │ MySQL  │ │ Redis  │ │  COS  │ │DashScope│
  │(11表)  │ │(缓存/  │ │(文件) │ │(AI模型) │
  │        │ │ 锁/会话)│ │       │ │         │
  └────────┘ └────────┘ └───────┘ └────────┘
```

### 模块职责

| 模块 | 职责 | 核心组件数 |
|------|------|-----------|
| `user` | 用户注册、登录、验证码、资料管理、缓存管理、管理端用户管理 | 5 个 Component |
| `picture` | 图片上传（普通 / 分片）、CRUD、替换、去重、URL 保存、管理端审核、分享 | 11 个 Component |
| `space` | 私人空间、团队空间、成员管理（四级角色）、权限检查、管理端空间管理 | 4 个 Component |
| `ai` | AI 标注（qwen3.5-plus）、文生图（qwen-image-2.0）、SSE 推送、配额管理 | 2 个 Handler |
| `system` | 分类标签、轮播图、审计日志、系统统计 | — |
| `task` | 异步任务框架（分发、CAS 抢占、补偿、重试、卡死恢复） | 2 个 Component |
| `collab` | WebSocket 协同编辑（空间级单编辑锁、操作广播、断连恢复、Redis 状态存储） | 5 个类 |
| `common` | 注解（3 个）、AOP 切面（3 个）、缓存（RedisCacheManager）、配置（6 个）、基础设施（5 个）、工具类（8 个） | — |

## 3. 分层设计

### 请求处理流程

```
HTTP Request
    │
    ▼
SecurityHeaderFilter          ← 安全头注入（X-Content-Type-Options, X-Frame-Options 等）
    │
    ▼
TokenRefreshInterceptor       ← JWT 解析 + 15 分钟自动续签（X-New-Token 响应头）
    │
    ▼
MvcConfig                     ← 注册拦截器，排除公开路径
    │
    ▼
Controller                    ← 参数校验，调用 Service
    │
    ├── @RequireLogin         ← LoginCheckAspect AOP 切面校验登录态
    ├── @RequireAdmin         ← AdminCheckAspect AOP 切面校验管理员
    └── @AuditLog             ← AuditLogAspect AOP 切面记录审计日志
    │
    ▼
Component (Manager)           ← 复杂业务逻辑独立管理器
    │
    ▼
Service                       ← 业务编排，事务管理
    │
    ▼
Mapper (MyBatis-Plus)         ← 数据访问
    │
    ▼
MySQL / Redis / COS
```

### 对象分层

| 类型 | 用途 | 命名规范 |
|------|------|----------|
| DTO | 入参对象（Controller 接收） | `XxxDTO` 或 `XxxRequest` |
| VO | 出参对象（Controller 返回） | `XxxVO` |
| Entity | 数据库实体（Mapper 映射） | `Xxx` |

### AOP 注解

| 注解 | 作用 | 切面 | 参数 |
|------|------|------|------|
| `@RequireLogin` | 要求登录 | `LoginCheckAspect` | — |
| `@RequireAdmin` | 要求管理员（role=1） | `AdminCheckAspect` | `errorMes` 自定义错误信息 |
| `@AuditLog` | 自动审计日志 | `AuditLogAspect` | `module`, `operation`, `description` |

## 4. 认证与授权

### 认证流程

```
1. 客户端请求验证码        GET /user/checkCode/login
   ────────────────────────────────────────────────▶
                          生成验证码 → 存 Redis(captchaKey → code)
                          → 返回 captchaKey + base64 图片
   ◀────────────────────────────────────────────────

2. 客户端提交登录          POST /user/login {username, password, captchaCode, captchaKey}
   ────────────────────────────────────────────────▶
                          CaptchaManager 校验验证码
                          → 校验密码(BCrypt)
                          → 签发 JWT(30 分钟)
                          → UserCacheManager 存 LoginContext 到 Redis
                          → 返回 UserVO + JWT
   ◀────────────────────────────────────────────────

3. 后续请求                Authorization: Bearer <JWT>
   ────────────────────────────────────────────────▶
                          TokenRefreshInterceptor 解析
                          → 超过 15 分钟自动续签(响应头 X-New-Token)
                          → AOP 切面校验登录态/管理员权限
                          → LoginContext 加载用户权限上下文
```

### Token 生命周期

- **签发**：JWT 有效期 30 分钟
- **续签**：超过 15 分钟的请求自动签发新 Token（响应头 `X-New-Token`）
- **登出**：JWT 加入 Redis 黑名单
- **封禁**：用户 ID 加入 Redis 集合 `BANNED_USERS`，所有 Token 即时失效
- **批量失效**：`USER_TOKEN_INVALID_BEFORE` 时间戳机制，之前签发的 Token 全部失效

### 权限模型

采用等级 + 角色双维度权限模型：

**Level（等级）— 控制资源配额**：

| Level | 角色 | 上传限制 | 空间配额 | 团队空间数 |
|-------|------|----------|----------|------------|
| 0 | 普通用户 | 10 MB | 512 MB | 1 |
| 1 | VIP | 1 GB | 50 GB | 2 |
| 2 | SVIP | 10 GB | 100 GB | 5 |

**Role（系统角色）— 控制功能权限**：

| Role | 说明 | 校验方式 |
|------|------|----------|
| 0 | 普通用户 | `@RequireLogin` |
| 1 | 管理员 | `@RequireAdmin`（AOP 切面校验 role=1） |

**团队成员角色 — 控制空间内操作权限**：

| RoleId | 角色 | 权限范围 |
|--------|------|----------|
| 1 | 所有者 (OWNER) | 全部权限：查看 / 编辑 / 删除 / 邀请 / 踢出 / 转让空间 |
| 2 | 成员 (MEMBER) | 查看 / 编辑 |
| 3 | 编辑者 (EDITOR) | 查看 / 编辑 |
| 4 | 查看者 (VIEWER) | 仅查看 |

`LoginContext` 统一管理三层权限：系统级权限、VIP 权限、团队级权限。

## 5. 缓存设计

### 缓存架构

```
请求 ──▶ Redis TTL 缓存 ──命中──▶ 返回
              │ 未命中
              ▼
         MySQL ──▶ 回填 Redis → 返回
```

### 缓存策略

RedisCacheManager 管理三个缓存键空间：

| 缓存 | TTL | 存储内容 |
|------|-----|----------|
| `userInfoCache` | 60 分钟 | 用户登录信息、用户资料 |
| `userPermCache` | 60 分钟 | 用户权限上下文 |
| `sysConfigCache` | 1440 分钟（24 小时） | 系统配置（分类标签、轮播图、AI 功能开关） |

| 操作 | 策略 |
|------|------|
| 读 | Redis 命中 → 返回；未命中 → 查 DB → 回填 Redis |
| 写 | 更新 DB → 清除 Redis 缓存 |
| 分布式锁 | Redisson 实现，保障并发写安全 |

### 缓存对象

- 用户登录信息（LoginContext）
- 用户权限信息（userPermCache）
- 系统配置（分类标签、轮播图、AI 功能开关）
- 验证码（CaptchaManager 管理）

## 6. 状态码定义

### 用户状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `user.status` | `1` | 正常 |
| `user.status` | `0` | 禁用 |

### 用户角色

| 字段 | 值 | 含义 |
|------|-----|------|
| `user.role` | `0` | 普通用户 |
| `user.role` | `1` | 管理员 |

### 图片状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `picture.status` | `0` | 正常 |
| `picture.status` | `1` | 禁用 |
| `picture.status` | `2` | 待审核 |
| `picture.is_private` | `0` | 公开 |
| `picture.is_private` | `1` | 私有 |

### 空间状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `space.type` | `0` | 私人空间 |
| `space.type` | `1` | 团队空间 |
| `space_team_member.role_id` | `1` | 所有者 (OWNER) |
| `space_team_member.role_id` | `2` | 成员 (MEMBER) |
| `space_team_member.role_id` | `3` | 编辑者 (EDITOR) |
| `space_team_member.role_id` | `4` | 查看者 (VIEWER) |

### AI 任务状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `task.status` | `PENDING` | 等待处理 |
| `task.status` | `PROCESSING` | 处理中 |
| `task.status` | `DONE` | 完成 |
| `task.status` | `FAILED` | 失败 |
| `task.biz_type` | `ai_tag` | AI 标注 |
| `task.biz_type` | `ai_draw` | AI 文生图 |

## 7. 功能模块设计

### 7.1 用户模块

**功能清单**：
- 注册（用户名 + 密码 + 验证码）
- 登录（用户名 + 密码 + 验证码）
- 图形验证码（登录 / 注册分别生成）
- 退出登录
- 查看 / 编辑个人资料（昵称、头像、邮箱、手机号、密码）
- 搜索用户
- 管理端：用户列表（多条件筛选）、封禁 / 解封、编辑用户信息

**设计要点**：
- 密码使用 BCrypt 哈希存储
- 修改密码后重新签发 JWT，旧 Token 即时失效
- 头像上传走图片模块，限制 5 MB
- 验证码由 CaptchaManager 管理，存储在 Redis

### 7.2 图片模块

**功能清单**：
- 普通上传（单文件）
- 分片上传（大文件：秒传校验 → 分片上传 → 合并）
- URL 保存图片
- 图片列表（分页、搜索、排序）
- 图片编辑（名称、标签、分类、描述、可见性）
- 图片裁剪（在线 Canvas 编辑）
- 图片替换（协同编辑中替换文件内容）
- 图片删除（单张 / 批量）
- 图片推荐（AI 推荐引擎，功能开关控制）
- 管理端：审核队列（审批 / 拒绝）、设为精选

**设计要点**：
- 上传前计算 MD5，查询 `file_resource` 表去重，命中则 refCount++，不实际上传
- 分片上传支持断点续传：`check` 接口返回已上传分片列表
- 上传限制按用户等级：普通 10 MB / VIP 1 GB / SVIP 10 GB
- 图片存储到腾讯云 COS，元数据存储到 MySQL
- 分片上传由 5 个组件协作：MultipartUploadCoordinator（协调）、Manager（生命周期）、SessionStore（Redis 进度）、Cleaner（过期清理）、Support（通用工具）
- 图片删除由 PictureDeleteManager 管理：级联处理引用计数、COS 文件清理、空间容量回收
- 图片替换由 PictureReplaceManager 管理：旧文件引用递减 + 新文件上传

### 7.3 分享模块

**功能清单**：
- 创建分享链接（可配置有效期、下载权限、最大查看次数）
- 多图分享（支持将多张图片打包为一个分享链接）
- 查看分享信息
- 预览分享图片（免登录）
- 下载分享图片（免登录）
- 取消分享

**设计要点**：
- 分享链接使用 UUID Token 标识，数据库存储 SHA-256 哈希（`share_token_hash`）
- 创建时仅返回一次明文 Token，后续无法再获取
- 预览和下载接口仅允许 `image/*` Content-Type，防 XSS
- 支持过期自动失效和查看次数限制
- 下载权限可独立控制（`allow_download`）

### 7.4 空间模块

**功能清单**：
- 创建空间（私人 / 团队）
- 空间列表（按类型筛选）
- 空间内图片管理
- 团队成员管理（邀请、移除、角色变更）
- 可保存空间列表（SaveToSpace）
- 管理端：空间列表、编辑、删除、启用 / 禁用

**设计要点**：
- 每个用户有且仅有一个私人空间（注册时自动创建，`uk_user_type` 唯一约束）
- 团队空间数量上限按等级：普通 1 / VIP 2 / SVIP 5
- 空间容量按等级分配：普通 512 MB / VIP 50 GB / SVIP 100 GB
- 使用乐观锁（`space.version`）防止并发写入时容量计算错误
- 团队成员四级角色：OWNER / MEMBER / EDITOR / VIEWER
- SpacePermissionChecker 校验用户空间访问权限
- SpaceQuotaManager 管理空间容量配额

### 7.5 AI 模块

**功能清单**：
- AI 标注：上传图片 → 自动提取标签和描述（qwen3.5-plus 模型）
- AI 文生图：输入文本描述 → 生成图片（qwen-image-2.0 模型）
- SSE 实时推送任务结果
- 下载 AI 生成的图片
- 月度配额管理（按用户等级分配，Redis 计数）
- 管理端：任务列表、统计、功能开关（标注开关、生图开关、推荐开关）

**设计要点**：

**标注流程**：
1. 用户提交图片 ID → AiQuotaManager 检查配额 → 创建 Task (PENDING)
2. 异步分发到线程池
3. Worker 通过 CAS 抢占（条件 UPDATE）→ PROCESSING
4. AiTagTaskHandler 调用 Spring AI Alibaba + 通义千问（qwen3.5-plus）
5. 提取标签和描述 → 写入 Task.result → DONE
6. SSE 推送结果到前端（AiSseEmitterRegistry）

**文生图流程**：
1. 用户提交文本描述 → AiQuotaManager 检查配额 → 创建 Task (PENDING)
2. 异步分发到线程池
3. Worker 通过 CAS 抢占 → PROCESSING
4. AiDrawTaskHandler 调用 DashScope SDK + 万相模型（qwen-image-2.0）
5. 生成图片 URL → 下载上传到 COS → 创建 picture 记录 → DONE
6. SSE 推送结果到前端

**容错机制**：
- 失败重试：最多 3 次，退避间隔 5s / 10s / 30s
- 卡死恢复：自动回收 PROCESSING 超过 5 分钟的任务
- 任务补偿：TaskDispatchCompensator 处理分发失败的任务
- CAS 抢占：`UPDATE task SET status='PROCESSING' WHERE id=? AND status='PENDING'`，避免重复消费
- 可插拔 Handler：TaskHandler 接口，当前实现 ai_tag（AiTagTaskHandler）和 ai_draw（AiDrawTaskHandler）

### 7.6 协同编辑模块

**功能清单**：
- 多人实时在线编辑同一团队空间的图片
- 图片操作广播（缩放、旋转、裁剪）
- 空间级单编辑锁管理
- 编辑冲突通知（lock-denied）
- 断连自动释放
- 重连后状态同步（resync）
- 在线用户感知（presence / join / leave）

**设计要点**：

**连接流程**：
1. 客户端通过 `ws://host/ws/collab?token=<JWT>&spaceId=<id>` 建立连接
2. CollabWebSocketHandler 验证 JWT 和空间权限（SpacePermissionChecker）
3. 注册会话到 CollabSessionRegistry
4. CollabStateStore 从 Redis 加载当前锁状态和图片变换信息（resync）
5. 广播 join 消息给同空间其他用户

**消息协议**：

| 类型 | 方向 | 说明 |
|------|------|------|
| `join` | 客户端 → 服务端 | 加入空间编辑会话 |
| `leave` | 客户端 → 服务端 | 离开空间编辑会话 |
| `presence` | 服务端 → 客户端 | 当前在线用户列表 |
| `lock` | 客户端 → 服务端 → 全体 | 锁定某张图片（空间级单编辑锁） |
| `unlock` | 客户端 → 服务端 → 全体 | 释放图片锁 |
| `lock-denied` | 服务端 → 客户端 | 锁定被拒绝（已有其他图片被锁定） |
| `transform` | 客户端 → 服务端 → 全体 | 图片变换操作（scale, rotation, crop） |
| `file-replaced` | 客户端 → 服务端 → 全体 | 图片文件已替换 |
| `resync` | 服务端 → 客户端 | 重连后同步当前状态 |

**锁机制**：
- 空间级单编辑锁：同一空间同时只允许锁定一张图片进行编辑
- Redis 分布式锁（TTL 30 分钟），Lua 脚本保证原子 CAS 解锁
- 编辑状态存储在 Redis（TTL 2 小时）
- 断连自动释放锁

### 7.7 系统配置模块

**功能清单**：
- 分类标签管理（CRUD，Redis 缓存）
- 轮播图管理（增删查）
- 审计日志查询（分页，支持多条件筛选）
- 系统统计数据（ECharts 可视化）

**设计要点**：
- 配置数据存储在 `pic_system` 表（键值对模式：`syskey` + `sysvalue`）
- 热点数据走 Redis 缓存（sysConfigCache，24 小时 TTL），变更时主动失效
- 审计日志通过 `@AuditLog` AOP 自动记录，由 AuditLogWriter 异步写入
- 敏感字段（password、token、apiKey）自动脱敏
- 审计日志包含：操作人、操作、模块、详情、HTTP 方法、URL、参数、结果、异常、IP

## 8. 数据库设计

### ER 关系概览

```
USER ──1:N──▶ SPACE ──1:N──▶ PICTURE ──1:0..1──▶ FILE_RESOURCE
  │                            │
  ├──1:N──▶ PICTURE            ├──1:N──▶ PICTURE_SHARE ──1:N──▶ PICTURE_SHARE_ITEM
  │                            │
  ├──1:N──▶ TASK               └──M:N──▶ PICTURE_TAG
  │
  ├──M:N──▶ SPACE_TEAM_MEMBER (via SPACE)
  │
  └──1:N──▶ SYS_AUDIT_LOG
```

### 关键约束

| 表 | 约束 | 说明 |
|-----|------|------|
| `user` | `username` UNIQUE | 用户名唯一 |
| `file_resource` | `(md5, size)` UNIQUE | 文件去重联合唯一 |
| `file_resource` | `ref_count >= 0` CHECK | 引用计数非负 |
| `space` | `(user_id, type)` UNIQUE | 每用户每类型空间唯一 |
| `space_team_member` | `(space_id, user_id)` UNIQUE | 空间内用户唯一 |
| `picture` | `(resource_id, user_id, space_id)` UNIQUE | 同一空间内同一文件唯一 |
| `task` | `task_id` UNIQUE | 任务 ID 唯一 |
| `picture_share` | `share_token` UNIQUE | 分享 Token 唯一 |

### 核心表结构

详细建表脚本：`src/FishPics-backend/src/sql/init.sql`

完整的表结构和字段定义见 [UML 图 — ER 图](uml_diagrams.md#3-er-图)。

## 9. 关键流程

### 9.1 登录流程

```
客户端                           服务端
  │                               │
  ├── GET /user/checkCode/login ──▶ CaptchaManager 生成验证码 → 存 Redis
  │◀── captchaKey + base64 ───────┤
  │                               │
  ├── POST /user/login ──────────▶ CaptchaManager 校验验证码
  │                               │  → PasswordUtil 校验密码(BCrypt)
  │                               │  → JwtUtils 签发 JWT(30min)
  │                               │  → UserCacheManager 存 LoginContext 到 Redis
  │◀── UserVO + JWT ─────────────┤
  │                               │
  ├── Authorization: Bearer JWT ─▶ TokenRefreshInterceptor 解析
  │                               │  → 超 15 分钟续签(响应头 X-New-Token)
  │                               │  → @RequireLogin AOP 校验
  │                               │  → LoginContext 加载用户权限
```

### 9.2 图片上传流程

**普通上传**：

```
客户端                           服务端
  │                               │
  ├── upload(file) ──────────────▶ PictureUploadManager 计算 MD5
  │                               │  → 查询 file_resource
  │                               │
  │                               ├── [命中] refCount++ → 写 picture
  │                               ├── [未命中] CosService 上传 → 写 file_resource → 写 picture
  │                               │
  │                               │  SpaceQuotaManager 更新空间容量
  │◀── PictureVO ────────────────┤
```

**分片上传**：

```
客户端                           服务端
  │                               │
  │  前端 SparkMD5 计算 MD5       │
  │                               │
  ├── check(md5, size) ──────────▶ MultipartUploadSupport 查询 file_resource
  │                               │
  │  [秒传命中]                    │
  │◀── PictureVO (CheckUploadVO) ┤
  │                               │
  │  [需上传]                      │
  │◀── 已上传分片列表 ────────────┤
  │                               │
  ├── upload-chunk (逐片) ────────▶ MultipartUploadManager 存储分片
  │                               │  → MultipartUploadSessionStore 更新 Redis 进度
  │                               │
  ├── merge(md5, size, cosKey) ──▶ MultipartUploadCoordinator 合并分片
  │                               │  → 去重 → 写 picture → 更新空间容量
  │◀── PictureVO ────────────────┤
  │                               │
  │     [过期清理]                  │
  │                               │  MultipartUploadCleaner 定时清理过期会话
```

### 9.3 AI 任务流程

```
客户端                           服务端                           AI 服务
  │                               │                               │
  ├── POST /ai/tags 或            │                               │
  │   /ai/draw/submit ──────────▶ AiQuotaManager 检查配额           │
  │                               │  → 创建 Task(PENDING)          │
  │◀── taskId ───────────────────┤  → 分发到线程池                  │
  │                               │                               │
  │                    TaskProcessor CAS 抢占 → PROCESSING         │
  │                               │                               │
  │                               ├── AiTagTaskHandler 或          │
  │                               │   AiDrawTaskHandler ──────────▶│
  │                               │◀── 返回结果 ──────────────────┤
  │                               │                               │
  │                    更新 Task.result → DONE                     │
  │                               │                               │
  │  AiSseEmitterRegistry SSE ──┤                               │
  │                               │                               │
  │  [或] GET /ai/tags/result ───▶ 返回 TaskVO                    │
  │                               │                               │
  │  [失败] TaskProcessor 重试(3次, 5s/10s/30s)                   │
  │  [卡死] TaskProcessor 回收(>5min)                             │
  │  [补偿] TaskDispatchCompensator 重分发                         │
```

### 9.4 协同编辑流程

```
客户端 A                         服务端                          客户端 B
  │                               │                               │
  ├── WS Connect(token,spaceId) ─▶ CollabWebSocketHandler          │
  │                               │  → JWT 认证 + 空间权限校验      │
  │                               │  → CollabSessionRegistry 注册  │
  │                               │  → CollabStateStore 加载状态    │
  │                               │  → 广播 join ─────────────────▶│
  │                               │◀── WS Connect(token,spaceId) ──┤
  │                               │                               │
  ├── lock(pictureId) ──────────▶ CollabStateStore 锁定(Redis)    │
  │                               │  → 广播 lock + lock-denied ──▶│
  │                               │                               │
  ├── transform(pictureId,data) ─▶ 广播 transform ──────────────▶│
  │                               │                               │
  ├── unlock() ──────────────────▶ Lua 脚本原子 CAS 解锁           │
  │                               │  → 广播 unlock ──────────────▶│
  │                               │                               │
  │                    [客户端 B 断连]                              │
  │                               │  → 自动释放 B 的锁(Redis TTL)  │
  │                               │  → 广播 leave ──────────────▶│
```

## 10. 安全设计

### XSS 防御

- 用户输入通过 Jsoup 白名单过滤（XssSanitizer），仅允许安全 HTML 标签
- 分享预览 / 下载接口强制 Content-Type 为 `image/*`
- 前端输出使用 React 默认的 JSX 转义

### 认证安全

- BCrypt 密码哈希（自适应强度）
- JWT 签名密钥 ≥ 32 字节
- 登出即时 Token 黑名单
- 封禁用户全局 Token 失效（Redis 集合 `BANNED_USERS`）
- `USER_TOKEN_INVALID_BEFORE` 批量 Token 失效机制
- 验证码防暴力破解（CaptchaManager）

### 请求安全

- `SecurityHeaderFilter` 注入安全响应头（X-Content-Type-Options、X-Frame-Options、X-XSS-Protection、Cache-Control: no-store）
- 接口限流（RateLimiter）
- IP 检测与记录（IpUtils）
- CORS 配置（CorsConfig）

### 数据安全

- 审计日志自动脱敏（password、token、apiKey 等字段）
- 敏感配置通过环境变量注入，不硬编码
- `application-local.yml` 纳入 `.gitignore`
- 分片上传用户隔离（per-user Redis keys）
