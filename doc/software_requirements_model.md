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
| ORM | MyBatis-Plus | 3.5.14 | 单表零 SQL（乐观锁、分页、防全表更新插件），复杂查询可自定义 |
| 数据库 | MySQL | 8.0 | 社区活跃，JSON 支持，窗口函数 |
| 远程缓存 | Redis + Redisson | 3.27.0 | 分布式锁、Pub/Sub 缓存失效、验证码、会话存储 |
| 认证 | JWT (jjwt) | 0.12.6 | 无状态认证，水平扩展友好 |
| 密码哈希 | BCrypt (spring-security-crypto) | 6.3.0 | 自适应哈希，抗彩虹表（仅 BCrypt，不含完整 Spring Security） |
| 对象存储 | 腾讯云 COS | 5.6.227 | 海量图片存储，CDN 加速 |
| AI 框架 | Spring AI Alibaba | 1.1.2.3 | 统一 AI 接口，支持多模型切换 |
| AI SDK | DashScope SDK | 2.22.18 | 阿里云官方 SDK，直连通义千问 / 万相 |
| WebSocket | Spring WebSocket | — | 原生集成，协同编辑支持 |
| API 文档 | Knife4j | 4.4.0 | OpenAPI 3 + Jakarta，中文友好 |
| XSS 防御 | Jsoup | 1.17.2 | HTML 白名单过滤 |
| 工具库 | Hutool | 5.8.38 | 常用工具集，减少重复代码；Lombok 1.18.36 |
| 异步 | CompletableFuture + 线程池 | — | AiTaskExecutor（core=8, max=32, queue=64）处理 AI 任务 |
| 分布式锁 | Redisson + Lua 脚本 | — | 配置更新、文件上传、协同编辑原子 CAS 解锁 |
| 安全 | Spring AOP | — | @RequireLogin / @RequireAdmin / @AuditLog 三切面 |

### 前端

| 组件 | 选型 | 版本 | 选型理由 |
|------|------|------|----------|
| 框架 | React | 19.2.5 | Hooks 原生支持，Server Components 前景 |
| UI 库 | Ant Design | 6.3.6 | 企业级组件库，国际化完善，暗色模式支持 |
| 构建工具 | Vite | 8.0.9 | 极速 HMR，ESBuild 预构建 |
| 路由 | React Router DOM | 7.14.2 | 声明式路由，懒加载 + 嵌套路由 + ErrorBoundary 支持 |
| HTTP 客户端 | Axios | 1.15.2 | 拦截器机制，请求 / 响应统一处理，Token 自动续签 |
| 日期处理 | Day.js | 1.11.20 | 轻量级日期库，antd 日期组件依赖 |
| 图表 | ECharts + echarts-for-react | 6.1.0 / 3.0.6 | 功能丰富的可视化库，管理后台仪表盘使用 |
| 图片裁剪 | Cropper.js | 1.6.2 | 轻量级图片裁剪 |
| MD5 计算 | SparkMD5 | 3.0.2 | 浏览器端增量 MD5，支持大文件分片 |
| 代码规范 | ESLint | 9 | 代码风格统一 |
| 状态管理 | React Context + 自定义 Hooks | — | 轻量级方案，无 Redux/Zustand，2 个 Context + 14 个 Hooks |
| 实时通信 | WebSocket + SSE | — | useCollabWebSocket（指数退避重连）+ useAiSse（SSE 轮询） |

## 2. 系统架构

### 整体架构

系统采用前后端分离的单体架构，后端按业务领域划分模块，前端为单页应用（SPA），状态管理使用 React Context + 自定义 Hooks 组合方案。

```
┌──────────────────────────────────────────────────────────────┐
│                        客户端（浏览器）                         │
│   ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐   │
│   │  桌面端页面   │  │  移动端页面   │  │  管理后台页面      │   │
│   │  (约14个)    │  │  (7个)       │  │  (7个)            │   │
│   └──────┬──────┘  └──────┬───────┘  └────────┬──────────┘   │
│          └───────────────┼────────────────────┘               │
│                     React SPA (Vite)                          │
│               Context: AuthContext + ThemeContext              │
│               Hooks: 14 个自定义 Hook                         │
└─────────────────────────┬────────────────────────────────────┘
                          │ HTTP / WebSocket
┌─────────────────────────┼────────────────────────────────────┐
│                   Spring Boot Application                     │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │  拦截器链：SecurityHeaderFilter → TokenRefresh →     │     │
│  │           Controller → @RequireLogin/@RequireAdmin   │     │
│  │           (AOP 切面校验) + @AuditLog 审计             │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ user   │ │picture │ │ space  │ │  ai    │ │ system │    │
│  │(Controller│(10组件)│ │(5组件) │ │(2处理器)│ │(2 Cont.)│   │
│  │ +Service)│        │ │        │ │        │ │        │    │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘    │
│      └──────────┴──────────┴─────┬────┴──────────┘          │
│                              common                          │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ cache  │ │ AOP    │ │ infra  │ │ task   │ │ collab │    │
│  │(6个缓存)│ │(3切面) │ │(5组件) │ │(框架)  │ │(WebSocket)│ │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘    │
│      └──────────┴──────────┴──────────┴──────────┘          │
└──────┬──────────┬──────────┬──────────┬──────────────────────┘
       │          │          │          │
  ┌────┴───┐ ┌───┴────┐ ┌───┴───┐ ┌───┴────┐
  │ MySQL  │ │ Redis  │ │  COS  │ │DashScope│
  │(11表)  │ │(6缓存/ │ │(文件) │ │(AI模型) │
  │        │ │ 锁/会话)│ │       │ │         │
  └────────┘ └────────┘ └───────┘ └────────┘
```

