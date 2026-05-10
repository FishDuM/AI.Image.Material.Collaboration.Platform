# AI.Image.Material.Collaboration.Platform UML 模型图

> 基于实际前后端项目代码完整重构，涵盖用户管理、帖子管理、图片管理、系统配置等全量功能

---

## 1. 用例图 (Use Case Diagram)

### 1.1 系统参与者 (Actors)

- **普通用户 (User)**：可注册、登录、浏览内容、发帖、上传图片、管理个人资料
- **管理员 (Admin)**：可管理用户、封禁/解封账号、编辑用户信息、查看用户详情

### 1.2 用例列表

#### 普通用户用例

- 注册账户（图形验证码验证，账号6-11位，密码8-20位，确认密码一致）
- 登录系统（图形验证码验证，Session认证）
- 获取个人主页信息（查看我的发布/收藏/点赞）
- 获取当前登录用户信息
- 编辑个人信息（昵称5-11位，账号6-11位，密码8-20位）
- 上传头像（文件大小限制5MB）
- 浏览社区广场
- 发布帖子（标题、内容、图片、封面、隐私设置）
- 编辑帖子（修改标题、内容、图片、封面、隐私设置）
- 获取帖子详情
- 获取帖子列表（分页、搜索、热门排序）
- 点赞帖子
- 上传帖子图片（文件大小限制5MB）
- 查看通知消息
- 访问个人空间
- 访问团队空间
- 访问AI管理
- 退出登录

#### 管理员用例

- 获取用户列表（分页查询，多维度搜索）
- 多维度搜索用户（ID、用户名、邮箱、手机号、昵称、角色、状态、创建时间）
- 获取单个用户详情
- 编辑用户信息（修改资料、密码、角色、状态）
- 封禁/解封用户（状态切换：正常↔禁用）
- 获取管理员图片列表（分页，按状态筛选）
- 审核图片（通过/拒绝/设置精选）
- 获取系统分类标签列表
- 添加系统分类标签
- 删除系统分类标签
- 获取跑马灯图片列表
- 添加跑马灯图片
- 删除跑马灯图片
- 创建空间（私人/团队）
- 获取空间列表（按类型查询）
- 更新空间信息

### 1.3 用例关系

- **包含关系 (Include)**:
  - 注册账户 → 获取图形验证码
  - 登录系统 → 获取图形验证码
  - 注册账户 → 验证用户协议
  - 编辑个人信息 → 校验身份合法性
  - 上传头像 → 校验文件大小
  - 上传帖子图片 → 校验文件大小
  - 发布帖子 → 上传图片
  - 管理员操作 → 权限校验
  - 管理员审核图片 → 校验图片状态
  - 创建空间 → 初始化存储大小
- **前置条件 (Precondition)**:
  - 用户管理 ← 管理员已登录且具有admin角色
  - 编辑用户 ← 用户存在且为管理员权限
  - 编辑个人信息 ← 用户已登录且只能修改自己的信息
  - 发布帖子 ← 用户已登录
  - 点赞帖子 ← 用户已登录

### 1.4 权限控制

- 管理员接口使用 `@AuthCheck(role = ADMIN)` 注解
- 通过 `AuthInterceptor` 切面拦截进行权限校验
- 基于 Spring Session + Redis 的用户认证机制
- 用户登录后将userId通过 `request.getSession().setAttribute(TOKEN_KEY, userId)` 存储到Session中
- 将User对象JSON序列化缓存到Redis（KEY: USER_ID:{userId}），Spring Session自动将会话数据持久化到Redis
- `LoginUser` 工具类从Session获取userId，再从Redis读取User对象JSON并反序列化
- 私有接口通过 `request.getSession().getAttribute(TOKEN_KEY)` 从Session中获取userId进行身份验证

---

## 2. 类图 (Class Diagram)

### 2.1 核心实体类 (Entity)

#### User 实体类

```
+------------------------------------------+
|                   User                   |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - username: String (UNIQUE, 32字符)      |
| - password: String (128字符, MD5+Salt)   |
| - avatar: String (256字符, URL)          |
| - email: String (64字符)                 |
| - phone: String (16字符)                 |
| - nickname: String (UNIQUE, 32字符)      |
| - status: Integer (1-正常/0-禁用/2-待审核)|
| - isDelete: Integer (0-未删除/1-已删除)  |
| - role: String (admin/user)              |
| - createTime: Date                       |
| - updateTime: Date                       |
| - likeNum: Long                          |
| - collectNum: Long                       |
| - isPrivateFollows: Integer (0-公开/1-不公开)|
| - isPrivatePostCollect: Integer (0-公开/1-不公开)|
| - isPrivateLikes: Integer (0-公开/1-不公开)|
| - isPrivateFans: Integer (0-公开/1-不公开)|
| - level: Integer (用户级别, 0-普通/1-VIP/2-SVIP)|
| - size: Long (已用存储大小, 字节)          |
+------------------------------------------+
```

#### Post 实体类

```
+------------------------------------------+
|                   Post                   |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - userId: Long (FK->User)                |
| - title: String (256字符)                |
| - content: String (TEXT)                 |
| - status: Integer (1-正常/0-禁用/2-待审核)|
| - createTime: Date                       |
| - updateTime: Date                       |
| - isDelete: Integer (0-未删除/1-已删除)  |
| - likesNum: Long                         |
| - collectsNum: Long                      |
| - commentNum: Integer                    |
| - isPrivate: Integer (0-公开/1-仅自己可见)|
| - cover: Long (封面图片ID, FK->Picture)  |
| - viewsNum: Long                         |
| - hot: BigDecimal (热度值)               |
+------------------------------------------+
```

#### Picture 实体类

```
+------------------------------------------+
|                 Picture                  |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - userId: Long (FK->User)                |
| - pictureName: Long                      |
| - url: String (512字符)                  |
| - width: String (32字符)                 |
| - height: String (32字符)                |
| - size: Long (文件大小, 字节)            |
| - status: Integer (1-正常/0-禁用/2-待审核, 默认2)|
| - createTime: Date                       |
| - updateTime: Date                       |
| - isPrivate: Integer (0-公开/1-不公开)   |
| - postId: Long (FK->Post)                |
| - spaceId: Long (FK->Space)              |
| - introduction: String (图片简介)        |
+------------------------------------------+
```

#### PictureChild 实体类

```
+------------------------------------------+
|              PictureChild                |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - pictureId: Long (FK->Picture)          |
| - postId: Long (FK->Post)                |
| - sortNum: Integer (排序序号)            |
+------------------------------------------+
```

#### Comment 实体类

```
+------------------------------------------+
|                 Comment                  |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - userId: Long (FK->User)                |
| - postId: Long (FK->Post)                |
| - content: String (TEXT)                 |
| - parentId: Long (父评论ID, 支持二级评论) |
| - toUserId: Integer (回复给谁)           |
| - status: Integer (1-正常/0-禁用/2-待审核)|
| - createTime: Date                       |
+------------------------------------------+
```

#### UserFans 实体类

```
+------------------------------------------+
|                UserFans                  |
+------------------------------------------+
| - id: Long (PK)                          |
| - userId: Long (FK->User)                |
| - fanId: Long (FK->User)                 |
+------------------------------------------+
```

#### UserPostCollect 实体类

```
+------------------------------------------+
|            UserPostCollect               |
+------------------------------------------+
| - id: Long (PK)                          |
| - userId: Long (FK->User)                |
| - postId: Long (FK->Post)                |
+------------------------------------------+
```

#### UserPostLikes 实体类

```
+------------------------------------------+
|             UserPostLikes                |
+------------------------------------------+
| - id: Long (PK)                          |
| - userId: Long (FK->User)                |
| - postId: Long (FK->Post)                |
+------------------------------------------+
```

#### Space 实体类

```
+------------------------------------------+
|                  Space                   |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - userId: Long (FK->User, 空间拥有者)    |
| - name: String (空间名称)                |
| - introduction: String (空间简介)        |
| - type: Integer (0-私人/1-团队)          |
| - level: Integer (0-普通/1-VIP/2-SVIP)   |
| - storageSize: Long (存储空间大小, 字节) |
| - size: Long (已用存储大小, 字节)        |
| - teamUsersId: String (团队成员ID列表)   |
+------------------------------------------+
```

#### PicSystem 实体类

```
+------------------------------------------+
|               PicSystem                  |
+------------------------------------------+
| - id: Long (PK, AUTO_INCREMENT)          |
| - syskey: String (256字符, UNIQUE)       |
| - sysvalue: String (1024字符)            |
+------------------------------------------+
```

### 2.2 数据传输对象 (DTO)

#### 用户相关 DTO

##### UserLoginRequest

```
+------------------------------------------+
|           UserLoginRequest               |
+------------------------------------------+
| - username: String                       |
| - password: String                       |
| - checkCode: String                      |
| - captchaKey: String                     |
+------------------------------------------+
```

##### UserRequestRequest (注册)

```
+------------------------------------------+
|           UserRequestRequest             |
+------------------------------------------+
| - username: String (6-11位)              |
| - password: String (8-20位)              |
| - checkPassword: String                  |
| - checkCode: String                      |
| - captchaKey: String                     |
+------------------------------------------+
```

##### UserEditRequest (用户编辑自己)

```
+------------------------------------------+
|            UserEditRequest               |
+------------------------------------------+
| - id: Long                               |
| - username: String (6-11位)              |
| - password: String (8-20位)              |
| - avatar: String                         |
| - email: String                          |
| - phone: String                          |
| - nickname: String (5-11位)              |
+------------------------------------------+
```

##### UserEditByAdminRequest (管理员编辑用户)

```
+------------------------------------------+
|        UserEditByAdminRequest            |
+------------------------------------------+
| - id: Long (必填)                        |
| - username: String                       |
| - password: String                       |
| - avatar: String                         |
| - email: String                          |
| - phone: String                          |
| - nickname: String                       |
| - status: Integer                        |
| - role: String                           |
+------------------------------------------+
```

##### UserQueryWrapper

```
+------------------------------------------+
|           UserQueryWrapper               |
+------------------------------------------+
| - id: Long                               |
| - username: String                       |
| - email: String                          |
| - phone: String                          |
| - nickname: String                       |
| - role: String                           |
| - status: Integer                        |
| - createTime: Date                       |
| - current: long (当前页)                 |
| - pageSize: long (每页大小)              |
| - sortField: String                      |
| - sortOrder: String                      |
+------------------------------------------+
```

##### UserIdRequest

```
+------------------------------------------+
|            UserIdRequest                 |
+------------------------------------------+
| - userId: Long                           |
+------------------------------------------+
```

#### 帖子相关 DTO

##### UploadPostRequest

```
+------------------------------------------+
|          UploadPostRequest               |
+------------------------------------------+
| - imageId: List<Long> (图片ID列表)       |
| - title: String                          |
| - content: String                        |
| - cover: Long (封面图片ID)               |
| - isPrivate: Integer (0-公开/1-仅自己可见)|
+------------------------------------------+
```

##### EditPostRequest

```
+------------------------------------------+
|           EditPostRequest                |
+------------------------------------------+
| - id: Long (帖子ID)                      |
| - imageId: List<Long> (图片ID列表)       |
| - title: String                          |
| - content: String                        |
| - cover: Long (封面图片ID)               |
| - isPrivate: Integer (0-公开/1-仅自己可见)|
+------------------------------------------+
```

##### PostQueryRequest

```
+------------------------------------------+
|          PostQueryRequest                |
|  extends PageRequest                     |
+------------------------------------------+
| - userId: Long                           |
| - text: String (搜索文本)                |
| - updateTime: Date                       |
| - hotPost: Boolean (是否热门优先)        |
| - current: long (继承自PageRequest)      |
| - pageSize: long (继承自PageRequest)     |
+------------------------------------------+
```

##### PostQueryWrapper

```
+------------------------------------------+
|          PostQueryWrapper                |
+------------------------------------------+
| - userId: Long                           |
| - text: String                           |
| - updateTime: Date                       |
| - hotPost: Boolean                       |
+------------------------------------------+
```

#### 图片相关 DTO

##### PictureMessage

```
+------------------------------------------+
|           PictureMessage                 |
+------------------------------------------+
| - pictureName: String                    |
| - width: String                          |
| - height: String                         |
| - size: String                           |
| - url: String                            |
+------------------------------------------+
```

#### 基础 DTO

##### PageRequest (基础分页)

```
+------------------------------------------+
|            PageRequest                   |
+------------------------------------------+
| - current: long                          |
| - pageSize: long                         |
+------------------------------------------+
```

##### DeleteById (基础删除)

```
+------------------------------------------+
|             DeleteById                   |
+------------------------------------------+
| - id: Long                               |
+------------------------------------------+
```

