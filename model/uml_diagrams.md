# FishPics 后端 UML 与数据模型

> 本文档基于当前 `src/FishPics-backend` 后端代码重构，重点描述参与者、用例、类结构、实体关系、请求流和 AI 任务流。

## 1. 用例模型

### 1.1 参与者

| 参与者 | 说明 |
| --- | --- |
| 访客 | 可查看公开配置、获取验证码、注册、登录、浏览公开内容 |
| 登录用户 | 可维护个人资料、上传图片、管理空间、发布帖子、互动、评论、关注、搜索用户 |
| VIP/SVIP 用户 | 继承登录用户能力，并可使用 AI 标注和文生图能力（异步任务模式） |
| 管理员 | 可管理用户、图片、帖子、评论、空间、系统配置、AI 任务和配置、审计日志 |
| AI Provider | DashScope / 阿里云模型服务，执行视觉理解和图像生成 |
| COS 对象存储 | 保存头像和图片文件 |
| Redis | 保存验证码、Token、用户缓存、系统配置缓存、多级缓存 L2 |
| Caffeine | 本地缓存 L1，覆盖用户信息、权限、帖子列表和系统配置 |
| MySQL | 保存业务主数据 |
| RocketMQ | 异步任务消息队列 |
| WebSocket | 实时任务完成通知（支持跨实例 Redis Pub/Sub） |

### 1.2 用例图

```mermaid
flowchart LR
    Guest["访客"]
    User["登录用户"]
    Vip["VIP/SVIP 用户"]
    Admin["管理员"]
    Redis["Redis"]
    COS["腾讯云 COS"]
    AI["AI Provider"]
    DB["MySQL"]
    MQ["RocketMQ"]
    WS["WebSocket"]

    Guest --> UC1["获取验证码"]
    Guest --> UC2["注册"]
    Guest --> UC3["登录"]
    Guest --> UC4["浏览公开图片/帖子/系统配置"]

    User --> UC5["编辑个人资料与隐私"]
    User --> UC6["上传头像/图片"]
    User --> UC7["创建和管理空间"]
    User --> UC8["发布/编辑帖子"]
    User --> UC9["点赞/收藏/评论"]
    User --> UC10["关注/查看粉丝"]
    User --> UC22["搜索用户"]
    User --> UC23["URL 保存图片"]
    User --> UC24["查看推荐内容"]

    Vip --> UC11["提交 AI 标注任务"]
    Vip --> UC12["提交 AI 文生图任务"]
    Vip --> UC13["查询任务结果"]

    Admin --> UC14["用户管理"]
    Admin --> UC15["图片审核"]
    Admin --> UC16["帖子审核/删除"]
    Admin --> UC17["评论审核/删除"]
    Admin --> UC18["空间管理"]
    Admin --> UC19["系统配置管理"]
    Admin --> UC20["AI 任务管理"]
    Admin --> UC21["AI 配置管理"]
    Admin --> UC25["审计日志查询"]
    Admin --> UC26["系统统计概览"]

    UC1 --> Redis
    UC3 --> Redis
    UC6 --> COS
    UC11 --> MQ
    UC12 --> MQ
    UC13 --> WS
    UC2 --> DB
    UC7 --> DB
    UC8 --> DB
    UC9 --> DB
    UC14 --> DB
```

### 1.3 权限矩阵

| 模块 | 访客 | 登录用户 | VIP/SVIP | 管理员 |
| --- | --- | --- | --- | --- |
| 验证码、注册、登录 | 是 | 是 | 是 | 是 |
| 公开图片、帖子、系统配置查询 | 是 | 是 | 是 | 是 |
| 当前用户、资料、隐私、退出 | 否 | 是 | 是 | 是 |
| 上传图片、空间、发帖、互动、评论 | 否 | 是 | 是 | 是 |
| 提交 AI 标注/文生图任务、查询任务结果 | 否 | 否 | 是 | 是 |
| 后台管理接口（用户、图片、帖子、评论、空间、系统） | 否 | 否 | 否 | 是 |
| AI 后台管理（任务列表、统计、配置） | 否 | 否 | 否 | 是 |

## 2. 领域类图