### 模块职责

| 模块 | 职责 | 核心组件 |
|------|------|----------|
| `user` | 用户注册、登录、验证码、资料管理、缓存管理、管理端用户管理 | UserController, UserService, CaptchaManager（common）, UserCacheManager（common） |
| `picture` | 图片上传（普通 / 分片 / URL 保存）、CRUD、替换、去重（FileResource）、管理端审核、分享 | PictureController, ShareController, PictureUploadService, PictureDeleteManager, PictureReplaceManager, PictureTagManager, ShareService, FileResourceService |
| `space` | 私人空间、团队空间、成员管理（四级角色）、容量配额、权限检查、管理端空间管理 | SpaceController, SpaceService, SpacePermissionChecker, SpaceQuotaManager, SpaceTeamMemberManager, SpaceAdminManager, SpaceAccessResolver |
| `ai` | AI 标注（视觉理解大模型 qwen3.5-plus-2026-04-20）、文生图（文生图大模型 qwen-image-2.0）、SSE 推送、配额管理、功能开关 | AiController, AiService, AiTagTaskHandler, AiDrawTaskHandler, AiQuotaManager, AiSseEmitterRegistry |
| `system` | 分类标签、轮播图、审计日志、系统统计 | SystemController, AuditLogController, PicSystemService, AuditLogService |
| `task` | 异步任务框架（分发、CAS 抢占、补偿、重试、卡死恢复） | TaskProcessor, TaskDispatchCompensator, TaskService, TaskHandler 接口 |
| `collab` | WebSocket 协同编辑（空间级单编辑锁、操作广播、断连恢复、Redis 状态存储、在线用户感知） | CollabWebSocketHandler, CollabSessionRegistry, CollabStateStore, CollabMessageFactory, WebSocketConfig |
| `common` | 注解（3 个）、AOP 切面（3 个）、缓存（6 个缓存键空间）、配置（8 个）、基础设施（5 个）、工具类（8 个）、常量（5 组）、枚举（5 个） | — |

### DTO / VO 汇总

| 模块 | DTO | VO |
|------|-----|----|
| user | `UserLoginRequest`, `UserRegisterRequest`, `UserEditRequest`, `UserEditByAdminRequest`, `UserIdRequest`, `UserQueryWrapper` | `UserVO`, `CheckCodeVO` |
| picture | `PictureQueryRequest`, `PictureUpdateRequest`, `AdminPictureListDTO`, `DeleteByIdListRequest`, `CheckUploadRequest`, `MergeChunksRequest`, `SavePictureByUrlRequest`, `PictureMetadata`, `ReviewPictureDTO`, `ShareCreateRequest`, `ShareCancelRequest` | `PictureVO`, `CheckUploadVO`, `UploadChunkVO`, `ShareInfoVO`, `ShareFileVO`, `SharePictureVO` |
| space | `CreateSpaceRequest`, `UpdateSpaceRequest`, `SpaceDeleteRequest`, `SpaceSetStatusRequest`, `SpaceAdminUpdateRequest`, `SpacePictureListRequest`, `SpaceQueryWrapper`, `TeamInviteRequest`, `TeamRemoveRequest`, `TeamChangeRoleRequest` | `SpaceVO`, `SpaceMemberVO` |
| ai | `AiDrawPictureDTO`, `AiConfigDTO`, `AiTaskQueryDTO` | `AiTaskSubmitVO`, `AiTaskVO`, `AiStatsVO`, `AiPictureMessage` |
| system | `AddSysPicTypeRequest`, `DeleteTypeRequest`, `AddSysMarqueeRequest`, `DeleteMarqueeRequest`, `AuditLogQueryRequest` | `SystemStatsVO` |
| common | `IdRequest`, `PageRequest` | `Response<T>` 统一响应 |

## 3. 分层设计

### 请求处理流程

```
HTTP Request
    │
    ▼
SecurityHeaderFilter          ← 安全头注入（X-Content-Type-Options: nosniff,
    │                            X-Frame-Options: DENY, X-XSS-Protection: 0）
    │
    ▼
TokenRefreshInterceptor       ← JWT 解析 + Redis 黑名单 + 封禁检查
    │                            → 超过 15 分钟续签（X-New-Token 响应头）
    │                            → LoginContext 加载到 ThreadLocal + Redis
    │
    ▼
MvcConfig                     ← 注册拦截器，排除公开路径
    │                            （/share/info/*, /share/preview/*,
    │                             /share/download/*, /ws/**）
    │
    ▼
Controller                    ← 参数校验，调用 Service
    │
    ├── @RequireLogin         ← LoginCheckAspect AOP 切面校验登录态
    ├── @RequireAdmin         ← AdminCheckAspect AOP 切面校验 role=1
    └── @AuditLog             ← AuditLogAspect AOP 切面记录审计日志（异步写入）
    │
    ▼
Component (Manager)           ← 复杂业务逻辑独立管理器
    │                            （PictureUploadService, SpaceQuotaManager 等）
    ▼
Service                       ← 业务编排，事务管理
    │
    ▼
Mapper (MyBatis-Plus)         ← 数据访问（11 个 Mapper 接口）
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
| `@RequireLogin` | 要求登录（校验 Token + LoginContext） | `LoginCheckAspect` | — |
| `@RequireAdmin` | 要求管理员（role=1） | `AdminCheckAspect` | `errorMes` 自定义错误信息 |
| `@AuditLog` | 自动审计日志（脱敏敏感字段后异步写入） | `AuditLogAspect` | `module`, `operation`, `description` |

## 4. 认证与授权

### 认证流程

```
1. 客户端请求验证码         GET /user/checkCode/login
   ────────────────────────────────────────────────▶
                           CaptchaManager 生成验证码 → 存 Redis(captchaKey → code)
                           → 返回 captchaKey + base64 图片
   ◀────────────────────────────────────────────────

