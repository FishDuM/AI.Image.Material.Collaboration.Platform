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
| 本地缓存 | Caffeine | — | 高性能 Java 本地缓存 |
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
| 图表 | ECharts | 6.1.0 | 功能丰富的可视化库 |
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
│  │           LoginInterceptor → Controller             │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ user   │ │picture │ │ space  │ │  ai    │ │ system │    │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘    │
│      └──────────┴──────────┴─────┬────┴──────────┘          │
│                              common                          │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ cache  │ │ AOP    │ │ auth   │ │ task   │ │ collab │    │
│  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘    │
│      └──────────┴──────────┴──────────┴──────────┘          │
└──────┬──────────┬──────────┬──────────┬──────────────────────┘
       │          │          │          │
  ┌────┴───┐ ┌───┴────┐ ┌───┴───┐ ┌───┴────┐
  │ MySQL  │ │ Redis  │ │  COS  │ │DashScope│
  └────────┘ └────────┘ └───────┘ └────────┘
```

### 模块职责

| 模块 | 职责 |
|------|------|
| `user` | 用户注册、登录、资料管理、管理端用户管理 |
| `picture` | 图片上传（普通 / 分片）、CRUD、URL 保存、管理端审核 |
| `space` | 私人空间、团队空间、成员管理、管理端空间管理 |
| `ai` | AI 标注、文生图、SSE 推送、管理端 AI 配置 |
| `system` | 分类标签、轮播图、审计日志、系统统计 |
| `task` | 异步任务框架（分发、抢占、补偿、重试） |
| `collab` | WebSocket 协同编辑（锁管理、操作广播、断连恢复） |
| `common` | 注解、AOP 切面、缓存、配置、常量、DTO/VO/Entity、枚举、异常、拦截器、工具类 |

## 3. 分层设计

### 请求处理流程

```
HTTP Request
    │
    ▼
SecurityHeaderFilter          ← 安全头注入（CSP、HSTS 等）
    │
    ▼
TokenRefreshInterceptor       ← JWT 解析 + 15 分钟自动续签
    │
    ▼
LoginInterceptor              ← 登录态校验 + UserHolder 设置
    │
    ▼
Controller                    ← 参数校验，调用 Service
    │
    ▼
Service                       ← 业务逻辑，事务管理
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
| DTO | 入参对象（Controller 接收） | `XxxDTO` |
| VO | 出参对象（Controller 返回） | `XxxVO` |
| Entity | 数据库实体（Mapper 映射） | `Xxx` 或 `XxxEntity` |

### AOP 注解

| 注解 | 作用 | 切面 |
|------|------|------|
| `@RequireLogin` | 要求登录 | `RequireLoginAspect` |
| `@RequireAdmin` | 要求管理员（level >= 3） | `RequireAdminAspect` |
| `@AuditLog` | 自动审计日志 | `AuditLogAspect` |
| `@RateLimiter` | 接口限流 | `RateLimiterAspect` |

## 4. 认证与授权

### 认证流程

```
1. 客户端请求验证码        GET /user/checkCode/login
   ────────────────────────────────────────────────▶
                          返回 captchaKey + base64 图片
   ◀────────────────────────────────────────────────

2. 客户端提交登录          POST /user/login {username, password, captchaCode, captchaKey}
   ────────────────────────────────────────────────▶
                          校验验证码 → 校验密码(BCrypt) → 签发 JWT
                          → Redis 存 LoginContext
                          → 返回 UserVO + JWT
   ◀────────────────────────────────────────────────

3. 后续请求                Authorization: Bearer <JWT>
   ────────────────────────────────────────────────▶
                          TokenRefreshInterceptor 解析
                          → 超过 15 分钟自动续签
                          → LoginInterceptor 校验登录态
                          → UserHolder 设置当前用户
```

### Token 生命周期

- **签发**：JWT 有效期 30 分钟
- **续签**：超过 15 分钟的请求自动签发新 Token
- **登出**：JWT 加入 Redis 黑名单
- **封禁**：用户 ID 加入 Redis 集合 `banned:users`，所有 Token 即时失效

### 权限模型

采用简化的等级权限模型（非 RBAC），通过 `user.level` 字段控制：

