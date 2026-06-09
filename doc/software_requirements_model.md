# FishPics 后端软件需求模型

> 本文档基于 `src/FishPics-backend` 当前后端项目，覆盖用户认证、图片管理与分片上传、文件去重、图片分享、空间协作、系统配置、AI 能力与后台治理等需求。接口统一挂载在 `/api` 上，具体控制器路径来自 `controller` 包。

## 1. 项目定位

FishPics 是一个图片素材协作平台。后端负责用户认证与权限管理、图片上传（含分片上传与文件去重）、空间容量管理、图片分享、系统配置、AI 图片处理任务和后台治理。

### 1.1 目标

- 支持用户注册登录、个人资料维护。
- 支持图片普通上传与分片上传（含秒传校验和断点续传）、文件去重、空间归档、公开展示、审核和信息编辑。
- 支持图片分享，可生成带过期时间和下载权限控制的分享链接，支持在线预览和下载。
- 支持私人空间与团队空间的图片资产协作管理，团队成员角色通过整数 `roleId` 区分（1=所有者，2=成员）。
- 支持管理员对用户、图片、空间、系统配置和 AI 任务进行管理。
- 支持已登录用户使用 AI 标注、文生图能力（异步任务模式），VIP 等级校验在 Service 层执行。

### 1.2 技术栈

| 层面 | 技术 |
| --- | --- |
| 运行环境 | Java 21 |
| Web 框架 | Spring Boot 3.3.0, Spring MVC |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | MySQL 8, 数据库名 `FishPics` |
| 缓存 | Redis, Caffeine 本地缓存, 多级缓存架构 |
| 认证 | JWT（jjwt），Redis 黑名单管理 |
| API 文档 | Knife4j OpenAPI3 |
| 工具库 | Hutool, Lombok |
| 对象存储 | 腾讯云 COS |
| 消息队列 | RocketMQ |
| AI | Spring AI Alibaba 1.1.2.3, DashScope SDK 2.22.18, 通义千问视觉理解与万相图像能力 |

### 1.3 运行与配置

- 服务端口：`8080`
- Servlet 上下文：`/api`
- Knife4j 扫描包：`hk.ljx.fishpicsbackend`（各模块 Controller 分布在 `user`、`picture`、`space`、`system`、`ai` 包下）
- 上传限制：单文件最大 `25MB`，单次请求最大 `100MB`
- RocketMQ：`task-topic` 主题（AI 异步任务），`audit-log-topic` 主题（审计日志异步写入）
- MySQL、Redis、COS、DashScope 密钥通过 `application-local.yml` 或本地配置注入。

## 2. 架构与公共机制

### 2.1 后端分层

| 层级 | 包 | 职责 |
| --- | --- | --- |
| Controller | `controller` | HTTP 入参校验、调用服务、统一响应 |
| Service | `service`, `service.impl` | 业务规则、权限和状态流转 |
| Mapper | `mapper` | MyBatis-Plus 数据访问 |
| DTO | `dto` | 请求参数模型 |
| VO | `vo` | 响应视图模型 |
| Entity | `entity` | 数据库表映射 |
| Common | `common` | 响应、异常、拦截器、AOP、常量、COS、配置 |
| Task | `task` | 异步任务消费、处理器和消息模型 |
| AI | `ai` | AI 能力接口、Spring AI Alibaba Agent 和 DashScope SDK 实现 |

### 2.2 统一响应

所有接口返回 `Response<T>`：

| 字段 | 含义 |
| --- | --- |
| `code` | 业务状态码，成功由 `ResUtils.success` 生成 |
| `message` | 提示信息 |
| `data` | 响应数据 |

业务异常通过 `ExcUtils.throwIfTrue` 抛出，由全局异常处理器统一转换为响应。

### 2.3 认证与权限