#### 图片相关 DTO

##### DeleteByIdList

```
+------------------------------------------+
|           DeleteByIdList                 |
+------------------------------------------+
| - idList: List<Long> (图片ID列表)        |
+------------------------------------------+
```

#### 空间相关 DTO

##### CreateSpace

```
+------------------------------------------+
|             CreateSpace                  |
+------------------------------------------+
| - name: String (空间名称)                |
| - introduction: String (空间简介)        |
| - type: Integer (空间类型, 0-私人/1-团队)|
+------------------------------------------+
```

##### UpdateSpace

```
+------------------------------------------+
|             UpdateSpace                  |
+------------------------------------------+
| - id: Long (空间ID)                      |
| - name: String (空间名称)                |
| - introduction: String (空间简介)        |
+------------------------------------------+
```

##### SpacePictureList

```
+------------------------------------------+
|           SpacePictureList               |
+------------------------------------------+
| - spaceId: Long (空间ID)                 |
| - current: long (当前页)                 |
| - pageSize: long (每页大小)              |
+------------------------------------------+
```

##### SpaceQueryWrapper

```
+------------------------------------------+
|          SpaceQueryWrapper               |
+------------------------------------------+
| - userId: Long                           |
| - name: String                           |
| - type: Integer                          |
+------------------------------------------+
```

#### 系统相关 DTO

##### AddSysPicType

```
+------------------------------------------+
|            AddSysPicType                 |
+------------------------------------------+
| - typeList: List<String> (标签列表)      |
+------------------------------------------+
```

##### AddSysMarquee

```
+------------------------------------------+
|           AddSysMarquee                  |
+------------------------------------------+
| - marqueeList: List<String> (跑马灯图片URL列表)|
+------------------------------------------+
```

### 2.3 视图对象 (VO)

#### UserLoginVO

```
+------------------------------------------+
|             UserLoginVO                  |
+------------------------------------------+
| - id: Long                               |
| - username: String                       |
| - nickname: String                       |
| - avatar: String                         |
| - email: String                          |
| - phone: String                          |
| - role: String                           |
+------------------------------------------+
```

#### UserMessageVO

```
+------------------------------------------+
|            UserMessageVO                 |
+------------------------------------------+
| - id: Long                               |
| - username: String                       |
| - avatar: String                         |
| - email: String                          |
| - phone: String                          |
| - nickname: String                       |
| - role: String                           |
| - createTime: Date                       |
| - postList: List<PostListVO>             |
| - postCollectList: List<PostListVO>      |
| - postLikeList: List<PostListVO>         |
+------------------------------------------+
```

#### CheckCodeVO

```
+------------------------------------------+
|             CheckCodeVO                  |
+------------------------------------------+
| - captchaKey: String                     |
| - base64Image: String                    |
+------------------------------------------+
```

#### PostListVO

```
+------------------------------------------+
|             PostListVO                   |
+------------------------------------------+
| - id: Long                               |
| - userId: Long                           |
| - username: String                       |
| - avatar: String                         |
| - likesNum: Long                         |
| - title: String                          |
| - url: String (封面图片URL)              |
+------------------------------------------+
```

#### PostDetailVO

```
+------------------------------------------+
|            PostDetailVO                  |
+------------------------------------------+
| - id: Long                               |
| - userId: Long                           |
| - username: String                       |
| - avatar: String                         |
| - title: String                          |
| - content: String                        |
| - updateTime: Date                       |
| - likesNum: Long                         |
| - collectsNum: Long                      |
| - commentNum: Integer                    |
| - pictureUrl: List<String> (图片URL列表) |
| - pictureIds: List<Long> (图片ID列表)    |
| - cover: Long (封面图片ID)               |
+------------------------------------------+
```

#### PicturePostVO

```
+------------------------------------------+
|            PicturePostVO                 |
+------------------------------------------+
| - url: String (图片URL)                  |
| - pictureId: Long (图片ID)               |
+------------------------------------------+
```

#### PictureAdminVO

```
+------------------------------------------+
|           PictureAdminVO                 |
+------------------------------------------+
| - id: Long                               |
| - url: String (图片URL)                  |
| - width: String                          |
| - height: String                         |
| - size: Long (文件大小)                  |
| - status: Integer                        |
| - createTime: Date                       |
| - userId: Long                           |
| - isPrivate: Integer                     |
+------------------------------------------+
```

#### PictureListVO

```
+------------------------------------------+
|           PictureListVO                  |
+------------------------------------------+
| - id: Long                               |
| - url: String (图片URL)                  |
+------------------------------------------+
```

#### PicturePageVO

```
+------------------------------------------+
|           PicturePageVO                  |
+------------------------------------------+
| - records: List<PictureAdminVO>          |
| - total: Long (总数)                     |
+------------------------------------------+
```

### 2.4 通用响应类

#### Response<T>

```
+------------------------------------------+
|             Response<T>                  |
+------------------------------------------+
| - code: Integer                          |
| - message: String                        |
| - data: T                                |
+------------------------------------------+
```

### 2.5 控制器类

#### UserController

```
+------------------------------------------+
|            UserController                |
+------------------------------------------+
| - userService: UserService               |
| - userMapper: UserMapper                 |
+------------------------------------------+
| + POST /user/login                       |
| + POST /user/register                    |
| + GET  /user/checkCode/register          |
| + GET  /user/checkCode/login             |
| + GET  /user/myself                      |
| + GET  /user/getUser                     |
| + POST /user/editUser                    |
| + GET  /user/logout (退出登录)           |
| + POST /user/admin/getUser               |
| + POST /user/admin/userList              |
| + POST /user/admin/setStatus             |
| + POST /user/admin/editUser              |
+------------------------------------------+
```

#### PostController

```
+------------------------------------------+
|            PostController                |
+------------------------------------------+
| - postService: PostService               |
+------------------------------------------+
| + POST /post/post (发布帖子)             |
| + GET  /post/getPost (获取帖子详情)      |
| + POST /post/editPost (编辑帖子)         |
| + POST /post/postList (获取帖子列表)     |
| + POST /post/like (点赞帖子)             |
| + POST /post/myPosts (我的帖子列表)      |
| + POST /post/myCollects (我的收藏列表)   |
| + POST /post/myLikes (我的点赞列表)      |
+------------------------------------------+
```

#### PictureController

```
+------------------------------------------+
|           PictureController              |
+------------------------------------------+
| - pictureService: PictureService         |
+------------------------------------------+
| + POST /picture/avatar (上传头像)        |
| + POST /picture/post (上传帖子图片)      |
| + GET  /picture/list (公开图片列表)      |
| + GET  /picture/admin/list (管理员图片列表)|
| + POST /picture/admin/review (审核图片)  |
| + POST /picture/delete (删除图片)        |
+------------------------------------------+
```

#### SpaceController

```
+------------------------------------------+
|           SpaceController                |
+------------------------------------------+
| - spaceService: SpaceService             |
+------------------------------------------+
| + POST /space/create (创建空间)          |
| + GET  /space/list (获取空间列表)        |
| + POST /space/update (更新空间)          |
| + POST /space/pictureList (空间图片列表) |
+------------------------------------------+
```

#### SystemController

```
+------------------------------------------+
|           SystemController               |
+------------------------------------------+
| - picSystemService: PicSystemService     |
+------------------------------------------+
| + GET  /system/list (获取分类标签列表)   |
| + POST /system/addList (添加分类标签)    |
| + POST /system/deleteType (删除分类标签) |
| + GET  /system/marquee (获取跑马灯列表)  |
| + POST /system/addMarquee (添加跑马灯)   |
| + POST /system/deleteMarquee (删除跑马灯)|
+------------------------------------------+
```

### 2.6 服务层接口

#### UserService 接口

```
+------------------------------------------+
|             UserService                  |
| extends IService<User>                   |
+------------------------------------------+
| + getCheckCode(redisKey, len, minute)    |
| + getLoginUser(request)                  |
| + userRegister(request, request)         |
| + userLogin(request, request)            |
| + newQueryWrapper(userQueryWrapper)      |
| + getUserList(wrapper, current, pageSize)|
| + setStatus(userId)                      |
| + editUser(adminRequest)                 |
| + getMyselfMessage(request)              |
| + editMyself(editRequest, request)       |
| + isMe(id, request)                      |
+------------------------------------------+
```

#### PostService 接口

```
+------------------------------------------+
|             PostService                  |
| extends IService<Post>                   |
+------------------------------------------+
| + uploadPost(request, request)           |
| + getPost(id)                            |
| + editPost(request, request)             |
| + getPostList(queryRequest)              |
| + newQueryWrapper(queryWrapper)          |
| + likePost(id, request)                  |
| + getMyPosts(pageRequest, request)       |
| + getMyCollects(pageRequest, request)    |
| + getMyLikes(pageRequest, request)       |
+------------------------------------------+
```

#### PictureService 接口

```
+------------------------------------------+
|            PictureService                |
| extends IService<Picture>                |
+------------------------------------------+
| + uploadAvatar(file, id, request)        |
| + uploadPicture4Post(file, request)      |
| + setPicturePostId(imageIds, postId)     |
| + getPictureList(current, pageSize)      |
| + getAdminPictureList(current, pageSize, status)|
| + reviewPicture(pictureId, status, selected)|
| + deletePicture(ids)                     |
+------------------------------------------+
```

#### PictureChildService 接口

```
+------------------------------------------+
|         PictureChildService              |
| extends IService<PictureChild>           |
+------------------------------------------+
```

#### SpaceService 接口

```
+------------------------------------------+
|             SpaceService                 |
| extends IService<Space>                  |
+------------------------------------------+
| + createSpace(createSpace, request)      |
| + listSpace(type, request)               |
| + updateSpace(updateSpace, request)      |
| + pictureList(spacePictureList, request) |
| + getSpaceQueryWrapper(spaceQueryWrapper)|
+------------------------------------------+
```

#### PicSystemService 接口

```
+------------------------------------------+
|          PicSystemService                |
+------------------------------------------+
| + getTypeList()                          |
| + addTypeList(addSysPicType)             |
| + deleteType(addSysPicType)              |
| + getMarquess()                          |
| + addMarquee(addSysMarquee)              |
| + deleteMarquee(addSysMarquee)           |
+------------------------------------------+
```

#### LoginUser 工具类

```
+------------------------------------------+
|              LoginUser                   |
+------------------------------------------+
| - stringRedisTemplate: StringRedisTemplate|
+------------------------------------------+
| + getLoginUser(request): User            |
|   [从Session获取userId，从Redis读取User] |
+------------------------------------------+
```

#### 其他服务接口

```
+------------------------------------------+
|          CommentService                  |
| extends IService<Comment>                |
+------------------------------------------+

+------------------------------------------+
|          UserFansService                 |
| extends IService<UserFans>               |
+------------------------------------------+

+------------------------------------------+
|       UserPostCollectService             |
| extends IService<UserPostCollect>        |
+------------------------------------------+

+------------------------------------------+
|       UserPostLikesService               |
| extends IService<UserPostLikes>          |
+------------------------------------------+
```

#### CosService 接口

```
+------------------------------------------+
|              CosService                  |
+------------------------------------------+
| 腾讯云COS对象存储服务                    |
+------------------------------------------+
```

### 2.7 类间关系

#### 关联关系 (Association)

- User "1" —— "\*" Post (一个用户发布多个帖子)
- User "1" —— "\*" Picture (一个用户拥有多个图片)
- User "1" —— "\*" Comment (一个用户发表多个评论)
- User "1" —— "\*" Space (一个用户拥有多个空间)
- Post "1" —— "\*" Comment (一个帖子有多个评论)
- Post "1" —— "\*" Picture (一个帖子包含多个图片, 通过postId关联)
- Space "1" —— "\*" Picture (一个空间包含多个图片, 通过spaceId关联)
- User "1" —— "\*" UserFans (一个用户有多个粉丝)
- User "1" —— "\*" UserPostCollect (一个用户收藏多个帖子)
- User "1" —— "\*" UserPostLikes (一个用户点赞多个帖子)

#### 聚合关系 (Aggregation)

- Post ◇—— "\*" Picture (帖子聚合多个图片，通过postId字段)
- Space ◇—— "\*" Picture (空间聚合多个图片，通过spaceId字段)

#### 依赖关系 (Dependency)