```mermaid
classDiagram
    class User {
        Long id
        String username
        String password
        String avatar
        String email
        String phone
        String nickname
        Integer status
        Integer isDelete
        String role
        Date createTime
        Date updateTime
        Long likeNum
        Long collectNum
        Integer isPrivateFollows
        Integer isPrivatePostCollect
        Integer isPrivateLikes
        Integer isPrivateFans
        Integer level
    }

    class Space {
        Long id
        String introduction
        Integer type
        String teamUsersId
        Long userId
        Long storageSize
        Integer level
        String name
        Long size
        Integer status
    }

    class Picture {
        Long id
        Long userId
        String pictureName
        String url
        String width
        String height
        Long size
        Integer status
        Date createTime
        Date updateTime
        Integer isPrivate
        Long spaceId
        String introduction
        String tags
        String type
    }

    class PictureChild {
        Long id
        Long pictureId
        Long postId
        Integer sortNum
    }

    class Post {
        Long id
        Long userId
        String title
        String content
        Integer status
        Date createTime
        Date updateTime
        Integer isDelete
        Long likesNum
        Long collectsNum
        Integer commentNum
        Integer isPrivate
        Long cover
        Long viewsNum
        Integer hot
    }

    class Comment {
        Long id
        Long userId
        Long postId
        String content
        Long parentId
        Integer toUserId
        Integer status
        Date createTime
    }

    class UserFans {
        Long id
        Long userId
        Long fanId
    }

    class UserPostCollect {
        Long id
        Long userId
        Long postId
    }

    class UserPostLikes {
        Long id
        Long userId
        Long postId
    }

    class UserInterestProfile {
        Long id
        Long userId
        String tag
        Integer weight
        Date createTime
        Date updateTime
    }

    class PicSystem {
        Long id
        String syskey
        String sysvalue
    }

    class Task {
        Long id
        String taskId
        Long userId
        String bizType
        String bizId
        String status
        String param
        String result
        String errorMsg
        Date createTime
        Date updateTime
    }

    class SpaceTeamMember {
        Long id
        Long spaceId
        Long userId
        Long roleId
        Date joinedAt
    }

    class SysRole {
        Long id
        String code
        String name
        Integer scope
        Integer isSystem
        Long inheritRoleId
        Date createTime
        Date updateTime
        Integer isDelete
    }

    class SysPermission {
        Long id
        String code
        String name
        String module
        Integer scope
        Integer sortOrder
        Date createTime
        Date updateTime
        Integer isDelete
    }

    class SysRolePermission {
        Long id
        Long roleId
        Long permissionId
    }

    class SysUserRole {
        Long id
        Long userId
        Long roleId
    }

    class SysAuditLog {
        Long id
        Long userId
        String username
        String operation
        String module
        String detail
        String method
        String url
        String params
        Integer result
        String errorMsg
        String ip
        LocalDateTime createTime
        Integer isDelete
    }

    User "1" --> "0..*" Space : creates
    User "1" --> "0..*" Picture : uploads
    User "1" --> "0..*" Post : publishes
    User "1" --> "0..*" Comment : writes
    User "1" --> "0..*" Task : submits
    User "1" --> "1" UserInterestProfile : has profile
    Space "1" --> "0..*" Picture : contains
    Post "1" --> "0..*" PictureChild : ordered images
    Picture "1" --> "0..*" PictureChild : used by posts
    Post "1" --> "0..*" Comment : has
    Comment "0..1" --> "0..*" Comment : replies
    Post "1" --> "0..*" UserPostCollect : collected by
    Post "1" --> "0..*" UserPostLikes : liked by
    User "1" --> "0..*" UserFans : target user
    User "1" --> "0..*" UserPostCollect : collects
    User "1" --> "0..*" UserPostLikes : likes
    Picture "0..1" --> "0..*" Task : processed by
    UserInterestProfile "1" --> "0..*" tags : contains
    Space "1" --> "0..*" SpaceTeamMember : has members
    User "1" --> "0..*" SpaceTeamMember : joins
    SysRole "1" --> "0..*" SpaceTeamMember : assigned to
    SysRole "1" --> "0..*" SysRolePermission : has permissions
    SysPermission "1" --> "0..*" SysRolePermission : granted to roles
    User "1" --> "0..*" SysUserRole : has roles
    SysRole "1" --> "0..*" SysUserRole : assigned to users
    SysRole "0..1" --> "0..*" SysRole : inherits from
    User "1" --> "0..*" SysAuditLog : generates
```

## 3. 数据库 ER 图