- 登录成功后签发 JWT（HMAC-SHA 密钥，30 分钟有效期），通过 `Authorization: Bearer <token>` 请求头传递。
- JWT 黑名单通过 Redis 管理，登出时将 `jti` 加入黑名单，TTL 与 JWT 剩余有效期一致。
- `TokenRefreshInterceptor`（order=0）解析 JWT，检查黑名单、用户封禁和禁用状态，从 Redis 读取 `LoginContext`（通过 `StringRedisTemplate` 直接操作，不经过 `MultiLevelCacheManager` 的 Caffeine L1），超过 15 分钟自动续签并通过 `X-New-Token` 响应头返回新 JWT。`LoginContext` 在 Redis 中的 TTL 为 7 天，采用惰性续期策略（TTL 低于一半即 3.5 天时才刷新），减少 Redis 操作。
- `LoginInterceptor`（order=1）检查 ThreadLocal 中是否存在有效 `LoginContext`，拦截未登录请求。
- 权限体系采用简化 RBAC：通过 `user.level` 字段判断用户等级（`0` 普通、`1` VIP、`2` SVIP、`3` 管理员），管理端接口通过 `@RequireAdmin` 注解 + AOP 切面（`AdminInterceptor`）校验管理员权限（`level >= 3`）。
- 审计日志：通过 `@AuditLog(module, operation)` 注解自动记录操作日志，由 `AuditLogAspect` 切面拦截，通过 RocketMQ 异步写入 `sys_audit_log` 表（MQ 失败时降级为同步写 DB）。
- 当前用户通过 `UserHolder`（ThreadLocal 存储 `LoginContext`）在线程内传递。

### 2.4 状态约定

| 对象 | 字段 | 约定 |
| --- | --- | --- |
| 用户 | `status` | `1` 正常，`0` 禁用 |
| 图片 | `status` | `1` 正常，`0` 禁用，`2` 待审核 |
| 图片 | `is_private` | `0` 公开，`1` 私有 |
| 图片 | `is_selected` | `0` 普通，`1` 精选 |
| 空间 | `status` | `1` 正常，`0` 禁用 |
| AI 任务 | `status` | `PENDING` 待处理，`PROCESSING` 处理中，`DONE` 成功，`FAILED` 失败 |
| 用户等级 | `level` | `0` 普通，`1` VIP，`2` SVIP，`3` 管理员 |
| 空间类型 | `type` | `0` 私人空间，`1` 团队空间 |
| 分享 | `status` | `1` 有效，`0` 已取消 |
| 去重文件 | `ref_count` | 引用计数，`>= 0`，归零时可清理物理文件 |

## 3. 功能需求

### 3.1 用户认证与资料

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 登录 | `POST /api/user/login` | 用户名、密码、登录验证码校验，成功返回 `UserVO` 和 JWT |
| 注册 | `POST /api/user/register` | 校验用户名、密码、确认密码、注册验证码，注册后自动创建私人空间 |
| 注册验证码 | `GET /api/user/checkCode/register` | 返回 `captchaKey` 和 Base64 图片 |
| 登录验证码 | `GET /api/user/checkCode/login` | 返回 `captchaKey` 和 Base64 图片 |
| 当前用户详情 | `GET /api/user/myself` | 返回用户详细资料 |
| 当前登录用户 | `GET /api/user/getUser` | 从 `LoginContext` 返回当前用户基础信息和权限列表 |
| 编辑本人资料 | `POST /api/user/editUser` | 修改昵称、用户名、密码（需原密码验证） |
| 退出登录 | `POST /api/user/logout` | 将 JWT 加入 Redis 黑名单，清除 ThreadLocal 和 Redis 中的权限上下文 |
| 用户主页 | `GET /api/user/profile` | 查询指定用户资料，公开接口，无需登录 |
| 用户搜索 | `GET /api/user/search` | 按用户名或昵称模糊搜索，需登录 |
| 管理员查询用户 | `POST /api/user/admin/getUser` | 根据 `userId` 查询用户详情，需管理员权限 |
| 管理员用户列表 | `POST /api/user/admin/userList` | 分页查询用户列表，支持筛选，需管理员权限 |
| 管理员用户状态变更 | `POST /api/user/admin/setStatus` | 切换用户启用/禁用状态，需管理员权限 |
| 管理员编辑用户 | `POST /api/user/admin/editUser` | 管理员修改用户资料，需管理员权限 |

