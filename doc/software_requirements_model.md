# FishPics 后端软件需求模型

> 本文档基于 `src/FishPics-backend` 当前后端项目重构，覆盖用户、图片、帖子、评论、空间、系统配置、AI 能力与后台管理等需求。接口统一挂载在 `/api` 上，具体控制器路径来自 `controller` 包。

## 1. 项目定位

FishPics 是一个图片素材协作与社区平台。后端负责用户认证、图片上传与空间容量管理、帖子发布与互动、评论审核、社交关系、系统配置、AI 图片处理任务和后台治理。

### 1.1 目标

- 支持用户注册登录、个人资料维护和隐私设置。
- 支持图片上传、空间归档、公开展示、审核和信息编辑。
- 支持帖子发布、编辑、浏览、点赞、收藏、评论和热度排序。
- 支持私人空间与团队空间的图片资产管理。
- 支持管理员对用户、图片、帖子、评论、空间、系统配置和 AI 任务进行管理。
- 支持 VIP/SVIP 用户使用 AI 标注、编辑、生成和推荐能力。

### 1.2 技术栈

| 层面 | 技术 |
| --- | --- |
| 运行环境 | Java 21 |
| Web 框架 | Spring Boot 3.3.0, Spring MVC |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | MySQL 8, 数据库名 `FishPics` |
| 缓存 | Redis, Redisson |
| API 文档 | Knife4j OpenAPI3 |
| 工具库 | Hutool, Lombok |
| 对象存储 | 腾讯云 COS |
| AI | Spring AI Alibaba 1.1.2.0, DashScope, 通义千问视觉理解与万相图像能力 |

### 1.3 运行与配置

- 服务端口：`8080`
- Servlet 上下文：`/api`
- Knife4j 扫描包：`hk.ljx.fishpicsbackend`（各模块 Controller 分布在 `user`、`picture`、`post`、`comment`、`space`、`system`、`ai` 包下）
- 上传限制：单文件最大 `10MB`，单次请求最大 `100MB`
- MySQL、Redis、COS、DashScope 密钥通过 `application-local.yml` 或本地配置注入。

## 2. 架构与公共机制

### 2.1 后端分层

| 层级 | 包 | 职责 |
| --- | --- | --- |
| Controller | `controller` | HTTP 入参校验、调用服务、统一响应 |
| Service | `service`, `service.impl` | 业务规则、权限和状态流转 |
| Mapper | `mapper`, `ai.mapper` | MyBatis-Plus 数据访问 |
| DTO | `dto`, `ai.dto` | 请求参数模型 |
| VO | `vo`, `vo.ai` | 响应视图模型 |
| Entity | `entity` | 数据库表映射 |
| Common | `common` | 响应、异常、拦截器、AOP、常量、COS、Redis Stream |
| AI | `ai` | AI 能力接口、任务服务和阿里云提供方适配 |

### 2.2 统一响应

所有接口返回 `Response<T>`：

| 字段 | 含义 |
| --- | --- |
| `code` | 业务状态码，成功由 `ResUtils.success` 生成 |
| `message` | 提示信息 |
| `data` | 响应数据 |

业务异常通过 `ExcUtils.throwIfTrue` 抛出，由全局异常处理器统一转换为响应。

### 2.3 认证与权限

- 登录成功后生成 UUID Token，并通过 `Authorization` 请求头传递。
- Redis 保存 Token 与用户 ID、用户信息缓存。
- `RefreshTokenInterceptor` 读取 Token，恢复当前用户到 `UserHolder`。
- `LoginInterceptor` 校验需要登录的请求。
- 管理员接口使用 `@AuthCheck(role = ADMIN)`，由 `AuthInterceptor` AOP 校验角色。
- 当前用户通过 `UserHolder` 在线程内传递。

### 2.4 状态约定

| 对象 | 字段 | 约定 |
| --- | --- | --- |
| 用户 | `status` | `1` 正常，`0` 禁用，`2` 待审核 |
| 图片 | `status` | `1` 正常，`0` 禁用，`2` 待审核 |
| 帖子 | `status` | `1` 正常，`0` 禁用，`2` 待审核 |
| 评论 | `status` | `1` 正常，`0` 禁用，`2` 待审核 |
| 空间 | `status` | `1` 正常，`0` 禁用 |
| AI 任务 | `status` | `0` 处理中，`1` 成功，`2` 失败 |
| 用户等级 | `level` | `0` 普通，`1` VIP，`2` SVIP |
| 空间类型 | `type` | `0` 私人空间，`1` 团队空间 |