```mermaid
erDiagram
    USER ||--o{ SPACE : creates
    USER ||--o{ PICTURE : uploads
    USER ||--o{ POST : publishes
    USER ||--o{ COMMENT : writes
    USER ||--o{ TASK : submits
    USER ||--o| USER_INTEREST_PROFILE : has
    USER ||--o{ USER_FANS : has_fans
    USER ||--o{ USER_POST_COLLECT : collects
    USER ||--o{ USER_POST_LIKES : likes
    USER ||--o{ SPACE_TEAM_MEMBER : joins
    USER ||--o{ SYS_USER_ROLE : has_roles
    USER ||--o{ SYS_AUDIT_LOG : generates

    SPACE ||--o{ PICTURE : stores
    SPACE ||--o{ SPACE_TEAM_MEMBER : has_members
    POST ||--o{ PICTURE_CHILD : has_ordered_images
    PICTURE ||--o{ PICTURE_CHILD : belongs_to_posts
    POST ||--o{ COMMENT : has
    COMMENT ||--o{ COMMENT : replies
    POST ||--o{ USER_POST_COLLECT : collected
    POST ||--o{ USER_POST_LIKES : liked
    PICTURE ||--o{ TASK : processed_by

    SYS_ROLE ||--o{ SPACE_TEAM_MEMBER : assigned_to
    SYS_ROLE ||--o{ SYS_ROLE_PERMISSION : has_permissions
    SYS_PERMISSION ||--o{ SYS_ROLE_PERMISSION : granted_to_roles
    SYS_ROLE ||--o{ SYS_USER_ROLE : assigned_to_users
    SYS_ROLE ||--o{ SYS_ROLE : inherits_from

    USER {
        bigint id PK
        varchar username UK
        varchar password
        varchar avatar
        varchar email
        varchar phone
        varchar nickname UK
        tinyint status
        tinyint is_delete
        varchar role
        datetime create_time
        datetime update_time
        bigint like_num
        bigint collect_num
        tinyint is_private_follows
        tinyint is_private_post_collect
        tinyint is_private_likes
        tinyint is_private_fans
        tinyint level
    }

    SPACE {
        bigint id PK
        varchar introduction
        tinyint type
        varchar team_users_id
        bigint user_id
        bigint storage_size
        tinyint level
        varchar name
        bigint size
        tinyint status
    }

    PICTURE {
        bigint id PK
        bigint user_id
        varchar picture_name
        varchar url
        varchar width
        varchar height
        bigint size
        tinyint status
        datetime create_time
        datetime update_time
        tinyint is_private
        bigint space_id
        varchar introduction
        varchar tags
        varchar type
    }

    PICTURE_CHILD {
        bigint id PK
        bigint picture_id
        bigint post_id
        int sort_num
    }

    POST {
        bigint id PK
        bigint user_id
        varchar title
        text content
        tinyint status
        datetime create_time
        datetime update_time
        int is_delete
        bigint likes_num
        bigint collects_num
        int comment_num
        tinyint is_private
        bigint cover
        bigint views_num
        int hot
    }

    COMMENT {
        bigint id PK
        bigint user_id
        bigint post_id
        text content
        bigint parent_id
        bigint to_user_id
        tinyint status
        datetime create_time
    }

    USER_FANS {
        bigint id PK
        bigint user_id
        bigint fan_id
    }

    USER_POST_COLLECT {
        bigint id PK
        bigint user_id
        bigint post_id
        datetime create_time
    }

    USER_POST_LIKES {
        bigint id PK
        bigint user_id
        bigint post_id
        datetime create_time
    }

    USER_INTEREST_PROFILE {
        bigint id PK
        bigint user_id UK
        varchar tag
        int weight
        datetime create_time
        datetime update_time
    }

    PIC_SYSTEM {
        bigint id PK
        varchar syskey UK
        varchar sysvalue
    }

    TASK {
        bigint id PK
        varchar task_id UK
        bigint user_id
        varchar biz_type
        varchar biz_id
        varchar status
        text param
        text result
        text error_msg
        datetime create_time
        datetime update_time
    }

    SPACE_TEAM_MEMBER {
        bigint id PK
        bigint space_id
        bigint user_id
        bigint role_id
        datetime joined_at
    }

    SYS_ROLE {
        bigint id PK
        varchar code UK
        varchar name
        tinyint scope
        tinyint is_system
        bigint inherit_role_id
        datetime create_time
        datetime update_time
        tinyint is_delete
    }

    SYS_PERMISSION {
        bigint id PK
        varchar code UK
        varchar name
        varchar module
        tinyint scope
        int sort_order
        datetime create_time
        datetime update_time
        tinyint is_delete
    }

    SYS_ROLE_PERMISSION {
        bigint id PK
        bigint role_id
        bigint permission_id
    }

    SYS_USER_ROLE {
        bigint id PK
        bigint user_id
        bigint role_id
    }

    SYS_AUDIT_LOG {
        bigint id PK
        bigint user_id
        varchar username
        varchar operation
        varchar module
        varchar detail
        varchar method
        varchar url
        text params
        tinyint result
        varchar error_msg
        varchar ip
        datetime create_time
        tinyint is_delete
    }
```

