# AI.Image.Material.Collaboration.Platform UML 模型图

> 基于实际前后端项目代码和数据库结构完全重构

## 1. 用例图 (Use Case Diagram)

### 1.1 系统参与者 (Actors)
- **普通用户 (User)**：可注册、登录、浏览内容、管理个人资料
- **管理员 (Admin)**：可管理用户、封禁/解封账号、编辑用户信息

### 1.2 用例列表

#### 普通用户用例
- 注册账户（图形验证码验证，账号6-11位，密码8-20位，确认密码一致）
- 登录系统（图形验证码验证，JWT Token认证）
- 获取个人主页信息（查看我的发布/收藏/点赞）
- 编辑个人信息（昵称5-11位，账号6-11位，密码8-20位）
- 浏览社区广场
- 查看通知消息
- 访问个人空间
- 访问团队空间
- 访问AI管理
- 退出登录

#### 管理员用例
- 获取用户列表（分页查询，多维度搜索）
- 多维度搜索用户（ID、用户名、邮箱、手机号、昵称、角色、状态、创建时间）
- 编辑用户信息（修改资料、密码、角色、状态）
- 封禁/解封用户（状态切换：正常↔禁用）
- 管理系统功能

### 1.3 用例关系
- **包含关系 (Include)**:
  - 注册账户 → 获取图形验证码
  - 登录系统 → 获取图形验证码
  - 注册账户 → 验证用户协议
  - 编辑个人信息 → 校验身份合法性
  
- **前置条件 (Precondition)**:
  - 用户管理 ← 管理员已登录且具有admin角色
  - 编辑用户 ← 用户存在且为管理员权限
  - 编辑个人信息 ← 用户已登录且只能修改自己的信息

### 1.4 权限控制
- 管理员接口使用 `@AuthCheck(role = ADMIN)` 注解
- 通过 `AuthInterceptor` 切面拦截进行权限校验
- 基于 JWT Token 的用户认证机制

## 2. 类图 (Class Diagram)

### 2.1 核心实体类 (Entity)

#### User 实体类
```
+----------------------------------+
|           User                   |
+----------------------------------+
| - id: Long (PK, AUTO_INCREMENT)  |
| - username: String (UNIQUE)      |
| - password: String (MD5+Salt)    |
| - avatar: String (URL)           |
| - email: String                  |
| - phone: String                  |
| - nickname: String (UNIQUE)      |
| - status: Integer (1-正常/0-禁用/2-待审核) |
| - isDelete: Integer (0-未删除/1-已删除, @TableLogic) |
| - role: String (admin/user)      |
| - createTime: Date               |
| - updateTime: Date               |
| - likeNum: Long                  |
| - collectNum: Long               |
| - isPrivateFollows: Integer (0-公开/1-不公开) |
| - isPrivatePostCollect: Integer (0-公开/1-不公开) |
| - isPrivateLikes: Integer (0-公开/1-不公开) |
| - isPrivateFans: Integer (0-公开/1-不公开) |
+----------------------------------+
```

#### Post 实体类
```
+----------------------------------+
|           Post                   |
+----------------------------------+
| - id: Long (PK, AUTO_INCREMENT)  |
| - userId: Long (FK->User)        |
| - title: String (256字符)        |
| - content: Text                  |
| - status: Integer (1-正常/0-禁用/2-待审核) |
| - createTime: Date               |
| - updateTime: Date               |
| - isDelete: Integer (0-否/1-是, @TableLogic) |
| - likesNum: Long                 |
| - collectsNum: Long              |
| - commentNum: Integer            |
| - isPrivate: Integer (0-公开/1-仅自己可见) |
+----------------------------------+
```

#### Picture 实体类
```
+----------------------------------+
|          Picture                 |
+----------------------------------+
| - id: Long (PK, AUTO_INCREMENT)  |
| - userId: Long (FK->User)        |
| - pictureName: Long              |
| - url: String (512字符)          |
| - width: String                  |
| - height: String                 |
| - size: String                   |
| - status: Integer (1-正常/0-禁用/2-待审核) |
| - createTime: Date               |
| - updateTime: Date               |
| - isPrivate: Integer (0-公开到首页/1-不公开) |
+----------------------------------+
```

#### Comment 实体类
```
+----------------------------------+
|          Comment                 |
+----------------------------------+
| - id: Long (PK, AUTO_INCREMENT)  |
| - userId: Long (FK->User)        |
| - postId: Long (FK->Post)        |
| - content: Text                  |
| - parentId: Long (父评论ID, 支持二级评论) |
| - toUserId: Integer (回复给谁)   |
| - status: Integer (1-正常/0-禁用/2-待审核) |
| - createTime: Date               |
+----------------------------------+
```

#### UserFans 实体类
```
+----------------------------------+
|         UserFans                 |
+----------------------------------+
| - id: Long                       |
| - userId: Long                   |
| - fanId: Long                    |
+----------------------------------+
```

#### UserPostCollect 实体类
```
+----------------------------------+
|      UserPostCollect             |
+----------------------------------+
| - id: Long                       |
| - userId: Long                   |
| - postId: Long                   |
+----------------------------------+
```

#### UserPostLikes 实体类
```
+----------------------------------+
|      UserPostLikes               |
+----------------------------------+
| - id: Long                       |
| - userId: Long                   |
| - postId: Long                   |
+----------------------------------+
```