## 3. 功能需求

### 3.1 用户认证与资料

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 登录 | `POST /api/user/login` | 用户名、密码、登录验证码校验，成功返回 `UserLoginVO` 和 Token |
| 注册 | `POST /api/user/register` | 校验用户名、密码、确认密码、注册验证码，注册后创建用户 |
| 注册验证码 | `GET /api/user/checkCode/register` | 返回 `captchaKey` 和 Base64 图片 |
| 登录验证码 | `GET /api/user/checkCode/login` | 返回 `captchaKey` 和 Base64 图片 |
| 当前用户主页 | `GET /api/user/myself` | 返回用户资料、本人帖子、收藏和点赞列表 |
| 当前登录用户 | `GET /api/user/getUser` | 从 `UserHolder` 返回当前用户基础信息 |
| 编辑本人资料 | `POST /api/user/editUser` | 修改用户名、密码、头像、邮箱、手机号、昵称 |
| 退出登录 | `POST /api/user/logout` | 清除当前线程用户并删除 Redis Token 映射 |
| 隐私设置 | `POST /api/user/privacy` | 更新关注、粉丝、收藏、点赞可见性 |
| 用户主页 | `GET /api/user/profile` | 查询指定用户资料，当前拦截器未放行该路径，需要登录态 |

### 3.2 社交关系

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 关注/取消关注 | `POST /api/user/follow` | 请求体为 `UserIdRequest`，由 `user_fans` 记录关系 |
| 粉丝列表 | `GET /api/user/fans` | 支持查询自己或指定用户粉丝，当前拦截器未放行该路径 |
| 关注列表 | `GET /api/user/follows` | 支持查询自己或指定用户关注，当前拦截器未放行该路径 |

隐私字段位于 `user` 表：`is_private_follows`、`is_private_post_collect`、`is_private_likes`、`is_private_fans`。

### 3.3 图片管理

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 上传头像 | `POST /api/picture/avatar` | `multipart/form-data`，头像文件最大 5MB |
| 上传图片 | `POST /api/picture/upload` | 上传到指定 `targetSpaceId`，未传默认私人空间 |
| 公开图片列表 | `GET /api/picture/list` | 分页返回 `status=1` 且 `is_private=1` 的首页公开图片 |
| 删除图片 | `DELETE /api/picture/delete` | 请求体为 `DeleteByIdList`，支持批量删除 |
| 更新图片信息 | `PUT /api/picture/update` | 修改图片名称和简介 |
| 管理员图片列表 | `GET /api/picture/admin/list` | 按状态分页查询 |
| 管理员审核图片 | `POST /api/picture/admin/review` | 修改图片状态和首页公开标记，`selected` 实际写入 `is_private` |

图片元数据保存在 `picture` 表，帖子与图片的有序关系由 `picture_child` 表维护。当前 SQL 中 `picture` 表不含 `post_id` 字段。

### 3.4 帖子与互动

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 发布帖子 | `POST /api/post/post` | 请求体为 `UploadPostRequest`，绑定图片 ID 列表和封面 |
| 帖子详情 | `GET /api/post/getPost` | 返回帖子详情、图片、作者、互动数据；当前接口被拦截器放行，Service 仅按 ID 查询，未强制校验 `status` 或 `is_private` |
| 编辑帖子 | `POST /api/post/editPost` | 作者更新标题、内容、图片、封面和隐私 |
| 帖子列表 | `POST /api/post/postList` | 支持分页、文本搜索、用户筛选、热门排序；当前默认筛选 `status=1`，不默认筛选 `is_private=0` |
| 点赞/取消点赞 | `POST /api/post/like` | 返回点赞后的状态 |
| 收藏/取消收藏 | `POST /api/post/collect` | 返回收藏后的状态 |
| 空间图片选择 | `POST /api/post/pictureList` | 发帖或编辑时按空间获取可选图片 |
| 本人帖子 | `POST /api/post/myPosts` | 分页返回当前用户发布内容 |
| 本人收藏 | `POST /api/post/myCollects` | 分页返回当前用户收藏内容 |
| 本人点赞 | `POST /api/post/myLikes` | 分页返回当前用户点赞内容 |
| 管理员帖子列表 | `POST /api/post/admin/list` | 后台分页查询 |
| 管理员审核帖子 | `POST /api/post/admin/review` | 修改帖子状态 |
| 管理员删除帖子 | `POST /api/post/admin/delete` | 后台删除帖子 |