2. 客户端提交登录           POST /user/login {username, password, captchaCode, captchaKey}
   ────────────────────────────────────────────────▶
                           CaptchaManager 校验验证码
                           → PasswordUtil 校验密码(BCrypt)
                           → JwtUtils 签发 JWT(30 分钟)
                           → UserCacheManager 存 LoginContext 到 Redis
                           → 返回 UserVO + JWT
   ◀────────────────────────────────────────────────

3. 后续请求                Authorization: Bearer <JWT>
   ────────────────────────────────────────────────▶
                           TokenRefreshInterceptor 解析 JWT
                           → Redis 黑名单检查 → 封禁检查(BANNED_USERS)
                           → 超过 15 分钟自动续签(响应头 X-New-Token)
                           → LoginContext 加载用户权限(ThreadLocal)
                           → AOP 切面校验登录态/管理员权限
```

### Token 生命周期

- **签发**：JWT 有效期 30 分钟，包含 userId, username, role, level, jti, iat, exp
- **续签**：超过 15 分钟的请求自动签发新 Token（响应头 `X-New-Token`）
- **登出**：JWT 的 jti 加入 Redis 黑名单（`JWT_BLACKLIST:<jti>`，TTL 7 天）
- **封禁**：用户 ID 加入 Redis 集合 `BANNED_USERS`，所有 Token 即时失效
- **批量失效**：`USER_TOKEN_INVALID_BEFORE:<userId>` 时间戳机制，该时间之前签发的 Token 全部失效

### 权限模型

采用等级 + 角色 + 团队成员角色三维权限模型：

**Level（等级）— 控制资源配额**：

| Level | 角色 | 个人空间 | 团队空间 | 团队空间数 | 单文件上传 |
|-------|------|----------|----------|------------|-----------|
| 0 | 普通 | 1 GB | 5 GB | 1 | 10 MB |
| 1 | VIP | 5 GB | 10 GB | 3 | 50 MB |
| 2 | SVIP | 10 GB | 20 GB | 5 | 100 MB |

**Role（系统角色）— 控制功能权限**：

| Role | 说明 | 校验方式 |
|------|------|----------|
| 0 | 普通用户 | `@RequireLogin` |
| 1 | 管理员 | `@RequireAdmin`（AOP 切面校验 role=1，禁止禁用最后一名管理员） |

**团队成员角色 — 控制空间内操作权限**：

| RoleId | 角色 | 可写 | 权限范围 |
|--------|------|------|----------|
| 1 | 所有者 (OWNER) | 是 | 全部权限：查看 / 编辑 / 删除 / 邀请 / 踢出 / 转让 / 变更角色 |
| 2 | 成员 (MEMBER) | 是 | 查看 / 上传 / 编辑（邀请和角色变更权限限定为 OWNER 和 MEMBER） |
| 3 | 编辑者 (EDITOR) | 否 | 查看 / 编辑（不可上传） |
| 4 | 浏览者 (VIEWER) | 否 | 仅查看 |

`LoginContext` 统一管理三层权限（ThreadLocal 存储，`LoginContextHelper` 工具类快捷获取）。

## 5. 缓存设计

### 缓存架构

```
请求 ──▶ Redis TTL 缓存 ──命中──▶ 返回
              │ 未命中
              ▼
         MySQL ──▶ 回填 Redis → 返回