### 3.2 图片管理

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 上传头像 | `POST /api/picture/avatar` | `multipart/form-data`，头像文件最大 5MB，支持指定 `targetUserId`（管理员可为其他用户上传） |
| 上传图片 | `POST /api/picture/upload` | 上传到指定 `targetSpaceId`，未传默认私人空间 |
| URL 保存图片 | `POST /api/picture/save-by-url` | 通过 URL 下载图片，校验魔数后上传 COS |
| 秒传校验 | `POST /api/picture/check` | 根据文件 MD5 和大小检查是否已存在，支持秒传和断点续传检测 |
| 分片上传 | `POST /api/picture/upload-chunk` | 上传单个分片，参数为文件、MD5 和分片序号 |
| 合并分片 | `POST /api/picture/merge` | 合并已上传的分片为完整文件，写入去重表和图片记录 |
| 公开图片列表 | `POST /api/picture/list` | 分页返回 `status=1` 且 `is_private=0` 的首页公开图片，入参为 `PictureQueryRequest` 支持按标签筛选 |
| 图片推荐 | `POST /api/picture/recommend` | 基于标签匹配的图片推荐 |
| 图片编辑信息 | `GET /api/picture/pictureEditMessage` | 返回 `PictureVO`，包含图片编辑所需数据 |
| 删除图片 | `POST /api/picture/delete` | 请求体为 `DeleteByIdList`，支持批量删除，同时删除 COS 文件并更新去重引用计数 |
| 更新图片信息 | `PUT /api/picture/update` | 修改图片名称、简介和标签 |
| 管理员图片列表 | `POST /api/picture/admin/list` | 按状态分页查询 |
| 管理员审核图片 | `POST /api/picture/admin/review` | 修改图片状态和首页公开标记，支持设置精选状态 |

图片元数据保存在 `picture` 表，物理文件通过 `file_resource` 表实现去重（MD5 + 文件大小唯一），`picture.resource_id` 关联去重记录。分片上传流程：秒传校验 -> 逐片上传 -> 合并分片，分片临时存储在 COS 的 `chunks/` 目录下。

### 3.3 图片分享

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 创建分享链接 | `POST /api/share/create` | 生成分享 Token，可设置过期天数和是否允许下载 |
| 获取分享信息 | `GET /api/share/info/{token}` | 无需登录，根据 Token 获取分享的图片信息，校验过期和状态 |
| 分享预览 | `GET /api/share/preview/{token}` | 无需登录，直接返回图片流，仅允许图片 MIME 类型，防止 XSS |
| 分享下载 | `GET /api/share/download/{token}` | 无需登录，以附件形式返回图片文件，需允许下载权限 |
| 取消分享 | `POST /api/share/cancel` | 分享者取消分享，将状态置为 `0` |

分享记录存储在 `picture_share` 表，通过 `share_token`（UUID）标识唯一分享链接，支持过期时间控制和下载权限管理。`/share/info/*`、`/share/preview/*`、`/share/download/*` 路径从登录拦截器中排除，支持匿名访问。

### 3.4 空间管理

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 创建空间 | `POST /api/space/create` | 创建私人或团队空间 |
| 空间列表 | `GET /api/space/list` | 按 `type` 查询当前用户可访问空间 |
| 空间详情 | `GET /api/space/getSpace` | 查询空间详情 |
| 更新空间 | `POST /api/space/update` | 修改名称和介绍 |
| 空间图片列表 | `POST /api/space/pictureList` | 分页返回空间内图片，支持关键词搜索和排序 |
| 可上传空间 | `GET /api/space/saveable` | 返回当前用户可上传图片的空间列表（私人空间 + 有上传权限的团队空间） |
| 团队成员列表 | `GET /api/space/team/members` | 查询团队空间成员 |
| 邀请团队成员 | `POST /api/space/team/invite` | 邀请用户加入团队空间，需指定 `roleId` |
| 移除团队成员 | `POST /api/space/team/remove` | 从团队空间移除成员 |
| 变更团队角色 | `POST /api/space/team/changeRole` | 变更团队成员角色（`roleId`：1=所有者，2=成员） |
| 管理员空间列表 | `POST /api/space/admin/list` | 按名称、类型分页筛选 |
| 管理员更新空间 | `POST /api/space/admin/update` | 修改空间配置 |
| 管理员删除空间 | `POST /api/space/admin/delete` | 删除指定空间 |
| 管理员设置状态 | `POST /api/space/admin/setStatus` | 启用或禁用空间 |

空间容量字段为 `storage_size` 和 `size`。创建空间时按用户等级和空间类型分配容量：

| 用户等级 | 私人空间 | 团队空间 |
| --- | --- | --- |
| 普通 (level=0) | 512MB | 512MB |
| VIP (level=1) | 50GB | 50GB |
| SVIP (level=2) | 100GB | 100GB |

团队空间数量上限：普通用户 1 个，VIP 2 个，SVIP 5 个。上传文件大小限制：普通用户 10MB，VIP 1GB，SVIP 10GB。团队成员角色通过 `space_team_member` 表的 `role_id` 整数字段管理（1=所有者，2=成员）。