#### UserFollows 实体类
```
+----------------------------------+
|       UserFollows                |
+----------------------------------+
| - id: Long                       |
| - userId: Long                   |
| - followsId: Long                |
+----------------------------------+
```

### 2.2 数据传输对象 (DTO)

#### UserLoginRequest
```
+----------------------------------+
|    UserLoginRequest              |
+----------------------------------+
| - username: String               |
| - password: String               |
| - checkCode: String              |
| - captchaKey: String             |
+----------------------------------+
```

#### UserRequestRequest (注册)
```
+----------------------------------+
|    UserRequestRequest            |
+----------------------------------+
| - username: String (6-11位)      |
| - password: String (8-20位)      |
| - checkPassword: String          |
| - checkCode: String              |
| - captchaKey: String             |
+----------------------------------+
```

#### UserEditRequest (用户编辑自己)
```
+----------------------------------+
|    UserEditRequest               |
+----------------------------------+
| - id: Long                       |
| - username: String (6-11位)      |
| - password: String (8-20位)      |
| - avatar: String                 |
| - email: String                  |
| - phone: String                  |
| - nickname: String (5-11位)      |
+----------------------------------+
```

#### UserEditByAdminRequest (管理员编辑用户)
```
+----------------------------------+
|  UserEditByAdminRequest          |
+----------------------------------+
| - id: Long (必填)                |
| - username: String               |
| - password: String               |
| - avatar: String                 |
| - email: String                  |
| - phone: String                  |
| - nickname: String               |
| - status: Integer                |
| - role: String                   |
+----------------------------------+
```

#### UserQueryWrapper
```
+----------------------------------+
|    UserQueryWrapper              |
+----------------------------------+
| - id: Long                       |
| - username: String               |
| - email: String                  |
| - phone: String                  |
| - nickname: String               |
| - role: String                   |
| - status: Integer                |
| - createTime: Date               |
| - current: long (当前页)         |
| - pageSize: long (每页大小)      |
| - sortField: String              |
| - sortOrder: String              |
+----------------------------------+
```

#### UserIdRequest
```
+----------------------------------+
|    UserIdRequest                 |
+----------------------------------+
| - userId: Long                   |
+----------------------------------+
```

#### PageRequest (基础分页)
```
+----------------------------------+
|    PageRequest                   |
+----------------------------------+
| - current: long                  |
| - pageSize: long                 |
+----------------------------------+
```

#### DeleteById (基础删除)
```
+----------------------------------+
|    DeleteById                    |
+----------------------------------+
| - id: Long                       |
+----------------------------------+
```

### 2.3 视图对象 (VO)

#### UserLoginVO
```
+----------------------------------+
|    UserLoginVO                   |
+----------------------------------+
| - loginToken: String (JWT Token) |
| - id: Long                       |
| - username: String               |
| - nickname: String               |
| - avatar: String                 |
| - email: String                  |
| - phone: String                  |
| - role: String                   |
+----------------------------------+
```

#### UserMessageVO
```
+----------------------------------+
|    UserMessageVO                 |
+----------------------------------+
| - id: Long                       |
| - username: String               |
| - avatar: String                 |
| - email: String                  |
| - phone: String                  |
| - nickname: String               |
| - role: String                   |
| - createTime: Date               |
| - postList: List<PostListVO>     |
| - postCollectList: List<PostListVO> |
| - postLikeList: List<PostListVO> |
+----------------------------------+
```

#### CheckCodeVO
```
+----------------------------------+
|    CheckCodeVO                   |
+----------------------------------+
| - captchaKey: String             |
| - base64Image: String            |
+----------------------------------+
```

#### PostListVO
```
+----------------------------------+
|    PostListVO                    |
+----------------------------------+
| - id: Long                       |
| - userId: Long                   |
| - title: String                  |
| - content: String                |
| - pictureIds: String (JSON数组)  |
| - createTime: Date               |
+----------------------------------+
```

### 2.4 通用响应类

#### Response<T>
```
+----------------------------------+
|    Response<T>                   |
+----------------------------------+
| - code: Integer                  |
| - message: String                |
| - data: T                        |
+----------------------------------+
```

### 2.5 控制器类

#### UserController
```
+----------------------------------+
|       UserController             |
+----------------------------------+
| - userService: UserService       |
+----------------------------------+
| + POST /user/login              |
| + POST /user/register           |
| + GET /user/checkCode/register  |
| + GET /user/checkCode/login     |
| + GET /user/myself              |
| + POST /user/editUser           |
| + POST /user/admin/userList     |
| + POST /user/admin/setStatus    |
| + POST /user/admin/editUser     |
+----------------------------------+
```

### 2.6 服务层类

#### UserService 接口
```
+----------------------------------+
|    UserService                   |
+----------------------------------+
| + getCheckCode()                |
| + getLoginUser()                |
| + userRegister()                |
| + userLogin()                   |
| + newQueryWrapper()             |
| + getUserList()                 |
| + setStatus()                   |
| + editUser()                    |
| + getMyselfMessage()            |
| + editMyself()                  |
| + isMe()                        |
+----------------------------------+
```

