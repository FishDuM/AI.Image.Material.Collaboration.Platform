# FishPics 后端 UML 与数据模型

> 本文档基于当前 `src/FishPics-backend` 后端代码重构，重点描述参与者、用例、类结构、实体关系、请求流和 AI 任务流。

## 1. 用例模型

### 1.1 参与者

| 参与者 | 说明 |
| --- | --- |
| 访客 | 可查看公开配置、获取验证码、注册、登录、浏览公开内容 |
| 登录用户 | 可维护个人资料、上传图片、管理空间、发布帖子、互动、评论、关注 |
| VIP/SVIP 用户 | 继承登录用户能力，并可使用 AI 标注、编辑、生成和推荐 |
| 管理员 | 可管理用户、图片、帖子、评论、空间、系统配置和 AI 任务 |
| AI Provider | DashScope / 阿里云模型服务，执行视觉理解和图像生成编辑 |
| COS 对象存储 | 保存头像和图片文件 |
| Redis | 保存验证码、Token、用户缓存、系统配置缓存和 Stream 消息 |
| MySQL | 保存业务主数据 |

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

    Vip --> UC11["AI 图片标注"]
    Vip --> UC12["AI 图片编辑"]
    Vip --> UC13["AI 图片生成"]
    Vip --> UC14["AI 图片推荐"]
    Vip --> UC15["查询 AI 任务"]

    Admin --> UC16["用户管理"]
    Admin --> UC17["图片审核"]
    Admin --> UC18["帖子审核/删除"]
    Admin --> UC19["评论审核/删除"]
    Admin --> UC20["空间管理"]
    Admin --> UC21["系统配置管理"]
    Admin --> UC22["AI 任务/配置/统计管理"]

    UC1 --> Redis
    UC3 --> Redis
    UC6 --> COS
    UC11 --> AI
    UC12 --> AI
    UC13 --> AI
    UC14 --> AI
    UC2 --> DB
    UC7 --> DB
    UC8 --> DB
    UC9 --> DB
    UC16 --> DB
```

### 1.3 权限矩阵

| 模块 | 访客 | 登录用户 | VIP/SVIP | 管理员 |
| --- | --- | --- | --- | --- |
| 验证码、注册、登录 | 是 | 是 | 是 | 是 |
| 公开图片、帖子、系统配置查询 | 是 | 是 | 是 | 是 |
| 当前用户、资料、隐私、退出 | 否 | 是 | 是 | 是 |
| 上传图片、空间、发帖、互动、评论 | 否 | 是 | 是 | 是 |
| AI 用户端提交与我的任务 | 否 | 否 | 是 | 是 |
| AI 任务详情 | 是 | 是 | 是 | 是 |
| 后台管理接口 | 否 | 否 | 否 | 是 |

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

    class PicSystem {
        Long id
        String syskey
        String sysvalue
    }

    class AiTask {
        Long id
        Long userId
        Integer type
        String subType
        String inputData
        String outputData
        Integer status
        String errorMsg
        Long pictureId
        Date createTime
        Date updateTime
    }

    User "1" --> "0..*" Space : creates
    User "1" --> "0..*" Picture : uploads
    User "1" --> "0..*" Post : publishes
    User "1" --> "0..*" Comment : writes
    User "1" --> "0..*" AiTask : submits
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
    Picture "0..1" --> "0..*" AiTask : related
```

## 3. 数据库 ER 图

```mermaid
erDiagram
    USER ||--o{ SPACE : creates
    USER ||--o{ PICTURE : uploads
    USER ||--o{ POST : publishes
    USER ||--o{ COMMENT : writes
    USER ||--o{ AI_TASK : submits
    USER ||--o{ USER_FANS : has_fans
    USER ||--o{ USER_POST_COLLECT : collects
    USER ||--o{ USER_POST_LIKES : likes

    SPACE ||--o{ PICTURE : stores
    POST ||--o{ PICTURE_CHILD : has_ordered_images
    PICTURE ||--o{ PICTURE_CHILD : belongs_to_posts
    POST ||--o{ COMMENT : has
    COMMENT ||--o{ COMMENT : replies
    POST ||--o{ USER_POST_COLLECT : collected
    POST ||--o{ USER_POST_LIKES : liked
    PICTURE ||--o{ AI_TASK : processed_by

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
        int to_user_id
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
    }

    USER_POST_LIKES {
        bigint id PK
        bigint user_id
        bigint post_id
    }

    PIC_SYSTEM {
        bigint id PK
        varchar syskey UK
        varchar sysvalue
    }

    AI_TASK {
        bigint id PK
        bigint user_id
        tinyint type
        varchar sub_type
        text input_data
        text output_data
        tinyint status
        varchar error_msg
        bigint picture_id
        datetime create_time
        datetime update_time
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
| `GET` | `/user/fans` | `userId,current,pageSize` | `IPage<FollowUserVO>` | 登录 |
| `GET` | `/user/follows` | `userId,current,pageSize` | `IPage<FollowUserVO>` | 登录 |
| `GET` | `/user/profile` | `userId` | `UserPublicProfileVO` | 登录 |
| `POST` | `/user/admin/getUser` | `UserIdRequest` | `AdminGetUserVO` | 管理员 |
| `POST` | `/user/admin/userList` | `UserQueryWrapper` | `IPage<User>` | 管理员 |
| `POST` | `/user/admin/setStatus` | `UserIdRequest` | `Boolean` | 管理员 |
| `POST` | `/user/admin/editUser` | `UserEditByAdminRequest` | `Boolean` | 管理员 |

### 4.2 PictureController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/picture/avatar` | `file,id` | `String` | 登录 |
| `POST` | `/picture/upload` | `file,targetSpaceId?` | `PictureListVO` | 登录 |
| `GET` | `/picture/list` | `current,pageSize` | `IPage<PictureListVO>` | 公开 |
| `DELETE` | `/picture/delete` | `DeleteByIdList` | `String` | 登录 |
| `PUT` | `/picture/update` | `PictureUpdateRequest` | `Boolean` | 登录 |
| `GET` | `/picture/admin/list` | `current,pageSize,status` | `IPage<PictureAdminVO>` | 管理员 |
| `POST` | `/picture/admin/review` | `pictureId,status,selected` | `Boolean` | 管理员；`selected` 写入 `is_private`，表示是否公开到首页 |