## 4. 控制器接口模型

### 4.1 UserController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/user/login` | `UserLoginRequest` | `UserLoginVO` | 公开 |
| `POST` | `/user/register` | `UserRequestRequest` | `Boolean` | 公开 |
| `GET` | `/user/checkCode/register` | - | `CheckCodeVO` | 公开 |
| `GET` | `/user/checkCode/login` | - | `CheckCodeVO` | 公开 |
| `GET` | `/user/myself` | - | `UserMessageVO` | 登录 |
| `GET` | `/user/getUser` | - | `UserLoginVO` | 登录 |
| `POST` | `/user/editUser` | `UserEditRequest` | `Boolean` | 登录 |
| `POST` | `/user/logout` | `Authorization` | 空响应 | 登录 |
| `POST` | `/user/privacy` | `UserPrivacyRequest` | `Boolean` | 登录 |
| `POST` | `/user/follow` | `UserIdRequest` | `Boolean` | 登录 |
| `POST` | `/user/fans` | `FollowQueryDTO` | `IPage<FollowUserVO>` | 登录 |
| `POST` | `/user/follows` | `FollowQueryDTO` | `IPage<FollowUserVO>` | 登录 |
| `GET` | `/user/profile` | `userId` | `UserPublicProfileVO` | 公开 |
| `GET` | `/user/search` | `keyword` | `List<UserSearchVO>` | 登录 |
| `POST` | `/user/admin/getUser` | `UserIdRequest` | `AdminGetUserVO` | `user:list` |
| `POST` | `/user/admin/userList` | `UserQueryWrapper` | `IPage<User>` | `user:list` |
| `POST` | `/user/admin/setStatus` | `UserIdRequest` | `Boolean` | `user:status` |
| `POST` | `/user/admin/editUser` | `UserEditByAdminRequest` | `Boolean` | `user:manage` |

### 4.2 PictureController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/picture/avatar` | `file,id` | `String` | 登录 |
| `POST` | `/picture/upload` | `file,targetSpaceId?` | `PictureListVO` | 登录 |
| `POST` | `/picture/save-by-url` | `SavePictureByUrlRequest` | `PictureListVO` | 登录 |
| `POST` | `/picture/list` | `PictureQueryRequest` | `IPage<PictureListVO>` | 公开 |
| `POST` | `/picture/recommend` | `PageRequest` | `IPage<PictureListVO>` | 登录 |
| `DELETE` | `/picture/delete` | `DeleteByIdList` | `String` | 登录 |
| `PUT` | `/picture/update` | `PictureUpdateRequest` | `Boolean` | 登录 |
| `GET` | `/picture/pictureEditMessage` | `id` | `PictureEditVO` | 登录 |
| `POST` | `/picture/admin/list` | `AdminPictureListDTO` | `IPage<PictureAdminVO>` | `picture:list` |
| `POST` | `/picture/admin/review` | `ReviewPictureDTO` | `Boolean` | `picture:review`；`selected` 写入 `is_private`，表示是否公开到首页 |