- UserController → UserService (控制器依赖服务层)
- PostController → PostService (控制器依赖服务层)
- PictureController → PictureService (控制器依赖服务层)
- SpaceController → SpaceService (控制器依赖服务层)
- SystemController → PicSystemService (控制器依赖服务层)
- UserServiceImpl → UserMapper (服务实现依赖数据访问层)
- UserServiceImpl → PostMapper (服务实现依赖帖子数据访问)
- UserServiceImpl → UserPostCollectMapper (服务实现依赖收藏数据访问)
- UserServiceImpl → UserPostLikesMapper (服务实现依赖点赞数据访问)
- UserServiceImpl → StringRedisTemplate (服务实现依赖Redis)
- UserServiceImpl → LoginUser (服务实现依赖登录用户工具)
- PostServiceImpl → PictureService (帖子服务依赖图片服务)
- PostServiceImpl → PictureChildService (帖子服务依赖子图片关联服务)
- PostServiceImpl → UserPostLikesService (帖子服务依赖点赞服务)
- PictureServiceImpl → CosService (图片服务依赖COS存储)
- PictureServiceImpl → SpaceMapper (图片服务依赖空间数据访问)
- AuthInterceptor → HttpSession (拦截器依赖Session获取用户信息)
- LoginUser → StringRedisTemplate (登录用户工具依赖Redis读取用户信息)

#### 实现关系 (Realization)

- UserServiceImpl —|> UserService (实现接口)
- PostServiceImpl —|> PostService (实现接口)
- PictureServiceImpl —|> PictureService (实现接口)
- PictureChildServiceImpl —|> PictureChildService (实现接口)
- CommentServiceImpl —|> CommentService (实现接口)
- UserPostCollectServiceImpl —|> UserPostCollectService (实现接口)
- UserPostLikesServiceImpl —|> UserPostLikesService (实现接口)
- UserFansServiceImpl —|> UserFansService (实现接口)
- SpaceServiceImpl —|> SpaceService (实现接口)
- PicSystemServiceImpl —|> PicSystemService (实现接口)

#### 继承关系 (Inheritance)

- UserServiceImpl —|> ServiceImpl<UserMapper, User> (MyBatis-Plus基类)
- PostServiceImpl —|> ServiceImpl<PostMapper, Post> (MyBatis-Plus基类)
- PictureServiceImpl —|> ServiceImpl<PictureMapper, Picture> (MyBatis-Plus基类)
- PictureChildServiceImpl —|> ServiceImpl<PictureChildMapper, PictureChild> (MyBatis-Plus基类)
- CommentServiceImpl —|> ServiceImpl<CommentMapper, Comment> (MyBatis-Plus基类)
- UserPostCollectServiceImpl —|> ServiceImpl<UserPostCollectMapper, UserPostCollect> (MyBatis-Plus基类)
- UserPostLikesServiceImpl —|> ServiceImpl<UserPostLikesMapper, UserPostLikes> (MyBatis-Plus基类)
- UserFansServiceImpl —|> ServiceImpl<UserFansMapper, UserFans> (MyBatis-Plus基类)
- SpaceServiceImpl —|> ServiceImpl<SpaceMapper, Space> (MyBatis-Plus基类)
- PicSystemServiceImpl —|> ServiceImpl<PicSystemMapper, PicSystem> (MyBatis-Plus基类)
- PostQueryRequest —|> PageRequest (继承分页基类)

---

## 3. 顺序图 (Sequence Diagram)

### 3.1 用户注册流程

```
用户 -> 前端: 点击注册，请求验证码
前端 -> 后端(UserController): GET /user/checkCode/register
后端 -> UserService: getCheckCode(redisKey, len=5, minute=5)
UserService -> Hutool CaptchaUtil: createCircleCaptcha()
Hutool CaptchaUtil --> UserService: 返回base64图片
UserService -> Redis: 存储验证码(5分钟)
UserService --> 后端: 返回base64图片
后端 --> 前端: Response<CheckCodeVO> (captchaKey + base64Image)

用户 -> 前端: 填写注册表单并提交
前端 -> 后端(UserController): POST /user/register (UserRequestRequest)
后端 -> UserService: userRegister(userRequestRequest, request)
UserService -> Redis: 校验验证码
UserService -> UserMapper: selectCount(username)
UserMapper -> MySQL: SELECT COUNT(*) FROM user WHERE username=?
MySQL --> UserMapper: 返回计数结果
UserService -> Hutool DigestUtil: MD5加密密码(加盐: fish)
UserService -> UserService: 生成默认昵称(小鱼籽_+随机6位)
UserService -> UserMapper: INSERT INTO user
UserMapper -> MySQL: 插入用户记录
MySQL --> UserMapper: 返回插入结果
UserService -> Redis: 删除验证码
UserService --> 后端: Response<Boolean>
后端 --> 前端: 注册成功
前端 --> 用户: 显示注册成功
```

### 3.2 用户登录流程

```
用户 -> 前端: 点击登录，请求验证码
前端 -> 后端(UserController): GET /user/checkCode/login
后端 -> UserService: getCheckCode(redisKey, len=5, minute=5)
UserService -> Hutool CaptchaUtil: createCircleCaptcha()
Hutool CaptchaUtil --> UserService: 返回base64图片
UserService -> Redis: 存储验证码(5分钟)
UserService --> 后端: 返回base64图片
后端 --> 前端: Response<CheckCodeVO> (captchaKey + base64Image)

用户 -> 前端: 提交登录表单
前端 -> 后端(UserController): POST /user/login (UserLoginRequest)
后端 -> UserService: userLogin(userLoginRequest, request)
UserService -> Redis: 校验验证码
UserService -> Hutool DigestUtil: MD5加密密码(加盐: fish)
UserService -> UserMapper: selectOne(username, password)
UserMapper -> MySQL: SELECT * FROM user WHERE username=? AND password=?
MySQL --> UserMapper: 返回用户数据
UserService -> HttpSession: setAttribute(TOKEN_KEY, userId)
UserService -> Redis: set(USER_ID:{userId}, UserJSON)
UserService -> BeanUtil: copyProperties(user, UserLoginVO)
UserService --> 后端: Response<UserLoginVO>
后端 --> 前端: Response<UserLoginVO>
前端 -> localStorage: 保存用户信息
前端 --> 用户: 显示登录成功并跳转
```

### 3.3 获取个人主页信息流程

```
用户 -> 前端: 访问个人主页
前端 -> 后端(UserController): GET /user/myself
后端 -> UserService: getMyselfMessage(request)
UserService -> LoginUser: getLoginUser(request) [从Session获取userId，从Redis获取User]
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser -> JSONUtil: toBean(UserJSON, User.class)
JSONUtil --> LoginUser: 返回User对象
LoginUser --> UserService: 返回User对象
UserService -> PostMapper: selectList(user_id=userId) [获取我的发布]
PostMapper -> MySQL: SELECT * FROM post WHERE user_id=?
MySQL --> PostMapper: 返回帖子列表
UserService -> UserPostCollectMapper: selectList(user_id=userId) [获取收藏ID]
UserPostCollectMapper -> MySQL: SELECT * FROM user_post_collect WHERE user_id=?
MySQL --> UserPostCollectMapper: 返回收藏记录
UserService -> PostMapper: selectList(id IN collectIds) [获取收藏帖子]
PostMapper -> MySQL: SELECT * FROM post WHERE id IN (?)
MySQL --> PostMapper: 返回收藏帖子列表
UserService -> UserPostLikesMapper: selectList(user_id=userId) [获取点赞ID]
UserPostLikesMapper -> MySQL: SELECT * FROM user_post_likes WHERE user_id=?
MySQL --> UserPostLikesMapper: 返回点赞记录
UserService -> PostMapper: selectList(id IN likeIds) [获取点赞帖子]
PostMapper -> MySQL: SELECT * FROM post WHERE id IN (?)
MySQL --> PostMapper: 返回点赞帖子列表
UserService -> BeanUtil: copyProperties(loginUser, UserMessageVO)
UserService --> 后端: UserMessageVO (含帖子列表/收藏列表/点赞列表)
后端 --> 前端: Response<UserMessageVO>
前端 --> 用户: 渲染个人主页
```

### 3.4 获取当前登录用户信息流程

```
用户 -> 前端: 请求当前用户信息
前端 -> 后端(UserController): GET /user/getUser
后端 -> UserService: getLoginUser(request)
UserService -> LoginUser: getLoginUser(request)
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON (命中)
LoginUser -> JSONUtil: toBean(UserJSON, User.class)
JSONUtil --> LoginUser: 返回User对象
LoginUser --> UserService: 返回User对象
后端 -> BeanUtil: copyProperties(user, UserLoginVO)
后端 --> 前端: Response<UserLoginVO>
前端 --> 用户: 显示用户信息
```

### 3.5 发布帖子流程

```
用户 -> 前端: 填写帖子内容并上传图片
前端 -> 后端(PictureController): POST /picture/post (MultipartFile)
PictureController: 校验文件大小(<=5MB)
PictureController -> PictureService: uploadPicture4Post(file, request)
PictureService -> LoginUser: getLoginUser(request) [获取当前用户]
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> PictureService: 返回User对象
PictureService -> CosService: 上传文件到腾讯云COS
CosService --> PictureService: 返回图片URL
PictureService -> PictureMapper: INSERT INTO picture (userId, url, width, height, size)
PictureMapper -> MySQL: 插入图片记录
MySQL --> PictureMapper: 返回插入结果(含pictureId)
PictureService --> 前端: Response<PicturePostVO> (url + pictureId)

用户 -> 前端: 提交帖子(含图片ID列表)
前端 -> 后端(PostController): POST /post/post (UploadPostRequest)
PostController -> PostService: uploadPost(uploadPostRequest, request)
PostService -> LoginUser: getLoginUser(request) [获取当前用户]
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> PostService: 返回User对象
PostService -> PostMapper: INSERT INTO post (userId, title, content, cover, isPrivate)
PostMapper -> MySQL: 插入帖子记录
MySQL --> PostMapper: 返回插入结果(含postId)
PostService -> PictureService: setPicturePostId(imageIds, postId)
PictureService -> PictureMapper: UPDATE picture SET post_id=? WHERE id IN (?)
PictureMapper -> MySQL: 更新图片postId
MySQL --> PictureMapper: 更新成功
PostService --> 后端: Response<Boolean>
后端 --> 前端: 发布成功
前端 --> 用户: 显示成功并跳转
```

### 3.6 获取帖子列表流程

```
用户 -> 前端: 浏览社区广场/加载帖子列表
前端 -> 后端(PostController): POST /post/postList (PostQueryRequest)
PostController -> PostService: getPostList(postQueryRequest)
PostService -> PostService: 构造分页查询条件
PostService -> PostMapper: selectPage(Page, QueryWrapper)
PostMapper -> MySQL: SELECT * FROM post LIMIT offset, pageSize
MySQL --> PostMapper: 返回分页帖子列表
PostService -> 关联查询用户信息 (userId -> username, avatar)
PostService -> 关联查询封面图片 (cover -> url)
PostService -> 转换PostListVO
PostService --> 后端: Response<IPage<PostListVO>>
后端 --> 前端: 帖子列表数据
前端 --> 用户: 渲染帖子列表(瀑布流/卡片)
```

### 3.7 获取帖子详情流程

```
用户 -> 前端: 点击帖子查看详情
前端 -> 后端(PostController): GET /post/getPost?id=xxx
PostController -> PostService: getPost(id)
PostService -> PostMapper: selectById(id)
PostMapper -> MySQL: SELECT * FROM post WHERE id=?
MySQL --> PostMapper: 返回帖子数据
PostService -> PictureChildMapper: list(postId=id, order by sortNum)
PictureChildMapper -> MySQL: SELECT * FROM picture_child WHERE post_id=? ORDER BY sort_num
MySQL --> PictureChildMapper: 返回图片关联列表(pictureIds)
PostService -> PictureMapper: list(id IN pictureIds)
PictureMapper -> MySQL: SELECT * FROM picture WHERE id IN (...)
MySQL --> PictureMapper: 返回图片数据
PostService -> 同步过滤: 构建urlMap，遍历pictureIds，同时过滤已删除图片，保证pictureUrl和pictureIds一一对应
PostService -> UserMapper: selectById(post.userId)
UserMapper -> MySQL: SELECT * FROM user WHERE id=?
MySQL --> UserMapper: 返回用户数据
PostService -> 组装PostDetailVO (帖子+用户+pictureUrl列表+pictureIds列表)
PostService --> 后端: Response<PostDetailVO>
后端 --> 前端: 帖子详情数据
前端 --> 用户: 渲染帖子详情
```

### 3.8 点赞帖子流程