#### UserServiceImpl
```
+----------------------------------+
|    UserServiceImpl               |
+----------------------------------+
| - stringRedisTemplate            |
| - userMapper: UserMapper         |
| - postMapper: PostMapper         |
| - userPostCollectMapper          |
| - userPostLikesMapper            |
+----------------------------------+
| + getCheckCode()                |
| + getLoginUser()                |
| + userRegister()                |
| + userLogin()                   |
| + newQueryWrapper()             |
| + getUserList()                 |
| + setStatus()                   |
| + editUser()                    |
| + getMyselfMessage()            |
| + editMyself()                  |
| + isMe()                        |
+----------------------------------+
```

### 2.7 其他服务接口和实现

| 服务接口 | 实现类 | 功能 |
|---------|--------|------|
| PostService | PostServiceImpl | 帖子业务逻辑 |
| PictureService | PictureServiceImpl | 图片业务逻辑 |
| CommentService | CommentServiceImpl | 评论业务逻辑 |
| UserPostCollectService | UserPostCollectServiceImpl | 帖子收藏业务 |
| UserPostLikesService | UserPostLikesServiceImpl | 帖子点赞业务 |
| UserFansService | UserFansServiceImpl | 粉丝业务逻辑 |
| LikesUserByIdService | LikesUserByIdServiceImpl | 按用户ID查询点赞 |

### 2.8 类间关系

#### 关联关系 (Association)
- User "1" —— "*" Picture (一个用户拥有多个图片)
- User "1" —— "*" Post (一个用户发布多个帖子)
- User "1" —— "*" Comment (一个用户发表多个评论)
- Post "1" —— "*" Comment (一个帖子有多个评论)
- User "1" —— "*" UserFans (一个用户有多个粉丝)
- User "1" —— "*" UserPostCollect (一个用户收藏多个帖子)
- User "1" —— "*" UserPostLikes (一个用户点赞多个帖子)

#### 聚合关系 (Aggregation)
- Post ◇—— "*" Picture (帖子聚合多个图片，通过pictureIds字段)

#### 依赖关系 (Dependency)
- UserController → UserService (控制器依赖服务层)
- UserServiceImpl → UserMapper (服务实现依赖数据访问层)
- UserServiceImpl → PostMapper (服务实现依赖帖子数据访问)
- UserServiceImpl → UserPostCollectMapper (服务实现依赖收藏数据访问)
- UserServiceImpl → UserPostLikesMapper (服务实现依赖点赞数据访问)
- UserServiceImpl → StringRedisTemplate (服务实现依赖Redis)
- UserServiceImpl → JwtUtil (JWT工具类)
- UserServiceImpl → BeanUtil (Hutool Bean工具)
- UserServiceImpl → DigestUtil (Hutool加密工具)

#### 实现关系 (Realization)
- UserServiceImpl —|> UserService (实现接口)
- PostServiceImpl —|> PostService (实现接口)
- PictureServiceImpl —|> PictureService (实现接口)
- CommentServiceImpl —|> CommentService (实现接口)
- UserPostCollectServiceImpl —|> UserPostCollectService (实现接口)
- UserPostLikesServiceImpl —|> UserPostLikesService (实现接口)
- UserFansServiceImpl —|> UserFansService (实现接口)
- LikesUserByIdServiceImpl —|> LikesUserByIdService (实现接口)

#### 继承关系 (Inheritance)
- UserServiceImpl —|> ServiceImpl<UserMapper, User> (MyBatis-Plus基类)
- 其他ServiceImpl —|> ServiceImpl (各自对应的MyBatis-Plus基类)

## 3. 顺序图 (Sequence Diagram)

### 3.1 用户注册流程

```
用户 -> 前端: 填写注册信息+验证码
前端 -> 后端(UserController): GET /user/checkCode/register
后端 -> UserService: getCheckCode(redisKey, len, minute)
UserService -> Redis: 生成图形验证码并存储(code, 5分钟)
Redis --> UserService: 存储成功
UserService -> Hutool CaptchaUtil: createCircleCaptcha()
Hutool CaptchaUtil --> UserService: 返回base64图片
UserService --> 后端: 返回base64图片
后端 --> 前端: Response<CheckCodeVO> (captchaKey + base64Image)

用户 -> 前端: 提交注册表单
前端 -> 后端(UserController): POST /user/register (UserRequestRequest)
后端 -> UserService: userRegister(userRequestRequest, request)
UserService -> UserService: 校验验证码(Redis)
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
用户 -> 前端: 输入用户名密码+验证码
前端 -> 后端(UserController): GET /user/checkCode/login
后端 -> UserService: getCheckCode(redisKey, len, minute)
UserService -> Redis: 生成图形验证码并存储(code, 5分钟)
Redis --> UserService: 存储成功
UserService -> Hutool CaptchaUtil: createCircleCaptcha()
Hutool CaptchaUtil --> UserService: 返回base64图片
UserService --> 后端: 返回base64图片
后端 --> 前端: Response<CheckCodeVO> (captchaKey + base64Image)

用户 -> 前端: 提交登录表单
前端 -> 后端(UserController): POST /user/login (UserLoginRequest)
后端 -> UserService: userLogin(userLoginRequest, response, request)
UserService -> UserService: 校验验证码(Redis)
UserService -> Hutool DigestUtil: MD5加密密码(加盐: fish)
UserService -> UserMapper: selectOne(username, password)
UserMapper -> MySQL: SELECT * FROM user WHERE username=? AND password=?
MySQL --> UserMapper: 返回用户数据
UserService -> Hutool JSONUtil: toJsonStr(user)
UserService -> JwtUtil: generateToken(userId)
JwtUtil --> UserService: 返回JWT Token (LOGIN-+userId)
UserService -> Redis: set(token, userJson, 1天)
Redis --> UserService: 存储成功
UserService -> HttpServletResponse: setHeader(Authorization, token)
UserService -> HttpServletResponse: setHeader(Access-Control-Expose-Headers, Authorization)
UserService -> BeanUtil: copyProperties(user, UserLoginVO)
UserService --> 后端: Response<UserLoginVO> + 设置Authorization头
后端 --> 前端: Response<UserLoginVO> + Header: Authorization
前端 -> localStorage: 保存用户信息
前端 --> 用户: 显示登录成功并跳转
```