| Level | 角色 | 权限范围 |
|-------|------|----------|
| 0 | 普通用户 | 基础功能 |
| 1 | VIP | 更大上传限制、更多空间配额 |
| 2 | SVIP | 最大上传限制、最多空间配额 |
| 3 | 管理员 | 全部功能 + 管理后台 |

管理端接口通过 `@RequireAdmin` 注解 + AOP 切面校验 `level >= 3`。

## 5. 缓存设计

### 多级缓存架构

```
请求 ──▶ L1 (Caffeine) ──命中──▶ 返回
              │ 未命中
              ▼
         L2 (Redis) ──命中──▶ 回填 L1 → 返回
              │ 未命中
              ▼
         MySQL ──▶ 回填 L2 + L1 → 返回
```

### 缓存策略

| 操作 | 策略 |
|------|------|
| 读 | L1 → L2 → DB，命中时回填上层 |
| 写 | 更新 DB → 更新 L2 → 广播失效 L1 |
| 删 | 删除 DB → 删除 L2 → 广播失效 L1 |
| 多节点 | Redisson Pub/Sub 广播本地缓存失效 |

### 缓存对象

- 用户登录信息（LoginContext）
- 系统配置（分类标签、轮播图）
- 热点图片数据
- 验证码

## 6. 状态码定义

### 用户状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `user.status` | `1` | 正常 |
| `user.status` | `0` | 禁用 |

### 图片状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `picture.status` | `1` | 正常 |
| `picture.status` | `0` | 禁用 |
| `picture.status` | `2` | 待审核 |
| `picture.is_private` | `0` | 公开 |
| `picture.is_private` | `1` | 私有 |

### 空间状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `space.type` | `0` | 私人空间 |
| `space.type` | `1` | 团队空间 |
| `space_team_member.roleId` | `1` | 所有者 |
| `space_team_member.roleId` | `2` | 成员 |

### AI 任务状态

| 字段 | 值 | 含义 |
|------|-----|------|
| `task.status` | `PENDING` | 等待处理 |
| `task.status` | `PROCESSING` | 处理中 |
| `task.status` | `DONE` | 完成 |
| `task.status` | `FAILED` | 失败 |
| `task.bizType` | `ai_tag` | AI 标注 |
| `task.bizType` | `ai_draw` | AI 文生图 |

## 7. 功能模块设计

### 7.1 用户模块

**功能清单**：
- 注册（用户名 + 密码 + 验证码）
- 登录（用户名 + 密码 + 验证码）
- 退出登录
- 查看 / 编辑个人资料（昵称、头像、密码）
- 搜索用户
- 管理端：用户列表、封禁 / 解封、编辑用户信息

**设计要点**：
- 密码使用 BCrypt 哈希存储
- 修改密码后重新签发 JWT，旧 Token 即时失效
- 头像上传走图片模块，限制 5 MB

### 7.2 图片模块

**功能清单**：
- 普通上传（单文件）
- 分片上传（大文件：秒传校验 → 分片上传 → 合并）
- URL 保存图片
- 图片列表（分页、搜索、排序）
- 图片编辑（名称、标签、分类）
- 图片删除（批量）
- 协同编辑中的图片替换
- 管理端：审核队列（审批 / 拒绝）、设为精选

**设计要点**：
- 上传前计算 MD5，查询 `file_resource` 表去重，命中则 refCount++，不实际上传
- 分片上传支持断点续传：`check` 接口返回已上传分片列表
- 上传限制按用户等级：普通 10 MB / VIP 1 GB / SVIP 10 GB
- 图片存储到腾讯云 COS，元数据存储到 MySQL

### 7.3 分享模块

**功能清单**：
- 创建分享链接（可配置有效期、下载权限、最大查看次数）
- 多图分享
- 查看分享信息
- 预览分享图片（免登录）
- 下载分享图片（免登录）
- 取消分享

**设计要点**：
- 分享链接使用 UUID Token 标识，存储 SHA-256 哈希
- 预览和下载接口仅允许 `image/*` Content-Type，防 XSS
- 支持过期自动失效

### 7.4 空间模块

**功能清单**：
- 创建空间（私人 / 团队）
- 空间列表（按类型筛选）
- 空间内图片管理
- 团队成员管理（邀请、移除、改角色）
- 管理端：空间列表、编辑、删除、启用 / 禁用