### 4.3 PostController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/post/post` | `UploadPostRequest` | `Boolean` | 登录 |
| `GET` | `/post/getPost` | `id` | `PostDetailVO` | 白名单路径；原子递增 `views_num` |
| `POST` | `/post/editPost` | `EditPostRequest` | `Boolean` | 作者 |
| `POST` | `/post/postList` | `PostQueryRequest` | `IPage<PostListVO>` | 白名单路径；默认筛 `status=1`，多级缓存 + 分布式锁 |
| `POST` | `/post/like` | `id` | `Boolean` | 登录；Redisson 分布式锁 |
| `POST` | `/post/collect` | `id` | `Boolean` | 登录；Redisson 分布式锁 |
| `POST` | `/post/pictureList` | `GetPictureBySpaceRequest` | `Map<String,Object>` | 登录 |
| `POST` | `/post/myPosts` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/myCollects` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/myLikes` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/recommend` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/admin/list` | `PostQueryRequest` | `IPage<PostListVO>` | `post:list` |
| `POST` | `/post/admin/review` | `ReviewPostDTO` | `Boolean` | `post:review` |
| `POST` | `/post/admin/delete` | `id` | `Boolean` | `post:delete` |

### 4.4 CommentController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/comment/create` | `CreateCommentRequest` | `Long` | 登录；XSS 过滤，status=待审核 |
| `POST` | `/comment/list` | `CommentQueryRequest` | `IPage<CommentVO>` | 公开；非管理员仅见已审核评论 |
| `POST` | `/comment/delete` | `id` | `Boolean` | 登录；软删除 |
| `POST` | `/comment/admin/list` | `CommentQueryRequest` | `IPage<CommentVO>` | `comment:list` |
| `POST` | `/comment/review` | `ReviewCommentDTO` | `Boolean` | `comment:review` |
| `POST` | `/comment/adminDelete` | `id` | `Boolean` | `comment:delete`；硬删除含子回复 |

### 4.5 SpaceController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/space/create` | `CreateSpace` | `Boolean` | 登录 |
| `GET` | `/space/list` | `type` | `List<SpaceVO>` | 登录 |
| `GET` | `/space/getSpace` | `id` | `SpaceVO` | 登录/成员 |
| `POST` | `/space/update` | `UpdateSpace` | `Boolean` | 拥有者/管理员 |
| `POST` | `/space/pictureList` | `SpacePictureList` | `PicturePageVO` | 登录/成员 |
| `GET` | `/space/saveable` | - | `List<SpaceVO>` | 登录 |
| `GET` | `/space/team/members` | `spaceId` | `List<SpaceMemberVO>` | 登录/成员 |
| `POST` | `/space/team/invite` | `TeamInviteRequest` | `Boolean` | `team:member_manage` |
| `POST` | `/space/team/remove` | `TeamRemoveRequest` | `Boolean` | `team:member_manage` |
| `POST` | `/space/team/changeRole` | `TeamChangeRoleRequest` | `Boolean` | `team:member_manage` |
| `POST` | `/space/admin/list` | `SpaceQueryWrapper` | `IPage<SpaceVO>` | `space:list` |
| `POST` | `/space/admin/update` | `SpaceAdminUpdateRequest` | `Boolean` | `space:manage` |
| `POST` | `/space/admin/delete` | `SpaceDeleteRequest` | `Boolean` | `space:manage` |
| `POST` | `/space/admin/setStatus` | `SpaceSetStatusRequest` | `Boolean` | `space:status` |

### 4.6 SystemController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `GET` | `/system/list` | - | `List<String>` | 公开 |
| `POST` | `/system/addList` | `AddSysPicType` | `Boolean` | `system:type` |
| `POST` | `/system/deleteType` | `DeleteTypeRequest` | `Boolean` | `system:type` |
| `GET` | `/system/marquee` | - | `List<String>` | 公开 |
| `POST` | `/system/addMarquee` | `AddSysMarquee` | `Boolean` | `system:marquee` |
| `POST` | `/system/deleteMarquee` | `DeleteMarqueeRequest` | `Boolean` | `system:marquee` |
| `POST` | `/system/audit-log/list` | `AuditLogQueryRequest` | `IPage<SysAuditLog>` | `user:manage` |
| `GET` | `/system/stats` | - | `SystemStatsVO` | `user:manage` |

### 4.8 PermissionController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `GET` | `/permission/roles` | - | `List<SysRole>` | `user:manage` |