### 3.3 获取个人主页信息流程

```
用户 -> 前端: 访问个人主页
前端 -> 后端(UserController): GET /user/myself
后端 -> UserService: getMyselfMessage(request)
UserService -> UserService: getLoginUser(request) [解析JWT获取用户]
UserService -> Redis: get(token)
Redis --> UserService: 返回用户JSON
UserService -> JSONUtil: toBean(userJson, User.class)
UserService --> 后端: 返回登录用户
UserService -> PostMapper: selectList(user_id=userId) [获取我的发布]
PostMapper -> MySQL: SELECT * FROM post WHERE user_id=?
MySQL --> PostMapper: 返回帖子列表
UserService -> UserPostCollectMapper: selectList(user_id=userId) [获取收藏ID]
UserPostCollectMapper -> MySQL: SELECT * FROM user_post_collect WHERE user_id=?
MySQL --> UserPostCollectMapper: 返回收藏记录
UserService -> PostMapper: selectList(post_id IN collectIds) [获取收藏帖子]
PostMapper -> MySQL: SELECT * FROM post WHERE id IN (?)
MySQL --> PostMapper: 返回收藏帖子列表
UserService -> UserPostLikesMapper: selectList(user_id=userId) [获取点赞ID]
UserPostLikesMapper -> MySQL: SELECT * FROM user_post_likes WHERE user_id=?
MySQL --> UserPostLikesMapper: 返回点赞记录
UserService -> PostMapper: selectList(post_id IN likeIds) [获取点赞帖子]
PostMapper -> MySQL: SELECT * FROM post WHERE id IN (?)
MySQL --> PostMapper: 返回点赞帖子列表
UserService -> BeanUtil: copyProperties(loginUser, UserMessageVO)
UserService --> 后端: UserMessageVO (含帖子列表/收藏列表/点赞列表)
后端 --> 前端: Response<UserMessageVO>
前端 --> 用户: 渲染个人主页
```

### 3.4 用户编辑个人信息流程

```
用户 -> 前端: 填写编辑表单
前端 -> 后端(UserController): POST /user/editUser (UserEditRequest)
后端 -> UserService: editMyself(userEditRequest, request)
UserService -> UserService: getLoginUser(request) [获取登录用户]
UserService -> UserService: isMe(id, request) [校验是否是自己的信息]
UserService -> JwtUtil: parseToken(token) [解析JWT对比用户ID]
JwtUtil --> UserService: 返回匹配结果
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

### 3.5 管理员获取用户列表流程

```
管理员 -> 前端: 访问用户管理页面
前端 -> 后端(UserController): POST /user/admin/userList (UserQueryWrapper)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
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

### 3.6 管理员封禁/解封用户流程

```
管理员 -> 前端: 点击封禁/解封按钮
前端 -> 后端(UserController): POST /user/admin/setStatus (UserIdRequest)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
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

### 3.7 管理员编辑用户信息流程

```
管理员 -> 前端: 填写编辑表单
前端 -> 后端(UserController): POST /user/admin/editUser (UserEditByAdminRequest)
后端 -> AuthInterceptor: @AuthCheck(role=ADMIN) [权限校验]
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

### 4.3 Token状态图

```
[未登录] --(登录成功)--> [已登录(Token存Redis, 1天)]
[已登录] --(Token过期)--> [需重新登录]
[已登录] --(退出登录)--> [未登录]
[已登录] --(Token失效)--> [MySQL查询用户, 存Redis(2天)]
```

## 5. 组件图 (Component Diagram)

### 5.1 前端组件 (FishPic-frontend)

#### 核心框架
- **React 19**: UI框架
- **Ant Design 6**: UI组件库
- **React Router v7**: 路由管理
- **Context API**: 认证状态管理 (AuthContext)
- **Axios**: HTTP客户端
- **localStorage**: 用户信息持久化
- **Vite**: 构建工具

#### 页面组件
- **HomePage.jsx**: 首页/登录/注册
- **CommunitySquare.jsx**: 社区广场
- **PrivateSpace.jsx**: 个人空间
- **TeamSpace.jsx**: 团队空间
- **Notifications.jsx**: 通知消息
- **UserProfile.jsx**: 用户资料
- **UserManagement.jsx**: 用户管理
- **AdminUserList.jsx**: 管理员用户列表
- **TeamManagement.jsx**: 团队管理
- **SpaceManagement.jsx**: 空间管理
- **AIManagement.jsx**: AI管理
- **NotFound.jsx**: 404页面