**设计要点**：
- 每个用户有且仅有一个私人空间（注册时自动创建）
- 团队空间数量上限按等级：普通 1 / VIP 2 / SVIP 5
- 空间容量按等级分配：普通 512 MB / VIP 50 GB / SVIP 100 GB
- 使用乐观锁防止并发写入时容量计算错误

### 7.5 AI 模块

**功能清单**：
- AI 标注：上传图片 → 自动提取标签和描述
- AI 文生图：输入文本描述 → 生成图片
- SSE 实时推送任务结果
- 下载 AI 生成的图片
- 管理端：任务列表、统计、功能开关（标注开关、生图开关、推荐开关）

**设计要点**：

**标注流程**：
1. 用户提交图片 ID → 创建 Task (PENDING)
2. 异步分发到线程池
3. Worker 通过 CAS 抢占（条件 UPDATE）→ PROCESSING
4. 调用 Spring AI Alibaba Agent + 通义千问视觉理解
5. 提取标签和描述 → 写入 Task.result → DONE
6. SSE 推送结果到前端

**文生图流程**：
1. 用户提交文本描述 → 创建 Task (PENDING)
2. 异步分发到线程池
3. Worker 通过 CAS 抢占 → PROCESSING
4. 调用 DashScope SDK + 万相模型
5. 生成图片 URL → 写入 Task.result → DONE
6. SSE 推送结果到前端

**容错机制**：
- 失败重试：最多 3 次，退避间隔 5s / 10s / 30s
- 卡死恢复：自动回收 PROCESSING 超过 5 分钟的任务
- CAS 抢占：`UPDATE task SET status='PROCESSING' WHERE id=? AND status='PENDING'`，避免重复消费

### 7.6 协同编辑模块

**功能清单**：
- 多人实时在线编辑同一团队空间的图片
- 图片操作广播（缩放、旋转、裁剪）
- 图片级锁管理
- 断连自动释放
- 重连后状态同步

**设计要点**：

**连接流程**：
1. 客户端通过 `ws://host/ws/collab?token=<JWT>&spaceId=<id>` 建立连接
2. 服务端验证 JWT 和空间权限
3. 注册会话到 SessionRegistry
4. 发送当前锁状态和图片变换信息（resync）

**消息类型**：

| 类型 | 方向 | 说明 |
|------|------|------|
| `lock` | 客户端 → 服务端 | 锁定某张图片 |
| `unlock` | 客户端 → 服务端 | 释放锁定 |
| `transform` | 客户端 → 服务端 → 全体 | 图片变换操作（scale, rotation, crop） |
| `resync` | 服务端 → 客户端 | 重连后同步当前状态 |

### 7.7 系统配置模块

**功能清单**：
- 分类标签管理（CRUD，Redis 缓存）
- 轮播图管理（增删查）
- 审计日志查询（分页）
- 系统统计数据

**设计要点**：
- 配置数据存储在 `pic_system` 表（键值对模式）
- 热点数据走 Redis 缓存，变更时主动失效
- 审计日志通过 `@AuditLog` AOP 自动记录，敏感字段（password、token、apiKey）自动脱敏

## 8. 数据库设计

### ER 关系概览

```
USER ──1:N──▶ SPACE ──1:N──▶ PICTURE ──1:0..1──▶ FILE_RESOURCE
  │                            │
  ├──1:N──▶ PICTURE            ├──1:N──▶ PICTURE_SHARE
  │                            │
  ├──1:N──▶ TASK               └──M:N──▶ PICTURE_TAG
  │
  └──M:N──▶ SPACE_TEAM_MEMBER (via SPACE)
```

### 关键约束