### 3.5 系统配置

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 分类标签列表 | `GET /api/system/list` | 返回系统图片分类（`syskey = type_list_key`） |
| 添加分类标签 | `POST /api/system/addList` | 管理员追加标签 |
| 删除分类标签 | `POST /api/system/deleteType` | 管理员删除标签 |
| 跑马灯列表 | `GET /api/system/marquee` | 返回首页轮播图片 URL（`syskey = marquees_key`） |
| 添加跑马灯 | `POST /api/system/addMarquee` | 管理员追加 URL |
| 删除跑马灯 | `POST /api/system/deleteMarquee` | 管理员删除 URL |
| 审计日志列表 | `POST /api/system/audit-log/list` | 管理员分页查询审计日志 |
| 系统统计概览 | `GET /api/system/stats` | 管理员获取系统统计数据 |

配置持久化在 `pic_system` 表（键值对结构：`syskey` + `sysvalue`），同时通过 Redis 做热点读取。当前已知的配置键：`type_list_key`（图片分类标签 JSON 数组）、`marquees_key`（跑马灯 URL 列表）、`ai_config`（AI 功能开关 JSON）。审计日志通过 RocketMQ 异步写入（`audit-log-topic`），避免阻塞主业务链路；MQ 不可用时降级为同步写 DB。

### 3.6 AI 能力

| 功能 | 接口 | 权限 | 说明 |
| --- | --- | --- | --- |
| 提交图片标注任务 | `POST /api/ai/tags` | 需登录 | 提交异步标注任务，返回 `taskId`（VIP 等级校验在 Service 层执行） |
| 查询标注结果 | `GET /api/ai/tags/result/{taskId}` | 需登录 | 查询标注任务结果（仅可查看本人任务） |
| 提交文生图任务 | `POST /api/ai/draw/submit` | 需登录 | 提交异步文生图任务，返回 `taskId`（VIP 等级校验在 Service 层执行） |
| 查询文生图结果 | `GET /api/ai/draw/result/{taskId}` | 需登录 | 查询文生图任务结果（仅可查看本人任务） |
| 下载 AI 生成图片 | `GET /api/ai/download-image/{taskId}` | 需登录 | 下载文生图结果图片（流式响应，最大 50MB） |
| 管理员任务列表 | `POST /api/ai/admin/tasks` | 管理员 | 分页查询 AI 任务，支持按类型和状态筛选 |
| 管理员任务统计 | `GET /api/ai/admin/stats` | 管理员 | 返回任务总数、成功/失败/处理中数量、按类型统计 |
| 获取 AI 配置 | `GET /api/ai/admin/config` | 管理员 | 获取 AI 功能开关配置（`syskey = ai_config`） |
| 更新 AI 配置 | `POST /api/ai/admin/config` | 管理员 | 更新 AI 功能开关（标注/编辑/生成/推荐），使用 `ReentrantLock` 保证并发安全 |

AI 能力采用**异步任务模式**：标注和文生图任务提交后立即返回 `taskId`，任务通过 RocketMQ 异步处理。`TaskConsumer` 消费消息时通过条件 UPDATE 原子抢占任务（PENDING -> PROCESSING），防止并发重复处理；处理超过 5 分钟未完成的任务允许被其他消费者重新抢占。任务结果持久化在 `task` 表中，前端通过轮询结果接口获取。Controller 层仅检查登录态和任务归属权，VIP 等级等业务权限校验在 Service 层执行。管理员可通过后台查看任务列表、统计和配置 AI 功能开关。

## 4. 数据需求

### 4.1 核心数据表

| 表 | 实体 | 说明 |
| --- | --- | --- |
| `user` | `User` | 用户账户、资料和等级（`level` 字段标识权限级别） |
| `space` | `Space` | 私人空间、团队空间和容量 |
| `space_team_member` | `SpaceTeamMember` | 团队空间成员关系，`role_id` 整数字段（1=所有者，2=成员） |
| `picture` | `Picture` | 图片元数据、状态、空间、精选标记和 AI 标签 |
| `file_resource` | `FileResource` | 物理文件去重记录（MD5 + 大小唯一），引用计数管理 |
| `picture_share` | `PictureShare` | 图片分享链接，含过期时间和下载权限控制 |
| `pic_system` | `PicSystem` | 系统键值配置（`syskey` + `sysvalue`） |
| `task` | `Task` | 异步任务（AI 标注、文生图等），含状态流转和结果 |
| `sys_audit_log` | `SysAuditLog` | 审计操作日志 |

### 4.2 关键关系