#### 布局与工具
- **GlobalLayout.jsx**: 全局布局组件
- **ProtectedRoute.jsx**: 路由保护组件
- **ErrorBoundary.jsx**: 错误边界组件
- **FunnyBackground.jsx**: 趣味背景组件
- **api/index.js**: API请求封装
- **utils/storage.js**: localStorage操作工具

### 5.2 后端组件 (FishPics-backend)

#### 核心框架
- **Spring Boot 2.7.6**: Web框架
- **MyBatis-Plus 3.5.15**: ORM框架 + 分页插件
- **MySQL 8+**: 关系型数据库
- **Redis**: 缓存服务 (验证码、登录Token)
- **Hutool**: 工具库 (加密、验证码、JSON、Bean拷贝)
- **JWT**: Token认证
- **Knife4j**: API文档生成
- **Lombok**: 代码简化

#### 包结构组件
- **Controller层**: UserController (用户控制器)
- **Service层**: UserService/Impl, PostService/Impl, PictureService/Impl, CommentService/Impl, UserPostCollectService/Impl, UserPostLikesService/Impl, UserFansService/Impl, LikesUserByIdService/Impl
- **Mapper层**: UserMapper, PostMapper, PictureMapper, CommentMapper, UserPostCollectMapper, UserPostLikesMapper, UserFansMapper, UserFollowsMapper
- **DTO层**: UserLoginRequest, UserRequestRequest, UserEditRequest, UserEditByAdminRequest, UserQueryWrapper, UserIdRequest, PageRequest, DeleteById
- **VO层**: UserLoginVO, UserMessageVO, CheckCodeVO, PostListVO
- **Entity层**: User, Post, Picture, Comment, UserFans, UserPostCollect, UserPostLikes, UserFollows

#### 公共组件
- **common/annotation**: AuthCheck (权限校验注解)
- **common/aop**: AuthInterceptor (权限拦截器)
- **common/config**: CorsConfig, JsonConfig, MybatisPlusConfig
- **common/constants**: RedisConstants, UserConstants
- **common/exception**: BaseException, ExceptionCode, ExcUtils, GlobalExceptionHandler
- **common/response**: Response, ResUtils
- **common/utils**: JwtUtil

#### 枚举类
- **enums/UserRoleEnum**: 用户角色 (admin, user)

### 5.3 组件依赖关系

```
前端(React 19 + Ant Design 6)
  ↓ HTTP请求 (Axios)
Vite Dev Server (开发模式)
  ↓ 代理配置 (/api)
Spring Boot (FishPics-backend)
  ├── 控制器层 (UserController)
  │     ↓ 依赖
  ├── 服务层 (UserService/Impl等)
  │     ├── 依赖 Redis (验证码/登录Token缓存)
  │     ├── 依赖 JWT (Token生成/解析)
  │     ├── 依赖 Hutool (工具类)
  │     └── 依赖
  ├── 数据访问层 (MyBatis-Plus Mapper)
  │     ↓ 依赖
  ├── MySQL数据库 (FishPics)
  └── Redis缓存服务器 (192.168.163.101:6379)
```

## 6. 部署图 (Deployment Diagram)

### 6.1 物理节点

| 节点 | 说明 | 地址/端口 |
|------|------|-----------|
| **客户端** | Web浏览器 (PC/Mobile) | - |
| **前端开发服务器** | Vite Dev Server | localhost:5173 |
| **后端API服务** | Spring Boot 2.7.6 | localhost:8080 |
| **API上下文路径** | 接口路径前缀 | /api |
| **数据库服务器** | MySQL 8+ | localhost:3306 |
| **缓存服务器** | Redis | 192.168.163.101:6379 |

### 6.2 网络连接

```
客户端 (浏览器)
  ↓ HTTP/HTTPS
Vite Dev Server (localhost:5173)
  ↓ 代理配置 /api -> localhost:8080/api
Spring Boot (localhost:8080/api)
  ├── ↓ JDBC (MySQL协议)
  │   MySQL (localhost:3306 / FishPics数据库)
  └── ↓ Redis协议
      Redis (192.168.163.101:6379)
```

### 6.3 端口配置

| 服务 | 端口 | 协议 |
|------|------|------|
| 前端开发服务器 | 5173 | HTTP |
| 后端API服务 | 8080 | HTTP |
| MySQL | 3306 | TCP (JDBC) |
| Redis | 6379 | TCP (Redis Protocol) |

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
生成JWT Token(LOGIN-+userId)
  ↓
存储用户信息JSON到Redis(1天有效期)
  ↓
设置Authorization响应头(Token)
  ↓
设置Access-Control-Expose-Headers头
  ↓
封装UserLoginVO(含loginToken)
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
从请求头获取Authorization Token
  ↓
解析JWT获取用户ID
  ↓
从Redis获取用户信息
  ↓ [Redis未命中]
从MySQL查询用户信息
  ↓
存储到Redis(2天有效期)
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

### 7.4 用户管理活动流