```
用户 -> 前端: 点击点赞按钮
前端 -> 后端(PostController): POST /post/like?id=xxx
PostController -> PostService: likePost(id, request)
PostService -> LoginUser: getLoginUser(request) [获取当前用户]
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> PostService: 返回User对象
PostService -> UserPostLikesMapper: selectOne(userId, postId)
UserPostLikesMapper -> MySQL: 查询是否已点赞
MySQL --> UserPostLikesMapper: 返回查询结果
PostService -> 判断点赞状态:
  [未点赞] -> UserPostLikesMapper: INSERT (userId, postId)
            -> PostMapper: UPDATE post SET likes_num = likes_num + 1
  [已点赞] -> UserPostLikesMapper: DELETE (userId, postId)
            -> PostMapper: UPDATE post SET likes_num = likes_num - 1
MySQL --> PostService: 操作成功
PostService --> 后端: Response<Boolean>
后端 --> 前端: 点赞/取消成功
前端 --> 用户: 更新点赞状态和数量
```

### 3.9 上传头像流程

```
用户 -> 前端: 选择图片作为头像
前端 -> 后端(PictureController): POST /picture/avatar (MultipartFile + id)
PictureController: 校验文件非空、文件大小(<=5MB)、用户ID非空
PictureController -> PictureService: uploadAvatar(file, id, request)
PictureService -> LoginUser: getLoginUser(request) [权限校验]
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> PictureService: 返回User对象
PictureService -> UserService: isMe(id, request) [只能修改自己头像, 管理员除外]
PictureService -> CosService: 上传文件到腾讯云COS
CosService --> PictureService: 返回图片URL
PictureService -> UserMapper: updateById(id, avatar=url)
UserMapper -> MySQL: UPDATE user SET avatar=? WHERE id=?
MySQL --> UserMapper: 更新成功
PictureService --> 后端: Response<String> (新头像URL)
后端 --> 前端: 上传成功
前端 --> 用户: 显示新头像
```

### 3.10 编辑个人信息流程

```
用户 -> 前端: 填写编辑表单
前端 -> 后端(UserController): POST /user/editUser (UserEditRequest)
后端 -> UserService: editMyself(userEditRequest, request)
UserService -> LoginUser: getLoginUser(request) [获取登录用户]
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> UserService: 返回User对象
UserService -> UserService: isMe(id, request) [校验是否是自己的信息]
UserService -> UserService: 校验昵称长度(5-11位)
UserService -> UserService: 校验账号长度(6-11位)
UserService -> UserService: 校验密码长度(8-20位)
UserService -> UserService: getById(id) [查询用户]
UserService -> MySQL: SELECT * FROM user WHERE id=?
MySQL --> UserService: 返回用户数据
UserService -> BeanUtil: copyProperties(userEditRequest, user, ignoreNullValue)
UserService -> DigestUtil: md5Hex(password + SALT) [如果需要更新密码]
UserService -> UserService: updateById(user)
UserService -> MySQL: UPDATE user SET ...
MySQL --> UserService: 返回更新结果
UserService --> 后端: Response<Boolean>
后端 --> 前端: 编辑成功
前端 --> 用户: 显示成功并刷新信息
```

### 3.11 管理员获取用户列表流程

```
管理员 -> 前端: 访问用户管理页面
前端 -> 后端(UserController): POST /user/admin/userList (UserQueryWrapper)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
AuthInterceptor -> LoginUser: getLoginUser(request)
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> AuthInterceptor: 返回User对象
AuthInterceptor -> AuthInterceptor: 校验用户角色是否为admin
AuthInterceptor -> 后端: 权限通过
后端 -> UserService: getUserList(userQueryWrapper, current, pageSize)
UserService -> UserService: newQueryWrapper(userQueryWrapper) [构造查询条件]
UserService -> UserMapper: selectPage(Page, QueryWrapper)
UserMapper -> MySQL: SELECT * FROM user LIMIT offset,pageSize
MySQL --> UserMapper: 返回分页数据
UserMapper --> UserService: IPage<User>
UserService --> 后端: 返回分页结果
后端 --> 前端: Response<IPage<User>>
前端 --> 管理员: 渲染用户列表表格
```

### 3.12 管理员封禁/解封用户流程

```
管理员 -> 前端: 点击封禁/解封按钮
前端 -> 后端(UserController): POST /user/admin/setStatus (UserIdRequest)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
AuthInterceptor -> LoginUser: getLoginUser(request)
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> AuthInterceptor: 返回User对象
AuthInterceptor -> AuthInterceptor: 校验用户角色是否为admin
AuthInterceptor -> 后端: 权限通过
后端 -> UserService: setStatus(userId)
UserService -> UserMapper: selectById(userId)
UserMapper -> MySQL: SELECT * FROM user WHERE id=?
MySQL --> UserMapper: 返回用户数据
UserMapper --> UserService: 返回User对象
UserService: 切换用户状态(1→0 或 0→1)
UserService -> UserMapper: updateById(user)
UserMapper -> MySQL: UPDATE user SET status=?
MySQL --> UserMapper: 返回更新结果
UserMapper --> UserService: 返回成功
UserService --> 后端: Response<Boolean>
后端 --> 前端: 返回操作结果
前端 --> 管理员: 显示操作成功并刷新列表
```

### 3.13 管理员编辑用户信息流程

```
管理员 -> 前端: 填写编辑表单
前端 -> 后端(UserController): POST /user/admin/editUser (UserEditByAdminRequest)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
AuthInterceptor -> LoginUser: getLoginUser(request)
LoginUser -> HttpSession: getAttribute(TOKEN_KEY)
HttpSession --> LoginUser: 返回userId
LoginUser -> Redis: get(USER_ID:{userId})
Redis --> LoginUser: 返回UserJSON
LoginUser --> AuthInterceptor: 返回User对象
AuthInterceptor -> AuthInterceptor: 校验用户角色是否为admin
AuthInterceptor -> 后端: 权限通过
后端 -> UserService: editUser(userEditByAdminRequest)
UserService -> UserMapper: selectById(id)
UserMapper -> MySQL: SELECT * FROM user WHERE id=?
MySQL --> UserMapper: 返回用户数据
UserMapper --> UserService: 返回User对象
UserService: 判断是否需要更新密码
UserService -> DigestUtil: MD5加密新密码(如需要)
UserService -> BeanUtil: copyProperties(userEditByAdminRequest, user, ignoreNullValue)
UserService: 优化头像(如为null则设置默认头像)
UserService -> UserMapper: updateById(user)
UserMapper -> MySQL: UPDATE user SET ...
MySQL --> UserMapper: 返回更新结果
UserMapper --> UserService: 返回成功
UserService --> 后端: Response<Boolean>
后端 --> 前端: 返回编辑成功
前端 --> 管理员: 显示成功并刷新列表
```

### 3.14 获取系统分类列表流程

```
用户 -> 前端: 加载社区分类
前端 -> 后端(SystemController): GET /system/list
SystemController -> PicSystemService: getTypeList()
PicSystemService -> Redis: get(type_list_key)
Redis --> PicSystemService: 返回JSON字符串
PicSystemService -> JSONUtil: toBeanList(json, String.class)
JSONUtil --> PicSystemService: 返回List<String>
PicSystemService --> 后端: Response<List<String>>
后端 --> 前端: 分类列表数据
前端 --> 用户: 显示分类标签
```

### 3.15 获取跑马灯图片流程

```
用户 -> 前端: 加载首页
前端 -> 后端(SystemController): GET /system/marquee
SystemController -> PicSystemService: getMarquess()
PicSystemService -> Redis: get(marquees_key)
Redis --> PicSystemService: 返回JSON字符串
PicSystemService -> JSONUtil: toBeanList(json, String.class)
JSONUtil --> PicSystemService: 返回List<String>
PicSystemService --> 后端: Response<List<String>>
后端 --> 前端: 跑马灯图片URL列表
前端 --> 用户: 渲染首页跑马灯轮播
```

### 3.16 管理员添加分类标签流程

```
管理员 -> 前端: 输入新标签并提交
前端 -> 后端(SystemController): POST /system/addList (AddSysPicType)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
AuthInterceptor -> LoginUser: getLoginUser(request)
LoginUser -> AuthInterceptor: 返回User对象
AuthInterceptor -> 后端: 权限通过
SystemController -> PicSystemService: addTypeList(addSysPicType)
PicSystemService -> Redis: get(type_list_key)
Redis --> PicSystemService: 返回已有标签JSON（或null）
PicSystemService -> JSONUtil: 解析已有标签列表
PicSystemService: 合并新标签（去重）
PicSystemService -> Redis: set(type_list_key, 新JSON)
PicSystemService -> PicSystemMapper: 更新/插入数据库
PicSystemService --> 后端: Response<Boolean>
后端 --> 前端: 添加成功
前端 --> 管理员: 显示成功并刷新列表
```

### 3.17 管理员创建空间流程

```
管理员 -> 前端: 填写空间信息并提交
前端 -> 后端(SpaceController): POST /space/create (CreateSpace)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
AuthInterceptor -> LoginUser: getLoginUser(request)
LoginUser -> AuthInterceptor: 返回User对象
AuthInterceptor -> 后端: 权限通过
SpaceController -> SpaceService: createSpace(createSpace, request)
SpaceService -> LoginUser: getLoginUser(request)
LoginUser -> SpaceService: 返回User对象
SpaceService: 根据type设置默认存储大小 (PRIVATE:512MB / TEAM:5GB)
SpaceService -> SpaceMapper: INSERT INTO space (name, introduction, type, userId, storageSize, level)
SpaceMapper -> MySQL: 插入空间记录
MySQL --> SpaceMapper: 返回插入结果
SpaceService --> 后端: Response<Boolean>
后端 --> 前端: 创建成功
前端 --> 管理员: 显示成功并刷新列表
```

### 3.18 获取公开图片列表流程

```
用户 -> 前端: 浏览图片库
前端 -> 后端(PictureController): GET /picture/list?current=1&pageSize=20
PictureController -> PictureService: getPictureList(current, pageSize)
PictureService -> PictureMapper: selectPage(Page, QueryWrapper<status=1>)
PictureMapper -> MySQL: SELECT * FROM picture WHERE status=1 AND is_private=0 LIMIT offset,pageSize
MySQL --> PictureMapper: 返回分页图片列表
PictureService -> 转换PictureListVO (id, url)
PictureService --> 后端: Response<IPage<PictureListVO>>
后端 --> 前端: 图片列表数据
前端 --> 用户: 渲染图片列表
```

### 3.19 管理员图片审核流程

```
管理员 -> 前端: 选择图片并操作（通过/拒绝/精选）
前端 -> 后端(PictureController): POST /picture/admin/review?pictureId=xxx&status=1&selected=false
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
AuthInterceptor -> LoginUser: getLoginUser(request)
LoginUser -> AuthInterceptor: 返回User对象
AuthInterceptor -> 后端: 权限通过
PictureController -> PictureService: reviewPicture(pictureId, status, selected)
PictureService -> PictureMapper: selectById(pictureId)
PictureMapper -> MySQL: 查询图片
MySQL --> PictureMapper: 返回图片信息
PictureService: 更新图片status和selected字段
PictureService -> PictureMapper: updateById(picture)
PictureMapper -> MySQL: UPDATE picture SET status=?, selected=?
MySQL --> PictureMapper: 更新成功
PictureService --> 后端: Response<Boolean>
后端 --> 前端: 审核成功
前端 --> 管理员: 显示成功并刷新列表
```

---

## 4. 状态图 (State Diagram)

### 4.1 用户状态图

```
[正常(1)] --(管理员封禁)--> [禁用(0)]
[禁用(0)] --(管理员解封)--> [正常(1)]
[正常(1)/禁用(0)] --(管理员编辑)--> [正常(1)/禁用(0)]
[正常/禁用] --(逻辑删除)--> [删除(isDelete=1)]
```

### 4.2 内容状态图 (图片/帖子/评论通用)

```
[正常(1)] --(禁用操作)--> [禁用(0)]
[禁用(0)] --(启用操作)--> [正常(1)]
[正常/禁用] --(待审核)--> [待审核(2)]
[正常/禁用/待审核] --(删除操作)--> [逻辑删除(isDelete=1)]
```

### 4.3 Spring Session状态图

```
[未登录] --(登录成功)--> [已登录(Spring Session存储用户, Redis持久化)]
[已登录] --(Spring Session过期)--> [需重新登录]
[已登录] --(退出登录, 使Session失效)--> [未登录]
[已登录] --(Session失效)--> [未登录]
```

### 4.4 帖子隐私状态图

```
[公开(0)] --(用户修改为仅自己可见)--> [仅自己可见(1)]
[仅自己可见(1)] --(用户修改为公开)--> [公开(0)]
```

### 4.5 图片首页展示状态图

```
[公开到首页(1)] --(用户修改为不公开)--> [不公开(0)]
[不公开(0)] --(用户修改为公开到首页)--> [公开到首页(1)]
```

---

## 5. 组件图 (Component Diagram)

### 5.1 前端组件 (FishPic-frontend)

#### 核心框架