- 一个用户可以创建多个图片和空间。
- 一个空间可以拥有多张图片。
- 一个图片通过 `resource_id` 关联一条去重文件记录，多张相同内容的图片可共享同一物理文件（引用计数 `ref_count` 管理生命周期）。
- 一个图片可以生成多个分享链接，每个链接有独立的过期时间和下载权限。
- 一个团队空间可以有多个成员，成员通过 `space_team_member` 关联，每个成员 `role_id` 为 1（所有者）或 2（成员）。
- 一个异步任务属于一个用户，可关联一个业务对象（图片）。

### 4.3 重要索引

- 用户：`uk_username`、`uk_nickname`
- 图片：`idx_user_id`、`idx_space_id`、`idx_picture_name`、`idx_introduction`、`idx_status`、`idx_update_time`
- 去重文件：`uk_md5_size`（MD5 + 大小联合唯一）
- 分享：`uk_share_token`、`idx_picture_id`、`idx_share_user_id`、`idx_expire_time`
- 团队成员：`uk_space_user`（空间 + 用户联合唯一）
- 异步任务：`uk_task_id`、`idx_user_id`、`idx_biz_type`、`idx_status`
- 审计日志：`idx_user_id`、`idx_create_time`、`idx_operation`

## 5. 非功能需求

### 5.1 安全

- 登录、资料、空间、图片管理、分享、AI 等用户态操作需要有效 JWT。
- 管理端接口统一通过 `@RequireAdmin` 注解控制，基于 `user.level >= 3` 校验。
- JWT 支持自动续签（超过 15 分钟签发新 Token）和黑名单机制（登出即时失效）。
- 封禁用户通过 Redis 集合（`banned:users`）实现即时全 Token 失效。
- 用户密码使用 MD5 加盐哈希方案保存。
- 文件上传需要校验空文件、大小、存储空间和业务权限。
- 分片上传通过 MD5 校验文件完整性，秒传校验避免重复上传。
- 分享预览接口限制 Content-Type 为 `image/*`，防止 XSS 攻击。
- 审计日志记录请求参数时自动脱敏（password、token、apiKey 等字段），并截断过长内容（最大 1000 字符）。

### 5.2 性能

- 分页查询使用 MyBatis-Plus 分页能力。
- 多级缓存架构（`MultiLevelCacheManager`）：Caffeine L1（热数据本地缓存）+ Redis L2，覆盖用户信息（L1: 30s / L2: 60min）、权限上下文（L1: 300s / L2: 60min）和系统配置（L1: 600s / L2: 1440min）。该缓存用于业务层的热点数据读取。
- `TokenRefreshInterceptor` 中的 JWT 权限上下文（`LoginContext`）通过 `StringRedisTemplate` 直接操作 Redis（TTL 7 天），不经过 `MultiLevelCacheManager` 的 Caffeine L1 层。采用惰性续期策略（TTL 低于一半时才刷新）减少 Redis 操作。
- 文件去重通过 `file_resource` 表的 MD5 + 大小联合唯一索引实现，相同文件只存储一份物理副本。
- 秒传校验可在上传前快速判断文件是否已存在，避免不必要的网络传输。
- 分片上传支持大文件传输，降低单次上传失败的重试成本。
- 图片文件存储在 COS，数据库只保存元数据和 URL。
- 审计日志通过 RocketMQ 异步写入（Producer 发送到 `audit-log-topic`，Consumer 持久化到 DB），避免阻塞主业务链路；MQ 发送失败时自动降级为同步写 DB。

### 5.3 可维护性

- 控制器保持薄层，业务规则落在 Service。
- 表模型与实体类一一对应，DTO/VO 隔离入参与出参。
- AI 能力通过 `TaskHandler` 接口与具体实现隔离，`TaskConsumer` 通过 `bizType` 自动路由到对应 Handler，便于扩展新的任务类型。
- 权限判断逻辑集中在 `user.level` 字段和 `@RequireAdmin` 注解，避免分散的权限校验代码。
- Knife4j 提供开发期接口文档。

## 6. 主要业务流程

### 6.1 登录流程

1. 前端请求 `/user/checkCode/login` 获取验证码图片和 `captchaKey`。
2. 用户提交用户名、密码、验证码和 `captchaKey` 到 `/user/login`。
3. 服务端校验验证码、用户状态和密码。
4. 签发 JWT，将用户信息写入 Redis 缓存。
5. 返回 `UserVO`，前端后续请求携带 `Authorization: Bearer <token>`。
6. `TokenRefreshInterceptor` 在每次请求时解析 JWT、检查黑名单、加载 `LoginContext` 到 ThreadLocal，超过 15 分钟自动续签。`LoginContext` 从 Redis 直接读取（`StringRedisTemplate`），不经过 Caffeine L1。