### 4.7 AiController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/ai/tags` | `IdRequest` | `AiTaskSubmitVO` | VIP/SVIP |
| `GET` | `/ai/tags/result/{taskId}` | `taskId` | `Task` | VIP/SVIP |
| `POST` | `/ai/draw` | `AiDrawPictureDTO` | `String` | VIP/SVIP |
| `POST` | `/ai/draw/submit` | `AiDrawPictureDTO` | `AiTaskSubmitVO` | VIP/SVIP |
| `GET` | `/ai/draw/result/{taskId}` | `taskId` | `Task` | VIP/SVIP |
| `POST` | `/ai/admin/tasks` | `AiTaskQueryDTO` | `IPage<AiTaskVO>` | 管理员 |
| `GET` | `/ai/admin/stats` | - | `AiStatsVO` | 管理员 |
| `GET` | `/ai/admin/config` | - | `AiConfigDTO` | 管理员 |
| `POST` | `/ai/admin/config` | `AiConfigDTO` | `Boolean` | 管理员 |

注：`/ai/**` 路径在 `MvcConfig` 中被排除在登录拦截器之外。AI 能力采用异步任务模式，任务通过 RocketMQ 消费，结果持久化在 `task` 表中。

## 5. 关键流程图

### 5.1 登录鉴权流程

```mermaid
sequenceDiagram
    participant C as Client
    participant U as UserController
    participant S as UserService
    participant R as Redis
    participant I as RefreshTokenInterceptor
    participant H as UserHolder

    C->>U: GET /user/checkCode/login
    U->>R: 写入验证码
    U-->>C: captchaKey + base64Image
    C->>U: POST /user/login
    U->>S: 校验验证码/用户/密码
    S->>R: 保存 token->userId 与 userId->user
    S-->>C: UserLoginVO(token)
    C->>I: 后续请求携带 Authorization
    I->>R: 读取 userId 与用户缓存
    I->>H: 写入当前用户
```

### 5.2 发帖与图片关联流程

```mermaid
sequenceDiagram
    participant C as Client
    participant P as PictureController
    participant PS as PictureService
    participant COS as COS
    participant PostC as PostController
    participant PostS as PostService
    participant DB as MySQL

    C->>P: POST /picture/upload(file,targetSpaceId)
    P->>PS: uploadPicture
    PS->>COS: 上传文件
    PS->>DB: 写入 picture
    PS-->>C: PictureListVO(id,url)
    C->>PostC: POST /post/post(imageId,title,content,cover,isPrivate)
    PostC->>PostS: uploadPost
    PostS->>DB: 写入 post
    PostS->>DB: 写入 picture_child(picture_id,post_id,sort_num)
    PostS-->>C: true
```

### 5.3 点赞/收藏流程

```mermaid
flowchart TD
    A["用户点击点赞或收藏"] --> B{"校验登录与帖子存在"}
    B --> C{"是否已有关系记录"}
    C -->|有| D["删除 user_post_likes / user_post_collect"]
    C -->|无| E["新增关系记录"]
    D --> F["扣减帖子统计数"]
    E --> G["增加帖子统计数"]
    F --> H["返回当前状态 false"]
    G --> I["返回当前状态 true"]
```

### 5.4 AI 调用流程

```mermaid
sequenceDiagram
    participant C as Client
    participant A as AiController
    participant S as AiService
    participant MQ as RocketMQ
    participant H as TaskHandler
    participant AI as AI Provider
    participant WS as WebSocket

    C->>A: POST /ai/tags(IdRequest)
    A->>S: submitTagTask(pictureId)
    S->>S: 创建 Task(PENDING)
    S->>MQ: 发送 ai_tag 消息
    S-->>C: AiTaskSubmitVO(taskId)

    MQ->>H: AiTagTaskHandler 消费
    H->>AI: ReactAgent + 通义千问视觉理解
    AI-->>H: 名称/描述/标签
    H->>S: 更新图片标签，标记 Task(DONE)
    S->>WS: 推送任务完成通知
    WS-->>C: 实时通知

    C->>A: GET /ai/tags/result/{taskId}
    A->>S: getTagResult(taskId)
    S-->>C: Task(含结果)

    C->>A: POST /ai/draw/submit(AiDrawPictureDTO)
    A->>S: submitDrawTask(dto, userId)
    S->>S: 创建 Task(PENDING)
    S->>MQ: 发送 ai_draw 消息
    S-->>C: AiTaskSubmitVO(taskId)

    MQ->>H: AiDrawTaskHandler 消费
    H->>AI: DashScope + 万相图像生成
    AI-->>H: 图片 URL
    H->>S: 标记 Task(DONE)
    S->>WS: 推送任务完成通知
    WS-->>C: 实时通知

    C->>A: GET /ai/draw/result/{taskId}
    A->>S: getDrawResult(taskId)
    S-->>C: Task(含结果)
```