- **React 19**: UI框架
- **Ant Design 6**: UI组件库
- **React Router v7**: 路由管理
- **Context API**: 认证状态管理 (AuthContext)、主题状态管理 (ThemeContext)
- **Axios**: HTTP客户端
- **localStorage**: 用户信息持久化
- **Vite 8**: 构建工具

#### 页面组件

- **HomePage.jsx**: 首页/登录/注册
- **CommunitySquare.jsx**: 社区广场 (帖子瀑布流展示，滚动100px后显示返回顶部按钮)
- **PrivateSpace.jsx**: 个人空间
- **TeamSpace.jsx**: 团队空间
- **Notifications.jsx**: 通知消息
- **UserProfile.jsx**: 用户资料
- **UserManagement.jsx**: 用户管理
- **AdminUserList.jsx**: 管理员用户列表
- **AdminPictureManagement.jsx**: 管理员图片管理
- **SystemManagement.jsx**: 系统管理（标签/跑马灯）
- **TeamManagement.jsx**: 团队管理（开发中）
- **SpaceManagement.jsx**: 空间管理（开发中）
- **AIManagement.jsx**: AI管理（开发中）
- **NotFound.jsx**: 404页面

#### 通用组件

- **GlobalLayout.jsx**: 全局布局组件
- **ProtectedRoute.jsx**: 路由保护组件
- **ErrorBoundary.jsx**: 错误边界组件
- **FunnyBackground.jsx**: 趣味背景动画
- **PostDetailModal.jsx**: 帖子详情弹窗
- **CreateEditPostModal.jsx**: 帖子发布/编辑弹窗
- **api/index.js**: API请求封装 (Axios配置、拦截器、所有接口方法)
- **utils/storage.js**: localStorage操作工具

### 5.2 后端组件 (FishPics-backend)

#### 核心框架

- **Spring Boot 2.7.6**: Web框架
- **MyBatis-Plus 3.5.15**: ORM框架 + 分页插件
- **MySQL 8+**: 关系型数据库
- **Redis**: 缓存服务 (验证码、Session存储、用户信息缓存、系统配置缓存)
- **Spring Session + Redis**: Session管理（Session中仅存userId，User缓存在Redis）
- **Redisson 3.24.3**: Redis分布式锁
- **腾讯云COS 5.6.227**: 对象存储服务 (图片存储)
- **Hutool 5.8.38**: 工具库 (加密、验证码、JSON、Bean拷贝)
- **Knife4j 4.4.0**: API文档生成
- **Lombok**: 代码简化

#### Controller 层

- **UserController**: 用户相关接口 (11个接口)
- **PostController**: 帖子相关接口 (5个接口)
- **PictureController**: 图片相关接口 (6个接口)
- **SpaceController**: 空间相关接口 (4个接口)
- **SystemController**: 系统相关接口 (6个接口)

#### Service 层

- **UserService/Impl**: 用户业务逻辑 (11个方法)
- **PostService/Impl**: 帖子业务逻辑 (9个方法，含myPosts/myCollects/myLikes)
- **PictureService/Impl**: 图片业务逻辑 (7个方法，含list/admin/review/delete)
- **SpaceService/Impl**: 空间业务逻辑 (5个方法)
- **PicSystemService/Impl**: 系统配置业务逻辑 (6个方法，标签+跑马灯)
- **CommentService/Impl**: 评论业务逻辑
- **UserPostCollectService/Impl**: 帖子收藏业务
- **UserPostLikesService/Impl**: 帖子点赞业务
- **UserFansService/Impl**: 粉丝业务逻辑
- **LoginUser**: 登录用户获取工具（Session + Redis）
- **CosService**: 腾讯云COS对象存储服务

#### Mapper 层

- **UserMapper**: 用户数据访问
- **PostMapper**: 帖子数据访问
- **PictureMapper**: 图片数据访问
- **PictureChildMapper**: 子图片关联数据访问
- **CommentMapper**: 评论数据访问
- **SpaceMapper**: 空间数据访问
- **PicSystemMapper**: 系统配置数据访问
- **UserFansMapper**: 粉丝数据访问
- **UserPostCollectMapper**: 收藏数据访问
- **UserPostLikesMapper**: 点赞数据访问

#### DTO 层