### 6.2 图片上传流程

**普通上传：**

1. 用户上传文件到 `/picture/upload`（可指定 `targetSpaceId`，未传默认私人空间）。
2. 服务端校验文件大小、空间容量，计算文件 MD5。
3. 查询 `file_resource` 表判断是否为重复文件：若已存在则增加引用计数，复用已有 COS 文件；若不存在则上传到 COS 并创建去重记录。
4. 创建 `picture` 记录，关联 `resource_id`。

**分片上传：**

1. 前端调用 `/picture/check` 提交文件 MD5 和大小，服务端检查是否可秒传（文件已存在）或需要续传（返回已上传分片列表）。
2. 若需上传，前端逐片调用 `/picture/upload-chunk` 上传分片，分片临时存储在 COS 的 `chunks/` 目录。
3. 全部分片上传完成后，前端调用 `/picture/merge` 提交合并请求。
4. 服务端合并分片为完整文件，执行去重逻辑，创建 `picture` 记录并清理临时分片。

### 6.3 图片分享流程

1. 图片所有者调用 `/share/create`，指定图片 ID、过期天数和是否允许下载。
2. 服务端生成 UUID 分享 Token，创建 `picture_share` 记录。
3. 分享链接形如 `/share/info/{token}`，无需登录即可访问。
4. 访问时服务端校验 Token 有效性、过期时间和分享状态，返回图片信息。
5. 通过 `/share/preview/{token}` 可直接预览图片（流式返回图片内容，仅允许图片 MIME 类型）。
6. 通过 `/share/download/{token}` 可下载图片（需分享链接允许下载）。
7. 分享者可随时调用 `/share/cancel` 取消分享。

### 6.4 AI 调用流程

1. 已登录用户调用 `/ai/tags`（提交标注任务）或 `/ai/draw/submit`（提交文生图任务）。Controller 层仅检查登录态，VIP 等级校验在 Service 层执行。
2. 服务端创建 `task` 记录（状态 PENDING），通过 RocketMQ 发送异步消息到 `task-topic`。
3. `TaskConsumer` 消费消息，通过条件 UPDATE 原子抢占任务（PENDING -> PROCESSING），根据 `bizType` 分发到对应 `TaskHandler`。
4. 标签识别：`AiTagTaskHandler` 通过 Spring AI Alibaba Agent + 通义千问视觉理解模型分析图片，更新图片标签并标记任务完成（DONE）。
5. 文生图：`AiDrawTaskHandler` 通过 DashScope SDK 调用万相模型生成图片，保存 URL 并标记任务完成（DONE）。
6. 任务失败时记录错误信息，DashScope 审核类错误自动转换为友好提示。
7. 前端通过 `/ai/tags/result/{taskId}` 或 `/ai/draw/result/{taskId}` 轮询结果（仅可查看本人任务）。
8. 文生图结果可通过 `/ai/download-image/{taskId}` 下载（流式响应，最大 50MB）。
9. 管理员可通过 `/ai/admin/tasks`、`/ai/admin/stats` 和 `/ai/admin/config` 管理 AI 任务和配置。

## 7. 后续维护说明

- 本文档应优先跟随 `controller`、`entity`、`src/sql/init.sql`、DTO 和 `common` 包变化更新。
- 若数据库结构调整，必须同步更新 `model/uml_diagrams.md` 中的类图和 ER 图。
- 若新增接口，需在功能需求和接口矩阵中补齐路径、权限和输入输出。
- `common` 包中的 `cache/MultiLevelCacheManager`（多级缓存）、`config/RocketMQConfig`、`aop/AdminInterceptor`（权限切面）、`aop/AuditLogAspect`（审计日志切面）、`interceptor/TokenRefreshInterceptor`（JWT 拦截器）如有更新需同步至本文档。
- `@RequireAdmin` 注解和 `@AuditLog` 注解如有变更需同步更新权限和安全需求。
- 文件去重逻辑（`file_resource` 表的引用计数管理）和分片上传流程如有变更需同步更新业务流程章节。
- `SpaceConstants` 中的空间容量和上传限制如有调整需同步更新空间管理章节。
- `SysConstants` 中的配置键如有变更需同步更新系统配置章节。