帖子统计字段包括 `likes_num`、`collects_num`、`comment_num`、`views_num`、`hot`。点赞使用 Redisson 锁降低并发重复操作风险。

### 3.5 评论

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 创建评论 | `POST /api/comment/create` | 支持一级评论与回复 |
| 评论列表 | `POST /api/comment/list` | 按帖子分页查询正常评论 |
| 删除评论 | `POST /api/comment/delete` | 当前用户删除评论 |
| 管理员评论列表 | `POST /api/comment/admin/list` | 后台分页查询 |
| 评论审核 | `POST /api/comment/review` | 管理员修改评论状态 |
| 管理员删除评论 | `POST /api/comment/adminDelete` | 后台删除评论 |

评论通过 `parent_id` 和 `to_user_id` 表示二级回复关系。

### 3.6 空间管理

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 创建空间 | `POST /api/space/create` | 创建私人或团队空间 |
| 空间列表 | `GET /api/space/list` | 按 `type` 查询当前用户可访问空间 |
| 空间详情 | `GET /api/space/getSpace` | 查询空间详情 |
| 更新空间 | `POST /api/space/update` | 修改名称和介绍 |
| 空间图片列表 | `POST /api/space/pictureList` | 分页返回空间内图片 |
| 管理员空间列表 | `GET /api/space/admin/list` | 按名称、类型分页筛选 |
| 管理员更新空间 | `POST /api/space/admin/update` | 修改空间配置 |
| 管理员删除空间 | `POST /api/space/admin/delete` | 删除指定空间 |
| 管理员设置状态 | `POST /api/space/admin/setStatus` | 启用或禁用空间 |

空间容量字段为 `storage_size` 和 `size`。创建空间时按用户等级和空间类型分配容量：私人空间普通/VIP/SVIP 为 512MB/5GB/10GB，团队空间普通/VIP/SVIP 为 512MB/30GB/50GB。团队成员以 `team_users_id` 字符串保存。

### 3.7 系统配置

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 分类标签列表 | `GET /api/system/list` | 返回系统图片分类 |
| 添加分类标签 | `POST /api/system/addList` | 管理员追加标签 |
| 删除分类标签 | `POST /api/system/deleteType` | 管理员删除标签 |
| 跑马灯列表 | `GET /api/system/marquee` | 返回首页轮播图片 URL |
| 添加跑马灯 | `POST /api/system/addMarquee` | 管理员追加 URL |
| 删除跑马灯 | `POST /api/system/deleteMarquee` | 管理员删除 URL |

配置持久化在 `pic_system` 表，同时通过 Redis 做热点读取。

### 3.8 AI 能力

| 功能 | 接口 | 权限 | 说明 |
| --- | --- | --- | --- |
| 图片标注 | `POST /api/ai/tags` | VIP/SVIP | 提交指定图片自动标签任务 |
| 图片编辑 | `POST /api/ai/edit` | VIP/SVIP | 提交抠图、风格迁移等编辑任务 |
| 图片生成 | `POST /api/ai/generate` | VIP/SVIP | 根据提示词生成图片 |
| 图片推荐 | `POST /api/ai/recommend` | VIP/SVIP | 根据参考图片推荐素材 |
| 任务详情 | `GET /api/ai/task/{id}` | `/ai/**` 白名单 | 查询 AI 任务结果，Controller 当前只校验任务存在 |
| 我的任务 | `GET /api/ai/task/my` | VIP/SVIP | 分页查询本人 AI 任务 |
| 任务管理 | `GET /api/ai/admin/tasks` | 管理员 | 按类型、状态、用户筛选 |
| AI 统计 | `GET /api/ai/admin/stats` | 管理员 | 查看任务统计 |
| AI 配置查询 | `GET /api/ai/admin/config` | 管理员 | 查看模型配置 |
| AI 配置更新 | `POST /api/ai/admin/config` | 管理员 | 更新模型开关与参数 |

AI 任务记录在 `ai_task` 表：`type` 区分标注、编辑、生成、推荐；`input_data` 和 `output_data` 保存 JSON；失败原因写入 `error_msg`。

## 4. 数据需求