## 6. DTO/VO 模型摘要

### 6.1 主要请求 DTO

| DTO | 主要字段 | 使用场景 |
| --- | --- | --- |
| `UserLoginRequest` | `username,password,checkCode,captchaKey` | 登录 |
| `UserRequestRequest` | `username,password,checkPassword,checkCode,captchaKey` | 注册 |
| `UserEditRequest` | `id,username,password,avatar,email,phone,nickname` | 编辑本人资料 |
| `UserPrivacyRequest` | 隐私开关字段 | 更新隐私 |
| `UserQueryWrapper` | 用户筛选与分页字段 | 后台用户查询 |
| `UploadPostRequest` | `imageId,title,content,cover,isPrivate` | 发布帖子 |
| `EditPostRequest` | `id,imageId,title,content,cover,isPrivate` | 编辑帖子 |
| `PostQueryRequest` | `userId,text,updateTime,hotPost,current,pageSize` | 帖子分页查询 |
| `CreateCommentRequest` | `postId,content,parentId,toUserId` | 创建评论 |
| `CommentQueryRequest` | 评论分页与筛选字段 | 评论列表 |
| `CreateSpace` | `name,introduction,type` | 创建空间 |
| `UpdateSpace` | `id,name,introduction` | 更新空间 |
| `SpacePictureList` | `spaceId,current,pageSize` | 空间图片列表 |
| `SpaceQueryWrapper` | 空间筛选与分页 | 后台空间列表 |
| `SpaceAdminUpdateRequest` | 空间后台可改字段 | 后台空间编辑 |
| `SpaceDeleteRequest` | `id` | 后台删除空间 |
| `SpaceSetStatusRequest` | `id,status` | 后台设置空间状态 |
| `PictureQueryRequest` | `tag,current,pageSize` | 图片列表查询 |
| `PictureUpdateRequest` | `ids,pictureName,introduction,pictureUrl` | 图片信息编辑 |
| `DeleteByIdList` | `ids` | 批量删除图片 |
| `AddSysPicType` | `value` | 添加分类标签 |
| `AddSysMarquee` | `url` | 添加跑马灯 |
| `DeleteTypeRequest` | `value` | 删除分类标签 |
| `DeleteMarqueeRequest` | `url` | 删除跑马灯 |
| `SavePictureByUrlRequest` | `url,targetSpaceId` | URL 保存图片 |
| `AdminPictureListDTO` | `status,current,pageSize` | 管理员图片列表 |
| `ReviewPictureDTO` | `pictureId,status,selected` | 管理员审核图片 |
| `ReviewPostDTO` | `id,status` | 管理员审核帖子 |
| `ReviewCommentDTO` | `id,status` | 管理员审核评论 |
| `FollowQueryDTO` | `userId,current,pageSize` | 粉丝/关注列表查询 |
| `TeamInviteRequest` | `spaceId,userId,roleId` | 邀请团队成员 |
| `TeamRemoveRequest` | `spaceId,userId` | 移除团队成员 |
| `TeamChangeRoleRequest` | `spaceId,userId,roleId` | 变更团队角色 |
| `AuditLogQueryRequest` | `current,pageSize+filters` | 审计日志查询 |
| `AiDrawPictureDTO` | `description,exclusion,style,size` | AI 文生图 |
| `AiTaskQueryDTO` | `type,status,current,pageSize` | AI 任务查询 |
| `AiConfigDTO` | `taggingEnabled,editingEnabled,generationEnabled,recommendationEnabled` | AI 功能配置 |
| `PageRequest` | `current,pageSize,sortField,sortOrder` | 通用分页查询 |

### 6.2 主要响应 VO