### 4.3 PostController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/post/post` | `UploadPostRequest` | `Boolean` | 登录 |
| `GET` | `/post/getPost` | `id` | `PostDetailVO` | 白名单路径；当前仅按 ID 查询，未强制校验 `status` 或 `is_private` |
| `POST` | `/post/editPost` | `EditPostRequest` | `Boolean` | 作者 |
| `POST` | `/post/postList` | `PostQueryRequest` | `IPage<PostListVO>` | 白名单路径；默认筛 `status=1`，不默认筛 `is_private=0` |
| `POST` | `/post/like` | `id` | `Boolean` | 登录 |
| `POST` | `/post/collect` | `id` | `Boolean` | 登录 |
| `POST` | `/post/pictureList` | `GetPictureBySpaceRequest` | `Map<String,Object>` | 登录 |
| `POST` | `/post/myPosts` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/myCollects` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/myLikes` | `PageRequest` | `IPage<PostListVO>` | 登录 |
| `POST` | `/post/admin/list` | `PostQueryRequest` | `IPage<PostListVO>` | 管理员 |
| `POST` | `/post/admin/review` | `id,status` | `Boolean` | 管理员 |
| `POST` | `/post/admin/delete` | `id` | `Boolean` | 管理员 |

### 4.4 CommentController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/comment/create` | `CreateCommentRequest` | `Long` | 登录 |
| `POST` | `/comment/list` | `CommentQueryRequest` | `IPage<CommentVO>` | 公开 |
| `POST` | `/comment/delete` | `id` | `Boolean` | 登录 |
| `POST` | `/comment/admin/list` | `CommentQueryRequest` | `IPage<CommentVO>` | 管理员 |
| `POST` | `/comment/review` | `id,status` | `Boolean` | 管理员 |
| `POST` | `/comment/adminDelete` | `id` | `Boolean` | 管理员 |

### 4.5 SpaceController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/space/create` | `CreateSpace` | `Boolean` | 登录 |
| `GET` | `/space/list` | `type` | `List<SpaceVO>` | 登录 |
| `GET` | `/space/getSpace` | `id` | `SpaceVO` | 登录/成员 |
| `POST` | `/space/update` | `UpdateSpace` | `Boolean` | 拥有者/管理员 |
| `POST` | `/space/pictureList` | `SpacePictureList` | `PicturePageVO` | 登录/成员 |
| `GET` | `/space/admin/list` | `current,pageSize,name,type` | `IPage<SpaceVO>` | 管理员 |
| `POST` | `/space/admin/update` | `SpaceAdminUpdateRequest` | `Boolean` | 管理员 |
| `POST` | `/space/admin/delete` | `{id}` | `Boolean` | 管理员 |
| `POST` | `/space/admin/setStatus` | `{id,status}` | `Boolean` | 管理员 |

### 4.6 SystemController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `GET` | `/system/list` | - | `List<String>` | 公开 |
| `POST` | `/system/addList` | `AddSysPicType` | `Boolean` | 管理员 |
| `POST` | `/system/deleteType` | `{value}` | `Boolean` | 管理员 |
| `GET` | `/system/marquee` | - | `List<String>` | 公开 |
| `POST` | `/system/addMarquee` | `AddSysMarquee` | `Boolean` | 管理员 |
| `POST` | `/system/deleteMarquee` | `{url}` | `Boolean` | 管理员 |

### 4.7 AiController