```
开始
  ↓
验证管理员权限(role=admin)
  ↓ [通过 @AuthCheck]
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

## 8. 包图 (Package Diagram)

### 8.1 后端包结构

```
hk.ljx.fishpicsbackend
├── FishPicsBackendApplication.java (启动类)
├── common (公共组件)
│   ├── annotation (注解)
│   │   └── AuthCheck (权限校验注解, role属性)
│   ├── aop (切面)
│   │   └── AuthInterceptor (权限拦截器, 解析JWT+校验角色)
│   ├── config (配置)
│   │   ├── CorsConfig (跨域配置, 允许前端请求)
│   │   ├── JsonConfig (JSON序列化配置)
│   │   └── MybatisPlusConfig (MyBatis分页插件配置)
│   ├── constants (常量)
│   │   ├── RedisConstants (Redis键常量: LOGIN_CODE_KEY, REGISTER_CODE_KEY)
│   │   └── UserConstants (用户常量: ADMIN, USER, SALT="fish", DEFAULT_NICK_NAME="小鱼籽_")
│   ├── exception (异常)
│   │   ├── BaseException (基础异常类)
│   │   ├── ExcUtils (异常工具类: throwIfTrue等)
│   │   ├── ExceptionCode (异常码枚举)
│   │   └── GlobalExceptionHandler (全局异常处理器)
│   ├── response (响应)
│   │   ├── ResUtils (响应工具类: success/error等)
│   │   └── Response (响应封装类: code, message, data)
│   └── utils (工具类)
│       └── JwtUtil (JWT工具类: generateToken, parseToken)
├── controller (控制器)
│   └── UserController (用户控制器, 9个接口)
├── dto (数据传输对象)
│   ├── base (基础请求)
│   │   ├── DeleteById (删除请求)
│   │   └── PageRequest (分页请求)
│   └── user (用户请求)
│       ├── UserEditRequest (用户编辑自己请求)
│       ├── UserEditByAdminRequest (管理员编辑用户请求)
│       ├── UserIdRequest (用户ID请求)
│       ├── UserLoginRequest (登录请求)
│       ├── UserQueryWrapper (查询条件)
│       └── UserRequestRequest (注册请求)
├── entity (实体类)
│   ├── Comment (评论)
│   ├── Picture (图片)
│   ├── Post (帖子)
│   ├── User (用户)
│   ├── UserFans (用户粉丝)
│   ├── UserFollows (用户关注)
│   ├── UserPostCollect (用户帖子收藏)
│   └── UserPostLikes (用户帖子点赞)
├── enums (枚举)
│   └── UserRoleEnum (用户角色: admin, user)
├── mapper (数据访问层)
│   ├── CommentMapper
│   ├── PictureMapper
│   ├── PostMapper
│   ├── UserMapper
│   ├── UserFansMapper
│   ├── UserFollowsMapper
│   ├── UserPostCollectMapper
│   └── UserPostLikesMapper
├── service (业务逻辑层)
│   ├── CommentService
│   ├── LikesUserByIdService
│   ├── PictureService
│   ├── PostService
│   ├── UserFansService
│   ├── UserPostCollectService
│   ├── UserPostLikesService
│   ├── UserService
│   └── impl
│       ├── CommentServiceImpl
│       ├── LikesUserByIdServiceImpl
│       ├── PictureServiceImpl
│       ├── PostServiceImpl
│       ├── UserFansServiceImpl
│       ├── UserPostCollectServiceImpl
│       ├── UserPostLikesServiceImpl
│       └── UserServiceImpl
└── vo (视图对象)
    ├── post
    │   └── PostListVO (帖子列表)
    └── user
        ├── CheckCodeVO (验证码)
        ├── UserLoginVO (登录用户)
        └── UserMessageVO (用户主页信息)
```

### 8.2 前端包结构

```
src/
├── api/
│   └── index.js (API请求封装, Axios配置)
├── assets/ (静态资源)
├── components/ (通用组件)
│   ├── ErrorBoundary.jsx (错误边界)
│   ├── FunnyBackground.jsx (趣味背景)
│   ├── GlobalLayout.jsx (全局布局)
│   └── ProtectedRoute.jsx (路由保护)
├── context/ (状态管理)
│   └── AuthContext.jsx (认证上下文)
├── pages/ (页面组件)
│   ├── AdminUserList.jsx (管理员用户列表)
│   ├── AIManagement.jsx (AI管理)
│   ├── CommunitySquare.jsx (社区广场)
│   ├── HomePage.jsx (首页/登录/注册)
│   ├── NotFound.jsx (404页面)
│   ├── Notifications.jsx (通知消息)
│   ├── PrivateSpace.jsx (个人空间)
│   ├── SpaceManagement.jsx (空间管理)
│   ├── TeamManagement.jsx (团队管理)
│   ├── TeamSpace.jsx (团队空间)
│   ├── UserManagement.jsx (用户管理)
│   └── UserProfile.jsx (用户资料)
├── utils/ (工具函数)
│   └── storage.js (localStorage操作)
├── App.jsx (主应用组件)
├── App.css (应用样式)
├── main.jsx (入口文件)
└── index.css (全局样式)
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
  │     └── service → common/utils (服务使用工具类)
  ├── mapper → entity (数据访问使用实体类)
  │     └── mapper → database (MyBatis映射)
  ├── common/aop → controller (切面拦截控制器)
  │     └── aop → common/utils (使用JWT工具)
  └── common/exception → 全局 (异常处理)