| VO | 用途 |
| --- | --- |
| `UserLoginVO` | 登录态用户信息、Token 和权限列表 |
| `UserMessageVO` | 个人主页聚合信息 |
| `UserPublicProfileVO` | 用户公开主页（含关注/粉丝/帖子数） |
| `UserSearchVO` | 用户搜索结果（id、昵称、头像） |
| `FollowUserVO` | 粉丝/关注列表项 |
| `CheckCodeVO` | 验证码图片与 key |
| `PictureListVO` | 图片列表基础项 |
| `PictureAdminVO` | 后台图片管理项 |
| `PictureEditVO` | 图片编辑信息 |
| `PicturePageVO` | 空间图片分页 |
| `PostListVO` | 帖子列表项（含作者信息和收藏状态） |
| `PostDetailVO` | 帖子详情聚合（含图片列表、互动状态） |
| `CommentVO` | 评论展示项（含嵌套回复和用户信息） |
| `SpaceVO` | 空间展示项（含图片数、成员列表） |
| `SpaceMemberVO` | 团队成员展示项（含角色信息） |
| `SystemStatsVO` | 系统统计概览 |
| `AiPictureMessage` | AI 标注结果（名称、描述、标签列表） |
| `AiTaskSubmitVO` | AI 任务提交返回（taskId, status） |
| `AiTaskVO` | AI 任务详情（管理后台） |
| `AiStatsVO` | AI 任务统计（管理后台） |
| `AdminGetUserVO` | 管理员查看用户详情（含角色 ID 列表） |

## 7. 状态与约束

### 7.1 状态字段

| 模型 | 字段 | 值 |
| --- | --- | --- |
| `User` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Picture` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Post` | `status` | `0` 草稿，`1` 已发布，`2` 待审核，`3` 已拒绝 |
| `Comment` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Space` | `status` | `0` 禁用，`1` 正常 |
| `Task` | `status` | `PENDING` 待处理，`PROCESSING` 处理中，`DONE` 成功，`FAILED` 失败 |
| `Task` | `bizType` | `ai_tag` 标注任务，`ai_draw` 文生图任务 |

### 7.2 关键约束

- `user.username`、`user.nickname` 唯一。
- `pic_system.syskey` 唯一。
- `picture_child` 对 `picture_id + post_id` 建立唯一索引。
- `user_interest_profile` 对 `user_id + tag` 建立唯一索引。
- `task.task_id` 唯一索引。
- `sys_role.code` 唯一。
- `sys_permission.code` 唯一。
- 帖子图片顺序必须依赖 `picture_child.sort_num`。
- `picture.is_private` 当前承担首页公开标记含义：`0` 不公开到首页，`1` 公开到首页；管理员图片审核接口的 `selected` 参数会写入该字段。
- `picture.tags` 存储 AI 标签，格式为 JSON 数组（如 `["人物","风景"]`）。
- `picture.type` 存储图片格式类型，`create.sql` 中已包含该列。
- 普通用户不能调用 AI 能力，必须 `level >= 1`。
- 热度定时任务（`HotScoreScheduler`）每 10 分钟执行一次：`hot = likes_num * 3 + collects_num * 3 + comment_num * 2 + views_num * 2`。
- 用户画像定时任务（`UserProfileScheduler`）每 30 分钟执行一次，根据点赞（权重+3）和收藏（权重+5）行为刷新用户兴趣标签权重。
- 角色支持继承：`sys_role.inheritRoleId` 指向父角色，子角色自动拥有父角色权限。
- 审计日志通过 `@AuditLog` 注解自动记录，包含操作模块、方法、URL、参数、IP 和用户信息。

## 8. 包依赖视图

```mermaid
flowchart TD
    Controller["controller"]
    Service["service / service.impl"]
    Mapper["mapper"]
    Entity["entity"]
    DTO["dto"]
    VO["vo"]
    Common["common"]
    Cache["cache"]
    Scheduled["scheduled"]
    AI["ai"]
    Task["task"]
    Permission["permission"]
    WS["websocket"]
    DB["MySQL"]
    Redis["Redis"]
    Caffeine["Caffeine"]
    COS["COS"]
    DashScope["DashScope"]
    MQ["RocketMQ"]

    Controller --> DTO
    Controller --> Service
    Controller --> VO
    Controller --> Common
    Service --> Mapper
    Service --> Entity
    Service --> Common
    Service --> Task
    Service --> Cache
    Permission --> Mapper
    Permission --> Cache
    Mapper --> DB
    Common --> Redis
    Common --> COS
    Common --> WS
    Cache --> Caffeine
    Cache --> Redis
    AI --> DashScope
    AI --> Service
    AI --> Task
    Scheduled --> Mapper
    Scheduled --> Service
    Task --> MQ
    Task --> Service
    WS --> Redis
```