| 方法 | 路径 | 入参 | 出参 | 权限 |
| --- | --- | --- | --- | --- |
| `POST` | `/ai/tags` | `id` | `AiPictureMessage` | VIP/SVIP |
| `POST` | `/ai/edit` | `EditingRequest` | `Long taskId` | VIP/SVIP |
| `POST` | `/ai/generate` | `GenerationRequest` | `Long taskId` | VIP/SVIP |
| `POST` | `/ai/recommend` | `RecommendationRequest` | `Long taskId` | VIP/SVIP |
| `GET` | `/ai/task/{id}` | `id` | `AiTaskVO` | 白名单路径，Controller 仅校验任务存在 |
| `GET` | `/ai/task/my` | `current,pageSize` | `IPage<AiTaskVO>` | VIP/SVIP |
| `GET` | `/ai/admin/tasks` | `current,pageSize,type,status,userId` | `IPage<AiTaskVO>` | 管理员 |
| `GET` | `/ai/admin/stats` | - | `AiStatsVO` | 管理员 |
| `GET` | `/ai/admin/config` | - | `AiConfigVO` | 管理员 |
| `POST` | `/ai/admin/config` | `AiConfigUpdateRequest` | `Boolean` | 管理员 |

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
    A["用户点击点赞或收藏"] --> B["校验登录与帖子存在"]
    B --> C{"是否已有关系记录"}
    C -->|有| D["删除 user_post_likes / user_post_collect"]
    C -->|无| E["新增关系记录"]
    D --> F["扣减帖子统计数"]
    E --> G["增加帖子统计数"]
    F --> H["返回当前状态 false"]
    G --> I["返回当前状态 true"]
```

### 5.4 AI 任务流程

```mermaid
stateDiagram-v2
    [*] --> Submitted: VIP/SVIP 提交请求
    Submitted --> Processing: 创建 ai_task(status=0)
    Processing --> ProviderCall: 异步调用模型服务
    ProviderCall --> Success: 生成 output_data
    ProviderCall --> Failed: 捕获异常并写入 error_msg
    Success --> [*]: status=1
    Failed --> [*]: status=2
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
| `SpaceAdminUpdateRequest` | 空间后台可改字段 | 后台空间编辑 |
| `PictureUpdateRequest` | `ids,pictureName,introduction,pictureUrl` | 图片信息编辑 |
| `DeleteByIdList` | `ids` | 批量删除图片 |
| `AddSysPicType` | `typeList` | 添加分类标签 |
| `AddSysMarquee` | `marqueeList` | 添加跑马灯 |
| `EditingRequest` | `imageUrl,editType,options` | AI 编辑 |
| `GenerationRequest` | `prompt` 等 | AI 生成 |
| `RecommendationRequest` | `referencePictureId` 等 | AI 推荐 |
| `AiConfigUpdateRequest` | AI 配置字段 | 后台配置 |

### 6.2 主要响应 VO

| VO | 用途 |
| --- | --- |
| `UserLoginVO` | 登录态用户信息和 Token |
| `UserMessageVO` | 个人主页聚合信息 |
| `UserPublicProfileVO` | 用户公开主页 |
| `FollowUserVO` | 粉丝/关注列表项 |
| `PictureListVO` | 图片列表基础项 |
| `PictureAdminVO` | 后台图片管理项 |
| `PicturePageVO` | 空间图片分页 |
| `PostListVO` | 帖子列表项 |
| `PostDetailVO` | 帖子详情聚合 |
| `CommentVO` | 评论展示项 |
| `SpaceVO` | 空间展示项 |
| `SpaceMemberVO` | 团队成员展示项 |
| `AiTaskVO` | AI 任务展示项 |
| `AiStatsVO` | AI 统计 |
| `AiConfigVO` | AI 配置 |

## 7. 状态与约束

### 7.1 状态字段

| 模型 | 字段 | 值 |
| --- | --- | --- |
| `User` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Picture` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Post` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Comment` | `status` | `0` 禁用，`1` 正常，`2` 待审核 |
| `Space` | `status` | `0` 禁用，`1` 正常 |
| `AiTask` | `status` | `0` 处理中，`1` 成功，`2` 失败 |

### 7.2 关键约束

- `user.username`、`user.nickname` 唯一。
- `pic_system.syskey` 唯一。
- `picture_child` 对 `picture_id + post_id` 建立唯一索引。
- 帖子图片顺序必须依赖 `picture_child.sort_num`。
- `picture.is_private` 当前承担首页公开标记含义：`0` 不公开到首页，`1` 公开到首页；管理员图片审核接口的 `selected` 参数会写入该字段。
- `picture.tags` 存储 AI 标签，格式为 JSON 数组（如 `["人物","风景"]`）。
- `ai_task.input_data` 和 `ai_task.output_data` 存储 JSON 字符串。
- 普通用户不能调用 AI 用户端能力，必须 `level >= 1`。

## 8. 包依赖视图

```mermaid
flowchart TD
    Controller["controller"]
    Service["service / service.impl"]
    Mapper["mapper / ai.mapper"]
    Entity["entity"]
    DTO["dto / ai.dto"]
    VO["vo / vo.ai"]
    Common["common"]
    AI["ai.interfaces / ai.provider / ai.service"]
    DB["MySQL"]
    Redis["Redis"]
    COS["COS"]
    DashScope["DashScope"]

    Controller --> DTO
    Controller --> Service
    Controller --> VO
    Controller --> Common
    Service --> Mapper
    Service --> Entity
    Service --> Common
    Mapper --> DB
    Common --> Redis
    Common --> COS
    AI --> DashScope
    AI --> Mapper
    Service --> AI
```