- **user/**: UserLoginRequest, UserRequestRequest, UserEditRequest, UserEditByAdminRequest, UserQueryWrapper, UserIdRequest
- **post/**: UploadPostRequest, EditPostRequest, PostQueryRequest, PostQueryWrapper
- **picture/**: PictureMessage, DeleteByIdList
- **space/**: CreateSpace, UpdateSpace, SpacePictureList, SpaceQueryWrapper
- **system/**: AddSysPicType, AddSysMarquee
- **base/**: PageRequest, DeleteById

#### VO 层

- **user/**: UserLoginVO, UserMessageVO, CheckCodeVO
- **post/**: PostListVO, PostDetailVO
- **picture/**: PicturePostVO, PictureAdminVO, PictureListVO, PicturePageVO

#### Entity 层

- **User**: 用户实体（含level, size字段）
- **Post**: 帖子实体（含hot热度字段）
- **Picture**: 图片实体（含spaceId, introduction字段，size为Long类型）
- **Comment**: 评论实体
- **Space**: 空间实体（含type, level, storageSize等）
- **PicSystem**: 系统配置实体（syskey, sysvalue）
- **UserFans**: 用户粉丝实体
- **UserPostCollect**: 用户帖子收藏实体
- **UserPostLikes**: 用户帖子点赞实体

#### 公共组件

- **common/annotation**: AuthCheck (权限校验注解)
- **common/aop**: AuthInterceptor (权限拦截器)
- **common/config**: COSConfig, CorsConfig, JsonConfig, MybatisPlusConfig, SessionRedisConfig
- **common/constants**: RedisConstants, SpaceConstants, SysConstants, UserConstants
- **common/exception**: BaseException, ExceptionCode, ExcUtils, GlobalExceptionHandler
- **common/response**: Response, ResUtils
- **common/utils**: LimitedInputStream (受限输入流)

#### 枚举类

- **enums/UserRoleEnum**: 用户角色 (admin, user)

### 5.3 组件依赖关系

```
前端(React 19 + Ant Design 6)
  ↓ HTTP请求 (Axios, 携带Cookie/Session)
Vite Dev Server (开发模式)
  ↓ 代理配置 (/api)
Spring Boot (FishPics-backend)
  ├── 控制器层 (UserController, PostController, PictureController, SpaceController, SystemController)
  │     ↓ 依赖
  ├── 服务层 (UserService/Impl, PostService/Impl, PictureService/Impl, SpaceService/Impl, PicSystemService/Impl等)
  │     ├── 依赖 Redis (验证码/Session存储/用户信息缓存/系统配置缓存)
  │     ├── 依赖 LoginUser (从Session获取userId，从Redis获取User)
  │     ├── 依赖 Hutool (工具类)
  │     ├── 依赖 CosService (图片存储)
  │     └── 依赖
  ├── 数据访问层 (MyBatis-Plus Mapper)
  │     ↓ 依赖
  ├── MySQL数据库 (FishPics, 10张表)
  ├── Redis缓存服务器 (192.168.163.101:6379, Session存储+业务缓存)
  ├── Redisson (分布式锁)
  └── 腾讯云COS (对象存储服务)
```

---

## 6. 部署图 (Deployment Diagram)

### 6.1 物理节点

| 节点               | 说明                         | 地址/端口            |
| ------------------ | ---------------------------- | -------------------- |
| **客户端**         | Web浏览器 (PC/Mobile)        | -                    |
| **前端开发服务器** | Vite Dev Server              | localhost:5173       |
| **后端API服务**    | Spring Boot 2.7.6            | localhost:8080       |
| **API上下文路径**  | 接口路径前缀                 | /api                 |
| **数据库服务器**   | MySQL 8+                     | localhost:3306       |
| **缓存服务器**     | Redis (Session存储+业务缓存) | 192.168.163.101:6379 |
| **对象存储**       | 腾讯云COS                    | 云端服务             |

### 6.2 网络连接

```
客户端 (浏览器)
  ↓ HTTP/HTTPS (携带Spring Session Cookie: SESSION)
Vite Dev Server (localhost:5173)
  ↓ 代理配置 /api -> localhost:8080/api
Spring Boot (localhost:8080/api)
  ├── ↓ JDBC (MySQL协议)
  │   MySQL (localhost:3306 / FishPics数据库)
  ├── ↓ Redis协议 (Spring Session存储/验证码/业务缓存)
  │   Redis (192.168.163.101:6379)
  └── ↓ HTTPS (腾讯云COS SDK)
      腾讯云COS (云端对象存储)
```

### 6.3 端口配置

| 服务           | 端口      | 协议                              |
| -------------- | --------- | --------------------------------- |
| 前端开发服务器 | 5173      | HTTP                              |
| 后端API服务    | 8080      | HTTP                              |
| MySQL          | 3306      | TCP (JDBC)                        |
| Redis          | 6379      | TCP (Redis Protocol, Session存储) |
| 腾讯云COS      | 云端HTTPS | HTTPS                             |

---

## 7. 活动图 (Activity Diagram)

### 7.1 用户注册活动流

```
开始
  ↓
填写用户名(6-11位)
  ↓
填写密码(8-20位)
  ↓
确认密码(必须一致)
  ↓
输入验证码
  ↓
勾选用户协议(如需要)
  ↓
前端校验(参数格式验证)
  ↓ [通过]
POST /user/register
  ↓
后端验证参数非空
  ↓ [通过]
后端验证验证码格式
  ↓
后端验证验证码正确性(查Redis)
  ↓ [通过]
检查用户名是否已存在(查MySQL)
  ↓ [不存在]
密码MD5加密(加盐: fish)
  ↓
生成默认昵称(小鱼籽_+随机6位)
  ↓
设置默认头像URL
  ↓
插入用户表(MySQL)
  ↓ [成功]
删除Redis验证码
  ↓
返回注册成功
  ↓
结束
```

### 7.2 用户登录活动流

```
开始
  ↓
填写用户名
  ↓
填写密码
  ↓
输入验证码
  ↓
前端校验(非空检查)
  ↓ [通过]
POST /user/login
  ↓
后端参数校验
  ↓ [通过]
后端获取验证码(查Redis)
  ↓ [通过]
密码MD5加密(加盐: fish)
  ↓
查询用户表(MySQL: username+password)
  ↓ [找到匹配记录]
将userId存储到HttpSession
  ↓
将User JSON存储到Redis (KEY: USER_ID:{userId})
  ↓
封装UserLoginVO(不含token)
  ↓
返回登录成功
  ↓
前端保存用户信息到localStorage
  ↓
登录成功
  ↓
结束
```

### 7.3 获取个人主页活动流

```
开始
  ↓
用户访问个人主页
  ↓
GET /user/myself
  ↓
从HttpSession获取userId (TOKEN_KEY)
  ↓
根据userId从Redis获取User JSON (KEY: USER_ID:{userId})
  ↓ [Redis命中]
反序列化为User对象
  ↓ [找到用户]
查询我的帖子列表(MySQL: user_id)
  ↓
转换为PostListVO列表
  ↓
查询我的收藏ID列表(MySQL: user_id)
  ↓
根据ID列表查询收藏帖子
  ↓
转换为PostListVO列表
  ↓
查询我的点赞ID列表(MySQL: user_id)
  ↓
根据ID列表查询点赞帖子
  ↓
转换为PostListVO列表
  ↓
封装UserMessageVO
  ↓
返回成功
  ↓
结束
```

### 7.4 发布帖子活动流

```
开始
  ↓
[步骤1: 上传图片]
选择图片文件
  ↓
前端校验文件大小(<=5MB)
  ↓ [通过]
POST /picture/post (multipart/form-data)
  ↓
后端校验文件非空、大小(<=5MB)
  ↓ [通过]
从HttpSession获取userId，再从Redis获取User对象 (LoginUser)
  ↓
上传图片到腾讯云COS
  ↓ [成功]
获取图片URL
  ↓
插入图片记录到MySQL(userId, url, width, height, size)
  ↓
返回PicturePostVO(url, pictureId)
  ↓
[步骤2: 提交帖子]
填写标题、内容，选择封面，设置隐私
  ↓
POST /post/post (UploadPostRequest)
  ↓
后端校验参数非空
  ↓ [通过]
从HttpSession获取userId，再从Redis获取User对象 (LoginUser)
  ↓
插入帖子记录到MySQL(userId, title, content, cover, isPrivate)
  ↓
获取生成的帖子ID
  ↓
更新图片记录，设置postId关联
  ↓
返回发布成功
  ↓
结束
```

### 7.5 获取帖子列表活动流

```
开始
  ↓
用户浏览社区广场
  ↓
POST /post/postList (PostQueryRequest)
  ↓
解析请求参数(userId, text, hotPost, current, pageSize)
  ↓
构造查询条件:
  ├─ 按userId筛选(如果提供)
  ├─ 按text模糊搜索title/content(如果提供)
  ├─ 按hotPost排序(热门优先)
  └─ 分页参数
  ↓
分页查询帖子表(MySQL)
  ↓ [获取IPage<Post>]
遍历帖子列表:
  ├─ 查询用户名和头像(userId -> User)
  ├─ 获取封面图片URL(cover -> Picture.url)
  └─ 组装PostListVO
  ↓
返回分页结果
  ↓
前端渲染瀑布流/卡片列表
  ↓
结束
```

### 7.6 点赞帖子活动流

```
开始
  ↓
用户点击帖子点赞按钮
  ↓
POST /post/like?id=xxx
  ↓
从HttpSession获取userId，再从Redis获取User对象 (LoginUser)
  ↓
查询是否已点赞(userId + postId)
  ↓
[判断点赞状态]
  ├─→ [未点赞]
  │     ├─ 插入点赞记录(user_post_likes)
  │     ├─ 帖子点赞数+1 (UPDATE post SET likes_num = likes_num + 1)
  │     └─ 返回成功
  │
  └─→ [已点赞]
        ├─ 删除点赞记录(DELETE FROM user_post_likes)
        ├─ 帖子点赞数-1 (UPDATE post SET likes_num = likes_num - 1)
        └─ 返回成功
  ↓
前端更新点赞状态和数量
  ↓
结束
```

### 7.7 用户管理活动流

```
开始
  ↓
验证管理员权限(role=admin)
  ↓ [通过 @AuthCheck]
AuthInterceptor从HttpSession获取userId，从Redis获取User对象 (LoginUser)
  ↓
校验用户角色是否为admin
  ↓ [通过]
加载用户管理页面
  ↓
输入搜索条件(可选)
  ↓
POST /user/admin/userList
  ↓
后端参数校验(非空检查)
  ↓
构造查询条件(QueryWrapper)
  ↓ [支持模糊匹配和精确匹配]
  ├─ like: id, username, email, phone, nickname
  └─ eq: role, status, create_time
  ↓ [支持排序]
  按sortField和sortOrder排序
  ↓
分页查询用户表(MySQL)
  ↓
返回IPage<User>
  ↓
渲染用户列表表格
  ↓
[管理员操作]
  ├─→ 获取用户详情: POST /user/admin/getUser
  │     └─ 返回User对象
  │
  ├─→ 编辑用户: POST /user/admin/editUser
  │     ├─ 校验用户ID存在
  │     ├─ 查询用户信息
  │     ├─ 密码加密(如修改)
  │     ├─ 拷贝非空字段
  │     └─ 更新用户
  │
  └─→ 封禁/解封: POST /user/admin/setStatus
        ├─ 查询用户信息
        ├─ 切换状态(1→0 或 0→1)
        └─ 更新状态
  ↓
刷新用户列表
  ↓
结束
```

---

## 8. 包图 (Package Diagram)

### 8.1 后端包结构

```
hk.ljx.fishpicsbackend
├── FishPicsBackendApplication.java (启动类)
├── common (公共组件)
│   ├── annotation (注解)
│   │   └── AuthCheck.java (权限校验注解, role属性)
│   ├── aop (切面)
│   │   └── AuthInterceptor.java (权限拦截器, 从Session获取userId + 从Redis获取User + 校验角色)
│   ├── config (配置)
│   │   ├── COSConfig.java (腾讯云COS配置)
│   │   ├── CorsConfig.java (跨域配置, 允许前端请求)
│   │   ├── JsonConfig.java (JSON序列化配置)
│   │   ├── MybatisPlusConfig.java (MyBatis分页插件配置)
│   │   └── SessionRedisConfig.java (Session存储到Redis配置)
│   ├── constants (常量)
│   │   ├── RedisConstants.java (Redis键常量: LOGIN_CODE_KEY, REGISTER_CODE_KEY, TOKEN_KEY, USER_ID, LIKE_POST)
│   │   ├── SpaceConstants.java (空间常量: PRIVATE_SIZE=512MB, TEAM_SIZE=5GB, PRIVATE/TEAM级别)
│   │   ├── SysConstants.java (系统配置常量: SYS_PIC_TYPE, SYS_MARQUEES)
│   │   └── UserConstants.java (用户常量: ADMIN, USER, SALT="fish", DEFAULT_NICK_NAME="小鱼籽_")
│   ├── exception (异常)
│   │   ├── BaseException.java (基础异常类)
│   │   ├── ExcUtils.java (异常工具类: throwIfTrue等)
│   │   ├── ExceptionCode.java (异常码枚举)
│   │   └── GlobalExceptionHandler.java (全局异常处理器)
│   ├── response (响应)
│   │   ├── ResUtils.java (响应工具类: success/error等)
│   │   └── Response.java (响应封装类: code, message, data)
│   └── utils (工具类)
│       └── LimitedInputStream.java (限制输入流)
├── controller (控制器)
│   ├── UserController.java (用户控制器, 11个接口)
│   ├── PostController.java (帖子控制器, 5个接口)
│   ├── PictureController.java (图片控制器, 6个接口)
│   ├── SpaceController.java (空间控制器, 4个接口)
│   └── SystemController.java (系统控制器, 6个接口)
├── dto (数据传输对象)
│   ├── base (基础请求)
│   │   ├── DeleteById.java (删除请求)
│   │   └── PageRequest.java (分页请求)
│   ├── picture (图片请求)
│   │   ├── DeleteByIdList.java (批量删除请求)
│   │   └── PictureMessage.java (图片消息)
│   ├── post (帖子请求)
│   │   ├── EditPostRequest.java (编辑帖子请求)
│   │   ├── PostQueryRequest.java (帖子查询请求, 继承PageRequest)
│   │   ├── PostQueryWrapper.java (帖子查询包装器)
│   │   └── UploadPostRequest.java (上传帖子请求)
│   ├── space (空间请求)
│   │   ├── CreateSpace.java (创建空间请求)
│   │   ├── UpdateSpace.java (更新空间请求)
│   │   ├── SpacePictureList.java (空间图片列表请求)
│   │   └── SpaceQueryWrapper.java (空间查询包装器)
│   ├── system (系统请求)
│   │   ├── AddSysPicType.java (添加分类标签请求)
│   │   └── AddSysMarquee.java (添加跑马灯请求)
│   └── user (用户请求)
│       ├── UserEditByAdminRequest.java (管理员编辑用户请求)
│       ├── UserEditRequest.java (用户编辑自己请求)
│       ├── UserIdRequest.java (用户ID请求)
│       ├── UserLoginRequest.java (登录请求)
│       ├── UserQueryWrapper.java (查询条件)
│       └── UserRequestRequest.java (注册请求)
├── entity (实体类)
│   ├── Comment.java (评论)
│   ├── Picture.java (图片, 含spaceId/introduction, size为Long类型)
│   ├── PictureChild.java (子图片关联, pictureId/postId/sortNum)
│   ├── Post.java (帖子, 含hot热度字段)
│   ├── User.java (用户, 含level/size字段)
│   ├── Space.java (空间, 含type/level/storageSize等)
│   ├── PicSystem.java (系统配置, syskey/sysvalue)
│   ├── UserFans.java (用户粉丝)
│   ├── UserPostCollect.java (用户帖子收藏)
│   └── UserPostLikes.java (用户帖子点赞)
├── enums (枚举)
│   └── UserRoleEnum.java (用户角色: admin, user)
├── mapper (数据访问层)
│   ├── CommentMapper.java
│   ├── PictureMapper.java
│   ├── PictureChildMapper.java
│   ├── PostMapper.java
│   ├── SpaceMapper.java
│   ├── PicSystemMapper.java
│   ├── UserFansMapper.java
│   ├── UserMapper.java
│   ├── UserPostCollectMapper.java
│   └── UserPostLikesMapper.java
├── service (业务逻辑层)
│   ├── CommentService.java
│   ├── CosService.java
│   ├── LoginUser.java (登录用户获取工具)
│   ├── PictureService.java
│   ├── PictureChildService.java
│   ├── PostService.java
│   ├── SpaceService.java
│   ├── PicSystemService.java
│   ├── UserFansService.java
│   ├── UserPostCollectService.java
│   ├── UserPostLikesService.java
│   ├── UserService.java
│   └── impl
│       ├── CommentServiceImpl.java
│       ├── PictureServiceImpl.java
│       ├── PostServiceImpl.java
│       ├── SpaceServiceImpl.java
│       ├── PicSystemServiceImpl.java
│       ├── UserFansServiceImpl.java
│       ├── UserPostCollectServiceImpl.java
│       ├── UserPostLikesServiceImpl.java
│       └── UserServiceImpl.java
└── vo (视图对象)
    ├── picture
    │   ├── PicturePostVO.java (图片上传响应)
    │   ├── PictureAdminVO.java (管理员图片视图)
    │   ├── PictureListVO.java (图片列表项)
    │   └── PicturePageVO.java (图片分页)
    ├── post
    │   ├── PostDetailVO.java (帖子详情)
    │   └── PostListVO.java (帖子列表项)
    └── user
        ├── CheckCodeVO.java (验证码)
        ├── UserLoginVO.java (登录用户)
        └── UserMessageVO.java (用户主页信息)
```

### 8.2 前端包结构

```
src/
├── api/
│   └── index.js (API请求封装, Axios配置, 所有接口方法)
├── assets/ (静态资源)
│   ├── hero.png
│   ├── react.svg
│   └── vite.svg
├── components/ (通用组件)
│   ├── ErrorBoundary.jsx (错误边界)
│   ├── FunnyBackground.css (趣味背景样式)
│   ├── FunnyBackground.jsx (趣味背景)
│   ├── GlobalLayout.jsx (全局布局)
│   ├── ProtectedRoute.jsx (路由保护)
│   ├── PostDetailModal.jsx (帖子详情弹窗)
│   └── CreateEditPostModal.jsx (帖子发布/编辑弹窗)
├── context/ (状态管理)
│   ├── AuthContext.jsx (认证上下文)
│   └── ThemeContext.jsx (主题上下文)
├── pages/ (页面组件)
│   ├── AIManagement.css
│   ├── AIManagement.jsx (AI管理, 开发中)
│   ├── AdminPictureManagement.css
│   ├── AdminPictureManagement.jsx (管理员图片管理)
│   ├── AdminUserList.css
│   ├── AdminUserList.jsx (管理员用户列表)
│   ├── CommunitySquare.css
│   ├── CommunitySquare.jsx (社区广场)
│   ├── HomePage.jsx (首页/登录/注册)
│   ├── NotFound.css
│   ├── NotFound.jsx (404页面)
│   ├── Notifications.css
│   ├── Notifications.jsx (通知消息)
│   ├── PrivateSpace.css
│   ├── PrivateSpace.jsx (个人空间)
│   ├── SpaceManagement.css
│   ├── SpaceManagement.jsx (空间管理, 开发中)
│   ├── SystemManagement.css
│   ├── SystemManagement.jsx (系统管理)
│   ├── TeamManagement.css
│   ├── TeamManagement.jsx (团队管理, 开发中)
│   ├── TeamSpace.css
│   ├── TeamSpace.jsx (团队空间)
│   ├── UserManagement.css
│   ├── UserManagement.jsx (用户管理)
│   ├── UserProfile.css
│   └── UserProfile.jsx (用户资料)
├── utils/ (工具函数)
│   └── storage.js (localStorage操作)
├── App.css (应用样式)
├── App.jsx (主应用组件)
├── index.css (全局样式)
└── main.jsx (入口文件)
```

### 8.3 包依赖关系

```
前端 (React)
  │
  ├── components → pages (组件被页面引用)
  ├── context → components (认证状态被保护路由引用)
  ├── api → pages (API被页面调用)
  └── utils → pages/组件 (工具函数被使用)

后端 (Spring Boot)
  │
  ├── controller → service (控制器调用服务)
  │     └── controller → dto/vo (数据传输)
  ├── service → mapper (服务调用数据访问)
  │     ├── service → entity (服务使用实体类)
  │     ├── service → dto (服务使用请求对象)
  │     ├── service → vo (服务使用视图对象)
  │     ├── service → common/utils (服务使用工具类)
  │     ├── PostService → PictureService (帖子服务调用图片服务)
  │     └── PictureService → CosService (图片服务调用COS存储)
  ├── mapper → entity (数据访问使用实体类)
  │     └── mapper → database (MyBatis映射)
  ├── common/aop → controller (切面拦截控制器)
  │     └── aop → HttpSession (从Session获取用户信息)
  └── common/exception → 全局 (异常处理)
```

---

## 9. 数据库模型

### 9.1 数据库表结构

#### user 表 (用户表)

| 字段                    | 类型            | 约束                      | 说明                                     |
| ----------------------- | --------------- | ------------------------- | ---------------------------------------- |
| id                      | bigint unsigned | PK, AUTO_INCREMENT        | 用户ID                                   |
| username                | varchar(32)     | UNIQUE                    | 账号（登录用）                           |
| password                | varchar(128)    | -                         | 密码(MD5+Salt)                           |
| avatar                  | varchar(256)    | -                         | 头像URL                                  |
| email                   | varchar(64)     | -                         | 邮箱                                     |
| phone                   | varchar(16)     | -                         | 手机号                                   |
| nickname                | varchar(32)     | UNIQUE                    | 昵称（展示用）                           |
| status                  | tinyint         | DEFAULT 1                 | 状态 1-正常 0-禁用 2-待审核              |
| is_delete               | tinyint         | DEFAULT 0                 | 0-逻辑未删除, 1-逻辑删除 (@TableLogic)   |
| role                    | varchar(32)     | DEFAULT 'user'            | 用户的权限                               |
| create_time             | datetime        | DEFAULT CURRENT_TIMESTAMP | 创建时间                                 |
| update_time             | datetime        | DEFAULT CURRENT_TIMESTAMP | 更新时间                                 |
| like_num                | bigint          | -                         | 点赞数                                   |
| collect_num             | bigint          | -                         | 收藏数                                   |
| is_private_follows      | tinyint         | -                         | 0-公开关注列表，1-不公开关注列表         |
| is_private_post_collect | tinyint         | -                         | 0-公开帖子列表，1-不公开帖子列表         |
| is_private_likes        | tinyint         | -                         | 0-公开点赞帖子列表，1-不公开点赞帖子列表 |
| is_private_fans         | tinyint         | -                         | 0-公开粉丝列表，1-不公开粉丝列表         |
| level                   | int             | DEFAULT 0                 | 用户级别 0-普通用户 1-VIP 2-SVIP         |
| size                    | bigint          | DEFAULT 0                 | 已用存储大小（字节）                     |

#### post 表 (帖子表)

| 字段         | 类型         | 约束                      | 说明                         |
| ------------ | ------------ | ------------------------- | ---------------------------- |
| id           | bigint       | PK, AUTO_INCREMENT        | 主键                         |
| user_id      | bigint       | NOT NULL, FK              | 关联用户表                   |
| title        | varchar(256) | NOT NULL                  | 标题                         |
| content      | text         | NOT NULL                  | 内容                         |
| status       | tinyint      | DEFAULT 1                 | 状态 1-正常 0-禁用 2-待审核  |
| create_time  | datetime     | DEFAULT CURRENT_TIMESTAMP | 创建时间                     |
| update_time  | datetime     | DEFAULT CURRENT_TIMESTAMP | 更新时间                     |
| is_delete    | int          | -                         | 0-未删除, 1-已删除           |
| likes_num    | bigint       | -                         | 点赞数                       |
| collects_num | bigint       | -                         | 收藏数                       |
| comment_num  | int          | -                         | 评论数                       |
| is_private   | tinyint      | -                         | 0-公开，1-仅自己可见         |
| cover        | bigint       | FK                        | 封面图片的id (关联picture表) |
| views_num    | bigint       | -                         | 查看数                       |
| hot          | decimal(10)  | DEFAULT 0                 | 热度值                       |

#### picture 表 (图片表)

| 字段         | 类型         | 约束                      | 说明                         |
| ------------ | ------------ | ------------------------- | ---------------------------- |
| id           | bigint       | PK, AUTO_INCREMENT        | 主键                         |
| user_id      | bigint       | NOT NULL, FK              | 用户id                       |
| picture_name | bigint       | NOT NULL                  | 图片名称                     |
| url          | varchar(512) | NOT NULL                  | 图片地址                     |
| width        | varchar(32)  | -                         | 宽度                         |
| height       | varchar(32)  | -                         | 高度                         |
| size         | bigint       | -                         | 文件大小（字节）             |
| status       | tinyint      | DEFAULT 2                 | 状态 1-正常 0-禁用 2-待审核  |
| create_time  | datetime     | DEFAULT CURRENT_TIMESTAMP | 创建时间                     |
| update_time  | datetime     | DEFAULT CURRENT_TIMESTAMP | 更新时间                     |
| is_private   | tinyint      | -                         | 0-不公开到首页，1-公开到首页 |
| post_id      | bigint       | FK                        | 帖子id (关联post表)          |
| space_id     | bigint       | FK                        | 空间id (关联space表)         |
| introduction | varchar(256) | -                         | 图片简介                     |

#### picture_child 表 (子图片关联表)

| 字段       | 类型    | 约束               | 说明               |
| ---------- | ------- | ------------------ | ------------------ |
| id         | bigint  | PK, AUTO_INCREMENT | 主键               |
| picture_id | bigint  | NOT NULL, FK       | 关联图片ID         |
| post_id    | bigint  | NOT NULL, FK       | 关联帖子ID         |
| sort_num   | int     | -                  | 在帖子中的排序序号 |

#### comment 表 (评论表)

| 字段        | 类型     | 约束                      | 说明                        |
| ----------- | -------- | ------------------------- | --------------------------- |
| id          | bigint   | PK, AUTO_INCREMENT        | 主键                        |
| user_id     | bigint   | NOT NULL, FK              | 关联用户表                  |
| post_id     | bigint   | NOT NULL, FK              | 关联帖子表                  |
| content     | text     | NOT NULL                  | 评论内容                    |
| parent_id   | bigint   | -                         | 父评论（支持二级评论/回复） |
| to_user_id  | int      | -                         | 回复给谁                    |
| status      | tinyint  | DEFAULT 1                 | 状态 1-正常 0-禁用 2-待审核 |
| create_time | datetime | DEFAULT CURRENT_TIMESTAMP | 创建时间                    |

#### user_fans 表 (用户粉丝表)

| 字段    | 类型   | 约束 | 说明   |
| ------- | ------ | ---- | ------ |
| id      | bigint | PK   | 主键   |
| user_id | bigint | FK   | 用户ID |
| fan_id  | bigint | FK   | 粉丝ID |

#### user_post_collect 表 (用户帖子收藏表)

| 字段    | 类型   | 约束 | 说明   |
| ------- | ------ | ---- | ------ |
| id      | bigint | PK   | 主键   |
| user_id | bigint | FK   | 用户ID |
| post_id | bigint | FK   | 帖子ID |

#### user_post_likes 表 (用户帖子点赞表)

| 字段    | 类型   | 约束 | 说明   |
| ------- | ------ | ---- | ------ |
| id      | bigint | PK   | 主键   |
| user_id | bigint | FK   | 用户ID |
| post_id | bigint | FK   | 帖子ID |

#### space 表 (空间表)

| 字段          | 类型         | 约束                      | 说明                                |
| ------------- | ------------ | ------------------------- | ----------------------------------- |
| id            | bigint       | PK, AUTO_INCREMENT        | 主键                                |
| user_id       | bigint       | NOT NULL, FK              | 创建者ID                            |
| name          | varchar(256) | NOT NULL                  | 空间名称                            |
| introduction  | varchar(256) | -                         | 空间介绍                            |
| type          | tinyint      | NOT NULL                  | 空间类型 0-私有空间 1-团队空间      |
| level         | tinyint      | DEFAULT 0                 | 空间级别 0-普通版 1-专业版 2-旗舰版 |
| storage_size  | bigint       | DEFAULT 536870912         | 空间大小（字节, 默认512MB）         |
| size          | bigint       | DEFAULT 0                 | 已用空间大小（字节）                |
| team_users_id | text         | -                         | 团队空间成员ID列表（JSON数组）      |
| create_time   | datetime     | DEFAULT CURRENT_TIMESTAMP | 创建时间                            |
| update_time   | datetime     | DEFAULT CURRENT_TIMESTAMP | 更新时间                            |
| is_delete     | tinyint      | DEFAULT 0                 | 0-未删除, 1-已删除                  |

#### pic_system 表 (系统配置表)

| 字段     | 类型        | 约束   | 说明                                    |
| -------- | ----------- | ------ | --------------------------------------- |
| id       | bigint      | PK     | 主键                                    |
| syskey   | varchar(64) | UNIQUE | 配置键（如 SYS_PIC_TYPE, SYS_MARQUEES） |
| sysvalue | text        | -      | 配置值（JSON格式存储）                  |

### 9.2 数据库索引

| 表         | 索引        | 字段         |
| ---------- | ----------- | ------------ |
| user       | PRIMARY KEY | id           |
| user       | UNIQUE      | username     |
| user       | UNIQUE      | nickname     |
| post       | PRIMARY KEY | id           |
| post       | INDEX       | user_id      |
| post       | INDEX       | title        |
| picture    | PRIMARY KEY | id           |
| picture    | INDEX       | user_id      |
| picture    | INDEX       | picture_name |
| picture    | INDEX       | space_id     |
| comment    | PRIMARY KEY | id           |
| comment    | INDEX       | user_id      |
| comment    | INDEX       | post_id      |
| space      | PRIMARY KEY | id           |
| space      | INDEX       | user_id      |
| pic_system | PRIMARY KEY | id           |
| pic_system | UNIQUE      | syskey       |

### 9.3 实体关系 (ER)

```
User (1) ────< (N) Post
  │               │
  │               ├───< (1) Cover Picture (通过cover字段)
  │               │
  │               └───< (N) Comment
  │
  ├───< (N) Picture
  │               │
  │               └───< (N) Post (通过postId关联)
  │
  ├───< (N) Space (空间)
  │               │
  │               └───< (N) Picture (通过spaceId关联)
  │
  ├───< (N) UserFans (fan_id → User.id)
  │
  ├───< (N) UserPostCollect (post_id → Post.id)
  │
  └───< (N) UserPostLikes (post_id → Post.id)

PicSystem (独立配置表, syskey=sysvalue 存储系统配置)
```

---

## 10. API接口文档

### 10.1 用户公开接口

| 方法 | 路径                     | 说明           | 请求体             | 响应体                  | 权限 |
| ---- | ------------------------ | -------------- | ------------------ | ----------------------- | ---- |
| GET  | /user/checkCode/register | 获取注册验证码 | -                  | Response\<CheckCodeVO\> | 无   |
| GET  | /user/checkCode/login    | 获取登录验证码 | -                  | Response\<CheckCodeVO\> | 无   |
| POST | /user/register           | 用户注册       | UserRequestRequest | Response\<Boolean\>     | 无   |
| POST | /user/login              | 用户登录       | UserLoginRequest   | Response\<UserLoginVO\> | 无   |

### 10.2 用户私有接口 (需登录)

| 方法 | 路径             | 说明             | 请求体                    | 响应体                             | 权限                         |
| ---- | ---------------- | ---------------- | ------------------------- | ---------------------------------- | ---------------------------- |
| GET  | /user/myself     | 获取个人主页     | -                         | Response\<UserMessageVO\>          | 登录用户                     |
| GET  | /user/getUser    | 获取当前用户信息 | -                         | Response\<UserLoginVO\>            | 登录用户                     |
| POST | /user/editUser   | 编辑个人信息     | UserEditRequest           | Response\<Boolean\>                | 登录用户(仅自己)             |
| GET  | /user/logout     | 退出登录         | -                         | Response\<Boolean\>                | 登录用户                     |
| POST | /picture/avatar  | 上传头像         | MultipartFile + id        | Response\<String\>                 | 登录用户(仅自己, 管理员除外) |
| POST | /picture/post    | 上传帖子图片     | MultipartFile             | Response\<PicturePostVO\>          | 登录用户                     |
| GET  | /picture/list    | 获取公开图片列表 | current, pageSize (query) | Response\<IPage\<PictureListVO\>\> | 无                           |
| POST | /post/post       | 发布帖子         | UploadPostRequest         | Response\<Boolean\>                | 登录用户                     |
| POST | /post/editPost   | 编辑帖子         | EditPostRequest           | Response\<Boolean\>                | 登录用户(仅自己)             |
| GET  | /post/getPost    | 获取帖子详情     | id (query)                | Response\<PostDetailVO\>           | 登录用户                     |
| POST | /post/postList   | 获取帖子列表     | PostQueryRequest          | Response\<IPage\<PostListVO\>\>    | 无                           |
| POST | /post/like       | 点赞帖子         | id (query)                | Response\<Boolean\>                | 登录用户                     |
| POST | /post/myPosts    | 获取我的帖子列表 | PageRequest               | Response\<IPage\<PostListVO\>\>    | 登录用户                     |
| POST | /post/myCollects | 获取我的收藏列表 | PageRequest               | Response\<IPage\<PostListVO\>\>    | 登录用户                     |
| POST | /post/myLikes    | 获取我的点赞列表 | PageRequest               | Response\<IPage\<PostListVO\>\>    | 登录用户                     |

### 10.3 管理员接口 (需admin角色)

| 方法 | 路径                  | 说明               | 请求体                              | 响应体                              | 权限  |
| ---- | --------------------- | ------------------ | ----------------------------------- | ----------------------------------- | ----- |
| POST | /user/admin/getUser   | 获取用户详情       | UserIdRequest                       | Response\<User\>                    | admin |
| POST | /user/admin/userList  | 获取用户列表       | UserQueryWrapper                    | Response\<IPage\<User\>\>           | admin |
| POST | /user/admin/setStatus | 设置用户状态       | UserIdRequest                       | Response\<Boolean\>                 | admin |
| POST | /user/admin/editUser  | 编辑用户信息       | UserEditByAdminRequest              | Response\<Boolean\>                 | admin |
| GET  | /picture/admin/list   | 管理员获取图片列表 | current, pageSize, status (query)   | Response\<IPage\<PictureAdminVO\>\> | admin |
| POST | /picture/admin/review | 图片审核           | pictureId, status, selected (query) | Response\<Boolean\>                 | admin |
| POST | /picture/delete       | 删除图片           | DeleteByIdList                      | Response\<Boolean\>                 | admin |
| POST | /space/create         | 创建空间           | CreateSpace                         | Response\<Boolean\>                 | admin |
| GET  | /space/list           | 获取空间列表       | type (query)                        | Response\<IPage\<Space\>\>          | admin |
| POST | /space/update         | 更新空间           | UpdateSpace                         | Response\<Boolean\>                 | admin |
| POST | /space/pictureList    | 获取空间图片列表   | SpacePictureList                    | Response\<IPage\<PictureListVO\>\>  | admin |
| POST | /system/addList       | 添加分类标签       | AddSysPicType                       | Response\<Boolean\>                 | admin |
| POST | /system/deleteType    | 删除分类标签       | type (query)                        | Response\<Boolean\>                 | admin |
| GET  | /system/marquee       | 获取跑马灯图片     | -                                   | Response\<List\<String\>\>          | 无    |
| POST | /system/addMarquee    | 添加跑马灯图片     | AddSysMarquee                       | Response\<Boolean\>                 | admin |
| POST | /system/deleteMarquee | 删除跑马灯图片     | value (query)                       | Response\<Boolean\>                 | admin |

### 10.4 系统公开接口

| 方法 | 路径            | 说明           | 请求体 | 响应体                     | 权限 |
| ---- | --------------- | -------------- | ------ | -------------------------- | ---- |
| GET  | /system/list    | 获取分类列表   | -      | Response\<List\<String\>\> | 无   |
| GET  | /system/marquee | 获取跑马灯图片 | -      | Response\<List\<String\>\> | 无   |

### 10.5 响应格式

```json
{
  "code": 1,
  "message": "成功",
  "data": { ... }
}
```

---

## 11. 设计模式应用

### 11.1 使用的设计模式

| 模式                            | 应用场景                | 说明                                       |
| ------------------------------- | ----------------------- | ------------------------------------------ |
| **MVC模式**                     | 前后端分离架构          | Controller-Service-Mapper三层架构          |
| **Repository模式**              | MyBatis-Plus数据访问层  | BaseMapper提供通用CRUD方法                 |
| **Service模式**                 | 业务逻辑封装            | IService + ServiceImpl                     |
| **DTO模式**                     | 数据传输对象            | Request/Response分离                       |
| **VO模式**                      | 视图对象                | 封装返回给前端的数据                       |
| **Singleton模式**               | Spring Bean单例         | 所有Spring管理的Bean默认单例               |
| **Factory模式**                 | Spring IoC容器          | Bean创建和管理                             |
| **Template Method模式**         | MyBatis-Plus BaseMapper | 定义算法骨架，子类实现细节                 |
| **Chain of Responsibility模式** | Spring Interceptor链    | 请求拦截器链                               |
| **Proxy模式**                   | Spring AOP              | AuthInterceptor权限代理                    |
| **Strategy模式**                | QueryWrapper动态查询    | 根据不同条件构建不同查询                   |
| **Builder模式**                 | DTO/VO对象              | Lombok @Builder注解                        |
| **Template模式**                | 帖子发布流程            | 先上传图片，再提交帖子，最后关联图片与帖子 |
| **Facade模式**                  | LoginUser工具类         | 封装Session+Redis获取当前用户的复杂过程    |
| **Cache-Aside模式**             | 系统配置缓存(Redis)     | 先查Redis缓存，未命中再查数据库并更新缓存  |

### 11.2 模式优势

- **高内聚低耦合**: 各层职责清晰，Controller只处理请求，Service处理业务，Mapper处理数据
- **可维护性**: 修改某一层不影响其他层
- **可测试性**: 各组件可独立测试
- **可扩展性**: 易于添加新功能
- **类型安全**: 强类型Java语言和泛型支持
- **权限控制**: 基于注解和AOP的声明式权限校验
- **云服务集成**: 腾讯云COS实现对象存储与业务逻辑解耦

---

## 12. 性能与安全考虑

### 12.1 性能优化点

| 优化点                | 实现方式                                                    | 效果                                      |
| --------------------- | ----------------------------------------------------------- | ----------------------------------------- |
| **分页查询**          | MyBatis-Plus分页插件                                        | 优化大数据量查询，避免全表扫描            |
| **Redis缓存**         | 验证码(5分钟)、用户信息缓存(USER_ID:{userId})、系统配置缓存 | 减少数据库查询，提高响应速度              |
| **数据库索引**        | userId、username、nickname、title、space_id等建立索引       | 加速查询和唯一性校验                      |
| **按需加载**          | 前端按需渲染组件                                            | 提升首屏加载速度                          |
| **响应式设计**        | Ant Design移动端适配                                        | 多设备兼容                                |
| **Session+Redis认证** | Session存储userId + Redis存储User JSON                      | 仅需一次Redis查询获取用户，支持分布式部署 |
| **对象存储(COS)**     | 腾讯云COS存储图片，CDN加速访问                              | 降低服务器压力，提高图片加载速度          |
| **瀑布流布局**        | Ant Design Masonry组件                                      | 优化帖子展示性能，懒加载                  |
| **分布式锁**          | Redisson分布式锁                                            | 防止并发操作冲突                          |

### 12.2 安全措施

| 安全措施         | 实现方式                              | 防护目标                             |
| ---------------- | ------------------------------------- | ------------------------------------ |
| **输入验证**     | 前后端双重参数校验                    | 防止非法输入                         |
| **SQL注入防护**  | MyBatis-Plus参数化查询                | 防止SQL注入攻击                      |
| **XSS防护**      | React自动转义                         | 防止跨站脚本攻击                     |
| **CSRF防护**     | 跨域CORS配置 + Session认证            | 防止跨站请求伪造                     |
| **密码加密**     | MD5 + 盐值(fish)                      | 防止密码明文泄露                     |
| **验证码防护**   | 图形验证码(Hutool CircleCaptcha)      | 防暴力破解和机器注册                 |
| **Session认证**  | Spring Session + Redis存储            | 有状态认证，支持分布式部署，易于失效 |
| **权限控制**     | @AuthCheck注解 + AuthInterceptor AOP  | 基于角色的访问控制                   |
| **逻辑删除**     | @TableLogic注解                       | 数据安全保护，可恢复                 |
| **CORS配置**     | CorsConfig允许跨域                    | 前后端分离安全通信                   |
| **文件上传限制** | 文件大小校验(<=5MB)                   | 防止大文件攻击                       |
| **受限输入流**   | LimitedInputStream                    | 限制上传文件大小                     |
| **COS安全**      | 腾讯云COS私有桶 + 签名访问            | 保护图片资源不被非法访问             |
| **用户状态检查** | 登录/接口调用时校验用户状态(status=1) | 禁用用户无法使用系统                 |

### 12.3 常量管理

#### Redis键常量 (RedisConstants)

| 常量              | 用途                      | 有效期            |
| ----------------- | ------------------------- | ----------------- |
| LOGIN_CODE_KEY    | 登录验证码                | 5分钟             |
| REGISTER_CODE_KEY | 注册验证码                | 5分钟             |
| TOKEN_KEY         | Session中存储userId的键   | 由Session配置决定 |
| USER_ID           | Redis中存储User对象的前缀 | 由Session配置决定 |
| LIKE_POST         | 帖子点赞状态缓存前缀      | 临时缓存          |

#### 用户常量 (UserConstants)

| 常量              | 值         | 用途             |
| ----------------- | ---------- | ---------------- |
| USER              | "user"     | 普通用户角色     |
| ADMIN             | "admin"    | 管理员角色       |
| SALT              | "fish"     | 密码加密盐值     |
| DEFAULT_NICK_NAME | "小鱼籽\_" | 注册默认昵称前缀 |

#### 空间常量 (SpaceConstants)

| 常量         | 值         | 用途                    |
| ------------ | ---------- | ----------------------- |
| PRIVATE_SIZE | 536870912  | 私有空间默认大小(512MB) |
| TEAM_SIZE    | 5368709120 | 团队空间默认大小(5GB)   |
| PRIVATE      | "PRIVATE"  | 私有空间级别标识        |
| TEAM         | "TEAM"     | 团队空间级别标识        |

#### 系统配置常量 (SysConstants)

| 常量         | 值             | 用途            |
| ------------ | -------------- | --------------- |
| SYS_PIC_TYPE | "SYS_PIC_TYPE" | 图片分类标签Key |
| SYS_MARQUEES | "SYS_MARQUEES" | 跑马灯图片Key   |

### 12.4 业务规则

#### 注册规则

- 账号长度: 6-11位
- 密码长度: 8-20位
- 确认密码: 必须与密码一致
- 昵称生成: "小鱼籽\_" + 6位随机字符串
- 默认头像: GitHub默认头像URL

#### 编辑个人信息规则

- 昵称长度: 5-11位
- 账号长度: 6-11位
- 密码长度: 8-20位
- 只能修改自己的信息 (isMe校验)

#### 帖子发布规则

- 图片文件大小: <= 5MB
- 图片先上传到COS获取URL和ID，再提交帖子关联图片
- 帖子可设置公开/仅自己可见
- 帖子有封面图片(从关联图片中选择)

#### 管理员操作规则

- 必须具有admin角色
- 编辑用户时密码非空才加密
- 头像为null时设置默认头像
- 封禁/解封是状态切换 (1↔0)

#### 头像上传规则

- 图片文件大小: <= 5MB
- 用户只能修改自己的头像
- 管理员可以修改任意用户头像
- 头像上传到腾讯云COS存储

#### Session认证规则 (Spring Session + Redis + LoginUser)

- 登录成功后通过 `request.getSession().setAttribute(TOKEN_KEY, userId)` 将userId存储到Session中
- 同时通过 `Redis.set(USER_ID:{userId}, userJson)` 将User对象缓存到Redis中
- Spring Session自动将会话数据持久化到Redis (配置: `spring.session.store-type=redis`)
- 通过 `LoginUser.getLoginUser(request)` 工具类获取当前用户：先从Session取userId，再从Redis取User对象
- Spring Session由框架管理生命周期，过期后需重新登录
- 退出登录时清除Session中的userId和Redis中的User缓存
- 客户端自动携带Spring Session的Cookie (默认Cookie名称: `SESSION`)
- Spring Session支持分布式部署，会话数据在Redis中共享
- 用户信息更新后需同步更新Redis缓存中的User对象

#### 图片审核规则

- 图片上传后默认状态为2（待审核）
- 管理员可将图片状态修改为1（通过/正常）或0（禁用）
- 管理员可标记图片为精选（selected=true）
- 公开图片列表仅返回status=1的图片
- 图片支持关联空间（spaceId）

#### 空间管理规则

- 私有空间默认存储大小: 512MB
- 团队空间默认存储大小: 5GB
- 空间级别: 0-普通版, 1-专业版, 2-旗舰版
- 空间类型: 0-私有空间, 1-团队空间
- 团队空间可设置成员列表（JSON数组存储在team_users_id字段）
- 创建空间需要管理员权限

#### 系统配置规则

- 分类标签和跑马灯图片存储在pic_system表，key-value格式
- 分类标签Key: SYS_PIC_TYPE，值为JSON数组格式
- 跑马灯图片Key: SYS_MARQUEES，值为JSON数组格式
- 系统配置优先从Redis缓存读取，缓存未命中时查数据库并回写缓存
- 添加新标签时自动合并去重
- 管理员可添加/删除分类标签和跑马灯图片