### 4.1 核心数据表

| 表 | 实体 | 说明 |
| --- | --- | --- |
| `user` | `User` | 用户账户、资料、隐私和等级 |
| `space` | `Space` | 私人空间、团队空间和容量 |
| `picture` | `Picture` | 图片元数据、状态、空间和 AI 标签 |
| `picture_child` | `PictureChild` | 帖子与图片的有序关联 |
| `post` | `Post` | 帖子正文、状态、统计、封面 |
| `comment` | `Comment` | 评论和二级回复 |
| `user_fans` | `UserFans` | 用户关注/粉丝关系 |
| `user_post_collect` | `UserPostCollect` | 用户收藏帖子 |
| `user_post_likes` | `UserPostLikes` | 用户点赞帖子 |
| `pic_system` | `PicSystem` | 系统键值配置 |
| `ai_task` | `AiTask` | AI 异步任务 |

### 4.2 关键关系

- 一个用户可以创建多个帖子、图片、评论和空间。
- 一个帖子可以拥有多张图片，关系和顺序由 `picture_child` 保存。
- 一个空间可以拥有多张图片。
- 一个帖子可以被多个用户点赞、收藏和评论。
- 一个用户可以关注多个用户，也可以被多个用户关注。
- 一个 AI 任务属于一个用户，可关联一张图片。

### 4.3 重要索引

- 用户：`uk_username`、`uk_nickname`
- 帖子：`idx_user_id`、`idx_title`、`post_content_index`、`post_status_index`
- 图片：`idx_user_id`、`idx_picture_name`、`picture_space_id_index`、`picture_tags_index`、`picture_update_time_index`
- 图片关联：`picture_child_picture_id_post_id_uindex`
- 评论：`idx_post_id`、`idx_user_id`
- AI 任务：`ai_task_user_id_index`、`ai_task_type_index`、`ai_task_status_index`、`ai_task_picture_id_index`

## 5. 非功能需求

### 5.1 安全

- 登录、资料、空间、发帖、互动、AI 等用户态操作需要 Token。
- 管理端接口统一通过 `@AuthCheck(role = ADMIN)` 控制。
- 用户密码使用项目内加盐哈希方案保存。
- 文件上传需要校验空文件、大小、存储空间和业务权限。

### 5.2 性能

- 分页查询使用 MyBatis-Plus 分页能力。
- 分类标签、跑马灯、验证码、Token 和用户信息使用 Redis。
- 点赞等高并发互动使用 Redisson 锁保护关键更新。
- 图片文件存储在 COS，数据库只保存元数据和 URL。

### 5.3 可维护性

- 控制器保持薄层，业务规则落在 Service。
- 表模型与实体类一一对应，DTO/VO 隔离入参与出参。
- AI 能力通过接口与 Provider 实现隔离，便于替换模型供应商。
- Knife4j 提供开发期接口文档。

## 6. 主要业务流程

### 6.1 登录流程

1. 前端请求 `/user/checkCode/login` 获取验证码图片和 `captchaKey`。
2. 用户提交用户名、密码、验证码和 `captchaKey` 到 `/user/login`。
3. 服务端校验验证码、用户状态和密码。
4. 生成 Token，写入 Redis。
5. 返回 `UserLoginVO`，前端后续请求携带 `Authorization`。

### 6.2 发帖流程

1. 用户上传图片到 `/picture/upload`，图片进入目标空间。
2. 前端提交标题、内容、图片 ID 列表、封面和隐私配置。
3. 服务端创建 `post` 记录。
4. 服务端写入 `picture_child`，保存图片顺序。
5. 列表和详情接口聚合帖子、图片、作者和互动数据。

### 6.3 AI 任务流程

1. VIP/SVIP 用户提交标注、编辑、生成或推荐请求。
2. 服务端创建 `ai_task`，状态为处理中。
3. 异步处理器调用 AI Provider。
4. 成功时写入 `output_data` 和状态成功；失败时写入 `error_msg` 和状态失败。
5. 用户或管理员通过任务接口查询状态和结果。

## 7. 后续维护说明

- 本文档应优先跟随 `controller`、`entity`、`src/sql/create.sql` 和 AI DTO 变化更新。
- 若数据库结构调整，必须同步更新 `model/uml_diagrams.md` 中的类图和 ER 图。
- 若新增接口，需在功能需求和接口矩阵中补齐路径、权限和输入输出。