```

## 9. 数据库模型

### 9.1 数据库表结构

#### user 表 (用户表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK, AUTO_INCREMENT | 用户ID |
| username | varchar(32) | UNIQUE | 账号（登录用） |
| password | varchar(128) | - | 密码(MD5+Salt) |
| avatar | varchar(256) | - | 头像URL |
| email | varchar(64) | - | 邮箱 |
| phone | varchar(16) | - | 手机号 |
| nickname | varchar(32) | UNIQUE | 昵称（展示用） |
| status | tinyint | DEFAULT 1 | 状态 1-正常 0-禁用 2-待审核 |
| delete | tinyint | DEFAULT 0 | 0-逻辑未删除, 1-逻辑删除 |
| role | varchar(32) | DEFAULT 'user' | 用户的权限 |
| create_time | datetime | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

#### post 表 (帖子表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK, AUTO_INCREMENT | 主键 |
| user_id | bigint | NOT NULL, FK | 关联用户表 |
| title | varchar(256) | NOT NULL | 标题 |
| content | text | NOT NULL | 内容 |
| picture_ids | varchar(512) | - | 图片id数组(JSON) |
| status | tinyint | DEFAULT 1 | 状态 1-正常 0-禁用 2-待审核 |
| create_time | datetime | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | DEFAULT CURRENT_TIMESTAMP | 更新时间 |
| delete | int | - | 0-未删除, 1-已删除 |

#### picture 表 (图片表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK, AUTO_INCREMENT | 主键 |
| user_id | bigint | NOT NULL, FK | 用户id |
| picture_name | bigint | NOT NULL | 图片名称 |
| url | varchar(512) | NOT NULL | 图片地址 |
| width | varchar(32) | - | 宽度 |
| height | varchar(32) | - | 高度 |
| size | varchar(32) | - | 大小 |
| status | tinyint | DEFAULT 1 | 状态 1-正常 0-禁用 2-待审核 |
| create_time | datetime | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

#### comment 表 (评论表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK, AUTO_INCREMENT | 主键 |
| user_id | bigint | NOT NULL, FK | 关联用户表 |
| post_id | bigint | NOT NULL, FK | 关联帖子表 |
| content | text | NOT NULL | 评论内容 |
| status | tinyint | DEFAULT 1 | 状态 1-正常 0-禁用 2-待审核 |
| create_time | datetime | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

#### user_fans 表 (用户粉丝表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| user_id | bigint | - | 用户ID |
| fan_id | bigint | - | 粉丝ID |

#### user_post_collect 表 (用户帖子收藏表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| user_id | bigint | - | 用户ID |
| post_id | bigint | - | 帖子ID |

#### user_post_likes 表 (用户帖子点赞表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| user_id | bigint | - | 用户ID |
| post_id | bigint | - | 帖子ID |

#### user_follows 表 (用户关注表)
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint | PK | 主键 |
| user_id | bigint | - | 用户ID |
| follows_id | bigint | - | 关注对象ID |

### 9.2 数据库索引

| 表 | 索引 | 字段 |
|------|------|------|
| user | PRIMARY KEY | id |
| user | UNIQUE | username |
| user | UNIQUE | nickname |
| post | PRIMARY KEY | id |
| post | INDEX | user_id |
| post | INDEX | title |
| picture | PRIMARY KEY | id |
| picture | INDEX | user_id |
| picture | INDEX | picture_name |
| comment | PRIMARY KEY | id |
| comment | INDEX | user_id |
| comment | INDEX | post_id |

### 9.3 实体关系 (ER)

```
User (1) ────< (N) Post
  │               │
  │               │
  │               │
  │               └───< (N) Comment
  │               
  ├───< (N) Picture
  │
  ├───< (N) UserFans (fan_id → User.id)
  │
  ├───< (N) UserPostCollect (post_id → Post.id)
  │
  ├───< (N) UserPostLikes (post_id → Post.id)
  │
  └───< (N) UserFollows (follows_id → User.id)