```

### 缓存键空间

RedisCacheManager 管理 6 个缓存键空间，统一采用 CacheManager 模式：

| 缓存 | 键前缀 | TTL | 存储内容 |
|------|--------|-----|----------|
| `userInfoCache` | `cache:userInfo` | 60 分钟 | 用户登录信息、用户资料 |
| `userPermCache` | `cache:userPerm` | 60 分钟 | 用户权限上下文 |
| `sysConfigCache` | `cache:sysConfig` | 1440 分钟（24 小时） | 系统配置（分类标签、轮播图、AI 配置） |
| `spaceDetailCache` | `cache:spaceDetail` | 10 分钟 | 空间详情 |
| `teamMemberCache` | `cache:teamMember` | 10 分钟 | 团队成员列表 |
| `shareCache` | `cache:share` | 30 分钟 | 分享信息 |

### 缓存策略

| 操作 | 策略 |
|------|------|
| 读 | Redis TTL 缓存命中 → 返回；未命中 → 查 DB → 回填 Redis |
| 写 | 更新 DB → 清除 Redis 缓存（逐出策略，不缓存更新） |
| 分布式锁 | Redisson 实现，保障并发写安全 |
| Pub/Sub 失效 | 通过 Redis Pub/Sub 广播实现跨节点缓存一致性（非关键路径，可选） |

### Redis 键空间（非缓存用途）

| 用途 | 键模式 | 说明 |
|------|--------|------|
| 验证码 | `LOGIN_CODE:<key>` / `REGISTER_CODE:<key>` | 登录/注册图形验证码 |
| JWT 黑名单 | `JWT_BLACKLIST:<jti>` | 登出后 JWT 失效，TTL 7 天 |
| Token 失效标记 | `USER_TOKEN_INVALID_BEFORE:<userId>` | 时间戳，该时间前签发的 JWT 全部失效 |
| 封禁集合 | `BANNED_USERS` | Redis Set，被封禁的用户 ID 集合 |
| 用户 ID 映射 | `USER_ID:<token>` | Token → userId 映射 |
| 分片上传会话 | `file:upload:<userId>:<md5>` 系列 | 分片上传进度/状态/ETag/COS Key，TTL 24 小时，按 user 隔离 |
| 协同编辑锁 | 空间级 Redis 锁（Lua 脚本） | 编辑图片锁定，TTL 30 分钟 |
| 协同编辑状态 | 空间图片变换信息 | TTL 2 小时 |
| AI 去重 | `AI:SUBMIT:TAG:<userId>:<pictureId>` / `AI:SUBMIT:DRAW:<userId>:<hash>` | 防重复提交标注/绘图，TTL 30s/200s |

## 6. 状态码定义

### 业务状态码

| 字段 | 值 | 含义 |
|------|-----|------|
| `ExceptionCode.SUCCESS` | `1` | 成功 |
| `ExceptionCode.PARAMETER_ERROR` | `40001` | 参数错误 |
| `ExceptionCode.UNAUTHORIZED` | `40002` | 未认证 |
| `ExceptionCode.FORBIDDEN` | `40003` | 禁止访问 |
| `ExceptionCode.NOT_FOUND` | `40004` | 资源未找到 |
| `ExceptionCode.NOT_LOGIN` | `40005` | 未登录（前端触发重新登录） |
| `ExceptionCode.AI_TAG_ERROR` | `40006` | AI 生成图片标签失败 |
| `ExceptionCode.AI_DRAW_ERROR` | `40007` | AI 生成图片失败 |
| `ExceptionCode.CONFLICT` | `40009` | 操作冲突 |
| `ExceptionCode.UNPROCESSABLE_ENTITY` | `40022` | 无法处理的实体 |
| `ExceptionCode.TOO_MANY_REQUESTS` | `40029` | 请求过多（限流 / AI 去重命中） |
| `ExceptionCode.INTERNAL_SERVER_ERROR` | `50000` | 服务器内部错误 |
| `ExceptionCode.SERVICE_UNAVAILABLE` | `50001` | 服务不可用（AI 未配置等） |
| `ExceptionCode.DATABASE_ERROR` | `50003` | 数据库错误 |

### 用户状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `user.status` | `1` | 正常 |
| `user.status` | `0` | 禁用 |

### 用户等级

| 字段 | 值 | 含义 |
|------|-----|------|
| `user.level` | `0` | 普通用户 |
| `user.level` | `1` | VIP |
| `user.level` | `2` | SVIP |

### 用户角色

| 字段 | 值 | 含义 |
|------|-----|------|
| `user.role` | `0` | 普通用户 |
| `user.role` | `1` | 管理员 |

### 图片状态（精选标记）

| 字段 | 值 | 含义 |
|------|-----|------|
| `picture.is_selected` | `0` | 普通 |
| `picture.is_selected` | `1` | 精选 |
| `picture.is_selected` | `2` | 申请中 |

### 空间类型

| 字段 | 值 | 含义 |
|------|-----|------|
| `space.type` | `0` | 私人空间 |
| `space.type` | `1` | 团队空间 |

### 团队成员角色

| 字段 | 值 | 含义 |
|------|-----|------|
| `space_team_member.role_id` | `1` | 所有者 (OWNER) |
| `space_team_member.role_id` | `2` | 成员 (MEMBER) |
| `space_team_member.role_id` | `3` | 编辑者 (EDITOR) |
| `space_team_member.role_id` | `4` | 浏览者 (VIEWER) |

### AI 任务状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `task.status` | `PENDING` | 等待处理 |
| `task.status` | `PROCESSING` | 处理中 |
| `task.status` | `DONE` | 完成 |
| `task.status` | `FAILED` | 失败 |
| `task.biz_type` | `ai_tag` | AI 标注 |
| `task.biz_type` | `ai_draw` | AI 文生图 |

### AI 任务 VO 映射

| AiTaskVO 字段 | 映射规则 |
|---------------|----------|
| `type` | `ai_tag` → `0`, `ai_draw` → `2` |
| `status` | `PENDING/PROCESSING` → `0`, `DONE` → `1`, `FAILED` → `2` |

### 审计日志

| 字段 | 值 | 含义 |
|------|-----|------|
| `sys_audit_log.result` | `0` | 操作失败 |
| `sys_audit_log.result` | `1` | 操作成功 |

## 7. 功能模块设计

### 7.1 用户模块

**功能清单**：
- 注册（用户名 6-30 字符 + 密码 8-32 字符 + 图形验证码，限流 3 次 / 300s）
- 登录（用户名 + 密码 + 图形验证码，限流 5 次 / 60s）
- 图形验证码（登录 / 注册分别生成，CaptchaManager 管理，Redis 存储）
- 退出登录（JWT 加入黑名单，清除 LoginContext 缓存）
- 查看 / 编辑个人资料（昵称、头像、邮箱、手机号、密码；改密码后重新签发 JWT + 旧 Token 批量失效）
- 搜索用户（按用户名模糊搜索）
- 管理端：用户列表（多条件筛选，分页）、封禁 / 解封、编辑用户信息（等级、角色）

**设计要点**：
- 密码使用 BCrypt 哈希存储（spring-security-crypto 6.3.0，不含完整 Spring Security）
- 修改密码后调用 `invalidateUserTokens(id)` 使该用户所有旧 Token 失效（`USER_TOKEN_INVALID_BEFORE` 时间戳）
- 头像上传走图片模块接口（`/picture/avatar`），前端路径 `/user/profile` 查看
- 验证码由 CaptchaManager 管理，存储在 Redis（Key: `LOGIN_CODE:<key>` / `REGISTER_CODE:<key>`）
- 注册时自动创建私人空间（事务保证）
- 管理员不可禁用最后一名管理员

### 7.2 图片模块

**功能清单**：
- 普通上传（单文件 Multipart，后端自动计算 MD5）
- 头像上传（支持管理员代传）
- 分片上传（大文件：秒传校验 → 分片上传 → 合并，3 组件协作）
- URL 保存图片（通过 HTTP 下载到 COS）
- 图片列表（公开，分页、分类筛选、搜索）
- 图片编辑（名称、标签、分类、描述）
- 图片裁剪（在线 Canvas 编辑 - Cropper.js）
- 图片替换（协同编辑中替换文件内容，支持 collab 标记触发 WebSocket 广播）
- 图片删除（单张 / 批量，级联处理引用计数 + COS 文件清理 + 空间容量回收）
- 图片推荐（AI 推荐引擎，功能开关控制 `recommendationEnabled`）
- 管理端：图片列表（多条件筛选）、精选审核（设为普通 / 精选 / 拒绝申请）

**设计要点**：
- 上传前后端计算 MD5，查询 `file_resource` 表去重，命中则 `refCount++`，不实际上传
- 分片上传由 3 个组件协作：PictureUploadService（检查/分片/合并协调）、Redis 会话存储（24 小时 TTL，per-user 隔离）、前端的 upload.js（SparkMD5 增量计算）
- 上传限制按用户等级：普通 10 MB / VIP 50 MB / SVIP 100 MB（file_resource 存全量，空间配额独立控制）
- 图片存储到腾讯云 COS，元数据存储到 MySQL
- 图片删除由 PictureDeleteManager 管理：级联处理引用计数递减、`refCount=0` 时清理 COS 文件、空间容量回收
- 图片替换由 PictureReplaceManager 管理：旧文件引用递减 + 新文件上传（去重复用）
- 图片表无独立的 `status` 和 `is_private` 字段（SQL 迁移中已 DROP），精选状态使用 `is_selected`（0=普通 1=精选 2=申请中）
- 空间内图片列表由 SpaceController 管理（带空间权限校验）

### 7.3 分享模块

**功能清单**：
- 创建分享链接（可配置有效期、下载权限、最大查看次数，支持多图打包）
- 多图分享（`picture_share_item` 关联表，`sort_order` 控制排序）
- 团队分享（团队空间所有者可分享空间内任意成员的图片）
- 查看分享信息（Token 校验 + 过期检查 + 查看次数限制）
- 预览分享图片（免登录，支持 `size` 参数缩略图，`image/*` 流返回）
- 下载分享图片（免登录，文件名自动追加扩展名，需 `allow_download` 权限）
- 取消分享

**设计要点**：
- 分享链接使用 UUID Token 标识，数据库同时存储明文 Token（唯一索引 `uk_share_token`）和 SHA-256 哈希
- 创建时仅返回一次明文 Token，后续无法再获取 Token 明文
- 分享权限校验：图片所有者 或 团队空间 Owner 身份（`SpacePermissionChecker`）
- 预览接口支持 `size` 参数，通过 COS `imageMogr2` 实时生成缩略图，减少带宽消耗
- 下载文件名无扩展名时根据 Content-Type 自动追加（如 `image/png` → `.png`）
- 预览和下载接口仅允许 `image/*` Content-Type，防 XSS
- 支持过期自动失效和查看次数限制（`max_view_count`，0=不限）
- 下载权限独立控制（`allow_download`：0=仅预览 1=允许下载）
- 预览 / 下载接口为公开接口（MvcConfig 排除），不经过 Token 认证

### 7.4 空间模块

**功能清单**：
- 创建空间（私人 / 团队，按等级限制数量和容量）
- 空间列表（按类型筛选）
- 空间详情（Redis 缓存，10 分钟 TTL）
- 空间内图片管理（分页、带权限校验）
- 空间信息更新
- 团队成员管理（邀请、移除、角色变更）
- 可保存空间列表（SaveToSpace）
- 管理端：空间列表（分页）、编辑、删除、启用 / 禁用

**设计要点**：
- 每个用户有且仅有一个私人空间（注册时自动创建，`uk_user_type` 唯一约束）
- 团队空间数量上限按等级：level 0 → 1 个 / level 1 → 3 个 / level 2 → 5 个
- 空间容量按等级分配（`SpaceConstants` 区分个人和团队空间）：
  - 个人：普通 1 GB / VIP 5 GB / SVIP 10 GB
  - 团队：普通 5 GB / VIP 10 GB / SVIP 20 GB
- SQL 默认值（`storage_size` 536870912 = 512MB）与 Java 常量（`DEFAULT_STORAGE_SIZE` 1 GB）存在差异，实际运行时以 `SpaceConstants` 为准
- 使用乐观锁（`space.version`）防止并发写入时容量计算错误
- `SpacePermissionChecker` 校验用户空间访问权限（空间存在性 + 状态 + 成员角色）
- `SpaceQuotaManager` 管理空间容量配额（上传校验 + 删除回收）
- 团队成员四级角色：OWNER(1) / MEMBER(2) / EDITOR(3) / VIEWER(4)

### 7.5 AI 模块

**功能清单**：
- AI 标注：上传图片 → 自动提取标签和描述（通义千问视觉大模型 `qwen3.5-plus-2026-04-20`）
- AI 文生图：输入文本描述 + 风格 + 尺寸 → 生成图片（万相文生图大模型 `qwen-image-2.0`）
- 10 种绘图风格：photography, anime, oil painting, watercolor, sketch, 3d, pixel art, flat illustration, chinese painting, cyberpunk
- 5 种绘图尺寸：`1:1`(2048×2048), `16:9`(2688×1536), `9:16`(1536×2688), `4:3`(2368×1728), `3:4`(1728×2368)
- SSE 实时推送任务结果（AiSseEmitterRegistry，前端 useAiSse hook 重试 3 次间隔 2s）
- 下载 AI 生成的图片（返回 COS URL）
- 月度配额管理（标注 / 生图分别配额，按用户等级分配，Redis 原子计数管理）
- AI 任务管理端：任务列表（分页，支持类型/状态筛选）、统计数据、功能开关

**设计要点**：

**标注流程**：
1. 用户提交图片 ID → Redis 去重键 `AI:SUBMIT:TAG:<userId>:<pictureId>`（30s TTL）→ AiQuotaManager 检查并扣除标注配额
2. 创建 Task (PENDING) → 分发到 aiTaskExecutor 线程池
3. Worker 通过 CAS 抢占（条件 UPDATE）→ PROCESSING
4. AiTagTaskHandler 调用 Spring AI Alibaba + 通义千问视觉大模型，提取标签和描述
5. 写入 Task.result → DONE → SSE 推送

**文生图流程**：
1. 用户提交文本描述（最多 500 字符）+ 风格 + 尺寸 → Redis 去重键（基于描述+风格+尺寸+排除词 SHA-256 哈希，200s TTL）→ AiQuotaManager 检查并扣除绘图配额
2. 创建 Task (PENDING) → 分发到线程池
3. Worker CAS 抢占 → PROCESSING
4. AiDrawTaskHandler 调用 DashScope SDK + 万相文生图大模型
5. 生成图片 URL → 下载到本地 → 上传到 COS → 创建 picture 记录 → DONE → SSE 推送

**配额管理**：
| 用户等级 | 标注月配额 | 绘图月配额 |
|----------|-----------|-----------|
| 普通(level 0) | 有限 | 有限 |
| VIP(level 1) | 较多（可管理端配置） | 较多（可管理端配置） |
| SVIP(level 2) | 最多（可管理端配置） | 最多（可管理端配置） |

- 配额使用 Redis 原子计数器管理（`AiQuotaManager`）
- 任务提交前先扣配额（`checkAndConsume`），任务失败或去重命中时调用 `refund` 回退

**容错机制**：
- 失败重试：最多 3 次，退避间隔 5s / 10s / 30s
- 卡死恢复：自动回收 PROCESSING 超过 5 分钟的任务
- 任务补偿：TaskDispatchCompensator 处理分发失败的任务
- CAS 抢占：`UPDATE task SET status='PROCESSING' WHERE id=? AND status='PENDING'`，避免重复消费
- 可插拔 Handler：TaskHandler 接口，当前实现 ai_tag（AiTagTaskHandler）和 ai_draw（AiDrawTaskHandler）

**功能开关**（4 项，独立控制，存储于 `pic_system.ai_config`）：
| 开关 | 键 | 默认值 |
|------|-----|--------|
| AI 标注 | `taggingEnabled` | true |
| 协同编辑 | `editingEnabled` | false |
| AI 文生图 | `generationEnabled` | true |
| 图片推荐 | `recommendationEnabled` | true |

### 7.6 协同编辑模块

**功能清单**：
- 多人实时在线编辑同一团队空间的图片
- 图片操作广播（缩放 scale、旋转 rotation、裁剪 crop）
- 空间级单编辑锁管理（Lua 脚本原子操作）
- 编辑冲突通知（lock-denied）
- 断连自动释放（Redis TTL 30 分钟）
- 重连后状态同步（resync）
- 在线用户感知（presence / join / leave）

**设计要点**：

**连接流程**：
1. 客户端通过 `ws://host/api/ws/collab?token=<JWT>&spaceId=<id>` 建立连接（前端 useCollabWebSocket hook 管理）
2. CollabWebSocketHandler 验证 JWT 和空间权限（SpacePermissionChecker）
3. CollabSessionRegistry 注册会话到内存 ConcurrentHashMap
4. CollabStateStore 从 Redis 加载当前锁状态（如有已锁定图片+变换信息）
5. 广播 join 消息给同空间其他在线用户

**消息协议**：

| 类型 | 方向 | 说明 |
|------|------|------|
| `join` | 客户端 → 服务端 | 加入空间编辑会话 |
| `leave` | 客户端 → 服务端 | 离开空间编辑会话 |
| `presence` | 服务端 → 客户端 | 当前在线用户列表 |
| `lock` | 客户端 → 服务端 → 全体 | 锁定某张图片（空间级单编辑锁） |
| `unlock` | 客户端 → 服务端 → 全体 | 释放图片锁 |
| `lock-denied` | 服务端 → 客户端 | 锁定被拒绝（空间已有其他图片被锁定） |
| `transform` | 客户端 → 服务端 → 全体 | 图片变换操作（scale, rotation, crop） |
| `file-replaced` | 客户端 → 服务端 → 全体 | 图片文件已替换 |
| `resync` | 服务端 → 客户端 | 重连后同步当前状态 |

**前端 WebSocket 实现**（useCollabWebSocket hook）：
- 自动连接：mount 后 150ms 延迟启动连接
- 断线重连：指数退避 1s, 2s, 4s, 8s ... 最大 30s，最多 10 次重试（正常关闭 code 1000 不重连）
- 可见性恢复：`visibilitychange` 事件检测自动重连
- 清理释放：unmount 时 close(1000) 并记录到 WeakSet 避免重连

**锁机制**：
- 空间级单编辑锁：同一空间同时只允许锁定一张图片进行编辑
- Redis 分布式锁（TTL 30 分钟），Lua 脚本保证原子 CAS 解锁
- 编辑状态存储在 Redis（TTL 2 小时）
- 断连自动释放锁（WebSocket 关闭事件触发 unlock）
- CollabStateStore 提供锁状态读取和存储能力

### 7.7 系统配置模块

**功能清单**：
- 分类标签管理（CRUD，pic_system 表 type_list_key 键值，Redis 缓存 24 小时）
- 轮播图管理（增删查，pic_system 表 marquees_key 键值，JSON 数组存 URL 列表）
- AI 配置管理（pic_system 表 ai_config 键值，JSON 存储，含全部功能开关 + 配额配置）
- 审计日志查询（多条件筛选：操作类型、模块、用户、时间范围、IP）
- 系统统计数据（用户数、图片数、空间数等，ECharts 可视化）

**设计要点**：
- 配置数据存储在 `pic_system` 表（键值对模式：`syskey` + `sysvalue` JSON）
- 热点数据走 Redis 缓存（sysConfigCache，24 小时 TTL），变更时主动失效
- 审计日志通过 `@AuditLog` AOP 自动记录，由 AuditLogWriter 异步写入
- 敏感字段（password、token、apiKey、secret）自动脱敏
- 审计日志包含：操作人（userId + username）、操作类型（operation）、模块（module）、详情（detail）、HTTP 方法（method）、URL（url）、参数（params）、结果（result 0/1）、异常（errorMsg）、IP（ip）、时间（createTime）
- 系统统计接口返回：用户总数、图片总数、空间总数等汇总数据

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
| `user` | `nickname` UNIQUE | 昵称唯一 |
| `file_resource` | `(md5, size)` UNIQUE | 文件去重联合唯一 |
| `file_resource` | `ref_count >= 0` CHECK | 引用计数非负 |
| `space` | `(user_id, type)` UNIQUE | 每用户每类型空间唯一 |
| `space_team_member` | `(space_id, user_id)` UNIQUE | 空间内用户唯一 |
| `picture` | `(resource_id, user_id, space_id)` UNIQUE | 同一空间内同一文件资源唯一 |
| `task` | `task_id` UNIQUE | 任务 UUID 唯一 |
| `picture_share` | `share_token` UNIQUE | 分享 Token 唯一 |

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
  ├── Authorization: Bearer JWT ─▶ TokenRefreshInterceptor 解析 JWT
  │                               │  → Redis 黑名单检查
  │                               │  → BANNED_USERS 封禁检查
  │                               │  → USER_TOKEN_INVALID_BEFORE 批量失效检查
  │                               │  → 超 15 分钟续签(响应头 X-New-Token)
  │                               │  → LoginContext 加载到 ThreadLocal
  │                               │  → AOP 切面(@RequireLogin/@RequireAdmin)校验
```

### 9.2 图片上传流程

**普通上传**：

```
客户端                           服务端
  │                               │
  ├── upload(file) ──────────────▶ PictureUploadService 计算 MD5
  │                               │  → 查询 file_resource (md5 + size)
  │                               │
  │                               ├── [命中] refCount++ → 写 picture
  │                               ├── [未命中] CosService 上传 → 写 file_resource(refCount=1) → 写 picture
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
  ├── check(md5, size) ──────────▶ PictureUploadService 查询 file_resource
  │                               │  → 检查 COS 已上传分片列表(Redis 会话)
  │                               │
  │  [秒传命中]                    │
  │◀── PictureVO (CheckUploadVO) ┤
  │                               │
  │  [需上传]                      │
  │◀── 已上传分片列表 + uploadId ──┤
  │                               │
  ├── upload-chunk (逐片) ────────▶ PictureUploadService 存储分片到 COS
  │                               │  → Redis 存储分片进度(24h TTL, per-user 隔离)
  │                               │
  ├── merge(md5, size, cosKey) ──▶ PictureUploadService 合并分片
  │                               │  → file_resource 去重检查 → 写 picture
  │                               │  → 更新空间容量
  │◀── PictureVO ────────────────┤
```

### 9.3 AI 任务流程

```
客户端                           服务端                           AI 服务
  │                               │                               │
  ├── POST /ai/tags 或            │                               │
  │   /ai/draw/submit ──────────▶ Redis 去重检查(AI:SUBMIT:...)    │
  │                               │  → AiQuotaManager 检查/扣除配额 │
  │                               │  → TaskService 创建 Task(PENDING)│
  │◀── taskId ───────────────────┤  → aiTaskExecutor 分发到线程池   │
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
  │  [失败] TaskProcessor 重试(3次, 5s/10s/30s)                   │
  │  [卡死] TaskProcessor 回收(>5min)                             │
  │  [补偿] TaskDispatchCompensator 重分发                         │
```

### 9.4 协同编辑流程

```
客户端 A                         服务端                          客户端 B
  │                               │                               │
  ├── WS Connect(token,spaceId) ─▶ CollabWebSocketHandler          │
  │                               │  → JWT 验证 + SpacePermission  │
  │                               │  → CollabSessionRegistry 注册  │
  │                               │  → CollabStateStore 加载锁状态  │
  │                               │  → 广播 join ─────────────────▶│
  │                               │◀── WS Connect(token,spaceId) ──┤
  │                               │                               │
  ├── lock(pictureId) ──────────▶ CollabStateStore Lua 锁定       │
  │                               │  (空间级单编辑锁：同一空间同时 │
  │                               │   只允许锁定一张图片)           │
  │                               │  → 广播 lock ────────────────▶│
  │                               │  → [其它用户] lock-denied      │
  │                               │                               │
  ├── transform(pictureId,data) ─▶ 广播 transform(scale/rotation/ │
  │                               │   crop) ─────────────────────▶│
  │                               │                               │
  ├── unlock() ──────────────────▶ Lua 脚本原子 CAS 解锁           │
  │                               │  → 广播 unlock ──────────────▶│
  │                               │                               │
  │                    [客户端 B 断连]                              │
  │                               │  → 自动释放 B 的锁(Redis TTL)  │
  │                               │  → 广播 leave ──────────────▶│
```

### 9.5 分享流程

```
客户端                           服务端                          访客
  │                               │                               │
  ├── POST /share/create ────────▶ 权限校验(所有者或团队 Owner)     │
  │   {pictureIds, expireDays,    │  → INSERT picture_share        │
  │    allowDownload, maxViewCount│  → INSERT picture_share_item   │
  │◀── {shareUrl} (仅一次明文) ──┤                               │
  │                               │                               │
  │                               │  ◀── GET /share/info/{token} ──┤
  │                               │  SHA-256 匹配 + 校验过期       │
  │                               │  + 查看次数检查                │
  │                               │  ──▶ ShareInfoVO ────────────▶│
  │                               │                               │
  │                               │  ◀── GET /share/preview/{token}┤
  │                               │       ?size=400 & pictureId=   │
  │                               │  COS imageMogr2 缩略图         │
  │                               │  ──▶ image/* 流 ─────────────▶│
  │                               │                               │
  │                               │  ◀── GET /share/download/{token┤
  │                               │  allow_download 校验           │
  │                               │  COS 原图 + 自动追加扩展名     │
  │                               │  ──▶ image/* 附件 ───────────▶│
```

## 10. 安全设计

### XSS 防御

- 用户输入通过 Jsoup 白名单过滤（XssSanitizer），仅允许安全 HTML 标签
- 分享预览 / 下载接口强制 Content-Type 为 `image/*`
- 下载文件名无扩展名时根据 Content-Type 自动追加，防止浏览器误判文件类型
- 前端输出使用 React 默认的 JSX 转义

### 认证安全

- BCrypt 密码哈希（自适应强度，spring-security-crypto 6.3.0，无完整 Spring Security）
- JWT 签名密钥 ≥ 32 字节（环境变量注入，本地有 fallback 值）
- 登出即时 Token 黑名单（Redis JWT_BLACKLIST:<jti>，TTL 7 天）
- 封禁用户全局 Token 失效（Redis 集合 BANNED_USERS，实时检查）
- USER_TOKEN_INVALID_BEFORE 批量 Token 失效机制（修改密码等操作时触发）
- 验证码防暴力破解（CaptchaManager，登录 5 次 / 60s 限流，注册 3 次 / 300s 限流）
- Redis 去重防刷（AI 标注 30s / 绘图 200s 去重 TTL，配额不足时 refund）
- 管理员禁止禁用最后一名管理员

### 请求安全

- SecurityHeaderFilter 注入安全响应头（X-Content-Type-Options: nosniff、X-Frame-Options: DENY、X-XSS-Protection: 0）
- 接口限流（RateLimiter 注解，登录 / 注册接口）
- IP 检测与记录（IpUtils，审计日志记录客户端 IP）
- CORS 配置（CorsConfig，白名单：localhost:5173,3000,127.0.0.1:5173,3000，暴露 X-New-Token、Content-Disposition）
- 分片上传用户隔离（per-user Redis keys，防止 session 串号）
- Long → String JSON 序列化（JsonConfig，防止前端精度丢失）

### 数据安全

- 审计日志自动脱敏（password、token、apiKey、secret 等字段在写入前过滤）
- 敏感配置通过环境变量注入；`application-local.yml` 含明文密钥（适合本地开发，已纳入 `.gitignore`）
- 数据库密码、JWT 密钥、COS 密钥、DashScope API Key 均支持环境变量配置
- 乐观锁（`space.version`, `picture.version`, `file_resource.version`）防止并发写入数据竞争
- 图片删除后 `refCount=0` 时清理 COS 文件，避免存储泄漏