| 表 | 约束 | 说明 |
|-----|------|------|
| `user` | `username` UNIQUE | 用户名唯一 |
| `user` | `nickname` UNIQUE | 昵称唯一 |
| `file_resource` | `(md5, size)` UNIQUE | 文件去重联合唯一 |
| `file_resource` | `ref_count >= 0` | 引用计数非负 |
| `space_team_member` | `(space_id, user_id)` UNIQUE | 空间内用户唯一 |
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
  ├── GET /user/checkCode/login ──▶ 生成验证码 → 存 Redis
  │◀── captchaKey + base64 ───────┤
  │                               │
  ├── POST /user/login ──────────▶ 校验验证码 → 校验密码(BCrypt)
  │                               │  → 签发 JWT → 存 LoginContext 到 Redis
  │◀── UserVO + JWT ─────────────┤
  │                               │
  ├── Authorization: Bearer JWT ─▶ TokenRefreshInterceptor 解析
  │                               │  → 超 15 分钟续签
  │                               │  → LoginInterceptor 校验
  │                               │  → UserHolder 设置用户
```

### 9.2 图片上传流程

**普通上传**：

```
客户端                           服务端
  │                               │
  ├── upload(file) ──────────────▶ 计算 MD5
  │                               │  → 查询 file_resource
  │                               │
  │                               ├── [命中] refCount++ → 写 picture
  │                               ├── [未命中] 上传 COS → 写 file_resource → 写 picture
  │                               │
  │◀── PictureVO ────────────────┤
```

**分片上传**：

```
客户端                           服务端
  │                               │
  │  前端计算 MD5 (SparkMD5)      │
  │                               │
  ├── check(md5, size) ──────────▶ 查询 file_resource
  │                               │
  │  [秒传命中]                    │
  │◀── PictureVO ────────────────┤
  │                               │
  │  [需上传]                      │
  │◀── 已上传分片列表 ────────────┤
  │                               │
  ├── upload-chunk (逐片) ────────▶ 存储分片
  │                               │
  ├── merge(md5, size, cosKey) ──▶ 合并分片 → 去重 → 写 picture
  │◀── PictureVO ────────────────┤
```

### 9.3 AI 任务流程

```
客户端                           服务端                           AI 服务
  │                               │                               │
  ├── POST /ai/tags 或            │                               │
  │   /ai/draw/submit ──────────▶ 创建 Task(PENDING)              │
  │◀── taskId ───────────────────┤  → 分发到线程池                  │
  │                               │                               │
  │                    Worker CAS 抢占 → PROCESSING                │
  │                               │                               │
  │                               ├── 调用 AI 服务 ────────────────▶│
  │                               │◀── 返回结果 ──────────────────┤
  │                               │                               │
  │                    更新 Task.result → DONE                     │
  │                               │                               │
  │  SSE: result ────────────────┤                               │
  │                               │                               │
  │  [或] GET /ai/tags/result ───▶ 返回 TaskVO                    │
```

### 9.4 协同编辑流程

```
客户端 A                         服务端                          客户端 B
  │                               │                               │
  ├── WS Connect(token,spaceId) ─▶ 验证 JWT + 空间权限             │
  │                               │  → 注册会话                    │
  │                               │◀── WS Connect(token,spaceId) ─┤
  │                               │                               │
  ├── lock(pictureId) ──────────▶ 锁定图片                        │
  │                               │  → 广播 lock 到其他客户端 ────▶│
  │                               │                               │
  ├── transform(pictureId,data) ─▶ 广播 transform ──────────────▶│
  │                               │                               │
  ├── unlock(pictureId) ─────────▶ 释放锁                         │
  │                               │  → 广播 unlock ──────────────▶│
  │                               │                               │
  │                    [客户端 B 断连]                              │
  │                               │  → 自动释放 B 的所有锁          │
  │                               │  → 广播 unlock ──────────────▶│
```

## 10. 安全设计

### XSS 防御

- 用户输入通过 Jsoup 白名单过滤，仅允许安全 HTML 标签
- 分享预览 / 下载接口强制 Content-Type 为 `image/*`
- 前端输出使用 React 默认的 JSX 转义

### 认证安全

- BCrypt 密码哈希（自适应强度）
- JWT 签名密钥 ≥ 32 字节
- 登出即时 Token 黑名单
- 封禁用户全局 Token 失效
- 验证码防暴力破解

### 请求安全

- `SecurityHeaderFilter` 注入安全响应头（CSP、X-Content-Type-Options 等）
- 接口限流（`@RateLimiter`）
- IP 检测与记录

### 数据安全

- 审计日志自动脱敏（password、token、apiKey 等字段）
- 敏感配置通过环境变量注入，不硬编码
- `application-local.yml` 纳入 `.gitignore`