```

## 10. API接口文档

### 10.1 用户公开接口

| 方法 | 路径 | 说明 | 请求体 | 响应体 |
|------|------|------|--------|--------|
| GET | /user/checkCode/register | 获取注册验证码 | - | Response\<CheckCodeVO\> |
| GET | /user/checkCode/login | 获取登录验证码 | - | Response\<CheckCodeVO\> |
| POST | /user/register | 用户注册 | UserRequestRequest | Response\<Boolean\> |
| POST | /user/login | 用户登录 | UserLoginRequest | Response\<UserLoginVO\> |

### 10.2 用户私有接口 (需登录)

| 方法 | 路径 | 说明 | 请求体 | 响应体 |
|------|------|------|--------|--------|
| GET | /user/myself | 获取个人主页 | - | Response\<UserMessageVO\> |
| POST | /user/editUser | 编辑个人信息 | UserEditRequest | Response\<Boolean\> |

### 10.3 管理员接口 (需admin角色)

| 方法 | 路径 | 说明 | 请求体 | 响应体 |
|------|------|------|--------|--------|
| POST | /user/admin/userList | 获取用户列表 | UserQueryWrapper | Response\<IPage\<User\>\> |
| POST | /user/admin/setStatus | 设置用户状态 | UserIdRequest | Response\<Boolean\> |
| POST | /user/admin/editUser | 编辑用户信息 | UserEditByAdminRequest | Response\<Boolean\> |

### 10.4 响应格式

```json
{
  "code": 1,
  "message": "成功",
  "data": { ... }
}
```

## 11. 设计模式应用

### 11.1 使用的设计模式

| 模式 | 应用场景 | 说明 |
|------|----------|------|
| **MVC模式** | 前后端分离架构 | Controller-Service-Mapper三层架构 |
| **Repository模式** | MyBatis-Plus数据访问层 | BaseMapper提供通用CRUD方法 |
| **Service模式** | 业务逻辑封装 | IService + ServiceImpl |
| **DTO模式** | 数据传输对象 | Request/Response分离 |
| **VO模式** | 视图对象 | 封装返回给前端的数据 |
| **Singleton模式** | Spring Bean单例 | 所有Spring管理的Bean默认单例 |
| **Factory模式** | Spring IoC容器 | Bean创建和管理 |
| **Template Method模式** | MyBatis-Plus BaseMapper | 定义算法骨架，子类实现细节 |
| **Chain of Responsibility模式** | Spring Interceptor链 | 请求拦截器链 |
| **Proxy模式** | Spring AOP | AuthInterceptor权限代理 |
| **Strategy模式** | QueryWrapper动态查询 | 根据不同条件构建不同查询 |
| **Builder模式** | DTO/VO对象 | Lombok @Builder注解 |

### 11.2 模式优势

- **高内聚低耦合**: 各层职责清晰，Controller只处理请求，Service处理业务，Mapper处理数据
- **可维护性**: 修改某一层不影响其他层
- **可测试性**: 各组件可独立测试
- **可扩展性**: 易于添加新功能
- **类型安全**: 强类型Java语言和泛型支持
- **权限控制**: 基于注解和AOP的声明式权限校验

## 12. 性能与安全考虑

### 12.1 性能优化点

| 优化点 | 实现方式 | 效果 |
|--------|----------|------|
| **分页查询** | MyBatis-Plus分页插件 | 优化大数据量查询，避免全表扫描 |
| **Redis缓存** | 验证码(5分钟)、登录Token(1天)、用户信息(2天) | 减少数据库查询，提高响应速度 |
| **数据库索引** | userId、username、nickname、title等建立索引 | 加速查询和唯一性校验 |
| **按需加载** | 前端按需渲染组件 | 提升首屏加载速度 |
| **响应式设计** | Ant Design移动端适配 | 多设备兼容 |
| **JWT Token** | 无状态认证 | 减少服务端会话存储 |
| **Redis缓存回退** | Redis未命中时查MySQL，查到后反存Redis | 提高缓存命中率 |

### 12.2 安全措施

| 安全措施 | 实现方式 | 防护目标 |
|----------|----------|----------|
| **输入验证** | 前后端双重参数校验 | 防止非法输入 |
| **SQL注入防护** | MyBatis-Plus参数化查询 | 防止SQL注入攻击 |
| **XSS防护** | React自动转义 | 防止跨站脚本攻击 |
| **CSRF防护** | 跨域CORS配置 + Token认证 | 防止跨站请求伪造 |
| **密码加密** | MD5 + 盐值(fish) | 防止密码明文泄露 |
| **验证码防护** | 图形验证码(Hutool CircleCaptcha) | 防暴力破解和机器注册 |
| **Token认证** | JWT Token + Redis存储 | 无状态认证，易失效 |
| **权限控制** | @AuthCheck注解 + AuthInterceptor AOP | 基于角色的访问控制 |
| **逻辑删除** | @TableLogic注解 | 数据安全保护，可恢复 |
| **CORS配置** | CorsConfig允许跨域 | 前后端分离安全通信 |

### 12.3 常量管理

#### Redis键常量 (RedisConstants)
| 常量 | 用途 | 有效期 |
|------|------|--------|
| LOGIN_CODE_KEY | 登录验证码 | 5分钟 |
| REGISTER_CODE_KEY | 注册验证码 | 5分钟 |

#### 用户常量 (UserConstants)
| 常量 | 值 | 用途 |
|------|-----|------|
| LOGIN_TOKEN | - | 登录Token前缀 |
| USER | "user" | 普通用户角色 |
| ADMIN | "admin" | 管理员角色 |
| SALT | "fish" | 密码加密盐值 |
| DEFAULT_NICK_NAME | "小鱼籽_" | 注册默认昵称前缀 |

### 12.4 业务规则

#### 注册规则
- 账号长度: 6-11位
- 密码长度: 8-20位
- 确认密码: 必须与密码一致
- 昵称生成: "小鱼籽_" + 6位随机字符串
- 默认头像: GitHub默认头像URL

#### 编辑个人信息规则
- 昵称长度: 5-11位
- 账号长度: 6-11位
- 密码长度: 8-20位
- 只能修改自己的信息 (isMe校验)

#### 管理员操作规则
- 必须具有admin角色
- 编辑用户时密码非空才加密
- 头像为null时设置默认头像
- 封禁/解封是状态切换 (1↔0)
