# AI.Image.Material.Collaboration.Platform UML 模型图

## 1. 用例图 (Use Case Diagram)

### 1.1 系统参与者 (Actors)
- **普通用户 (User)**：可注册、登录、浏览内容
- **管理员 (Admin)**：可管理用户、封禁/解封账号

### 1.2 用例列表

#### 普通用户用例
- 注册账户（图形验证码验证）
- 登录系统（图形验证码验证）
- 浏览首页
- 切换主题（亮色/暗色）
- 退出登录
- 查看个人信息

#### 管理员用例
- 获取用户列表（分页查询）
- 多维度搜索用户（ID、用户名、手机号、昵称、角色、状态）
- 编辑用户信息（修改资料、密码）
- 封禁/解封用户
- 管理系统功能

### 1.3 用例关系
- **包含关系 (Include)**:
  - 注册账户 → 获取图形验证码
  - 登录系统 → 获取图形验证码
  - 注册账户 → 验证用户协议
  
- **前置条件 (Precondition)**:
  - 用户管理 ← 管理员已登录
  - 编辑用户 ← 用户存在且为管理员权限

## 2. 类图 (Class Diagram)

### 2.1 核心实体类

#### User 实体类
```
+---------------------+
|       User          |
+---------------------+
| - id: Long          |
| - username: String  |
| - password: String  |
| - avatar: String    |
| - email: String     |
| - phone: String     |
| - nickname: String  |
| - status: Integer   |
| - delete: Integer   |
| - role: String      |
| - createTime: Date  |
| - updateTime: Date  |
+---------------------+
```

#### Picture 实体类
```
+---------------------+
|      Picture        |
+---------------------+
| - id: Long          |
| - userId: Long      |
| - pictureName: Long |
| - url: String       |
| - width: String     |
| - height: String    |
| - size: String      |
| - status: Integer   |
| - createTime: Date  |
| - updateTime: Date  |
+---------------------+
```

#### Post 实体类
```
+---------------------+
|       Post          |
+---------------------+
| - id: Long          |
| - userId: Long      |
| - title: String     |
| - content: String   |
| - pictureIds: String|
| - status: Integer   |
| - delete: Integer   |
| - createTime: Date  |
| - updateTime: Date  |
+---------------------+
```

#### Comment 实体类
```
+---------------------+
|      Comment        |
+---------------------+
| - id: Long          |
| - userId: Long      |
| - postId: Long      |
| - content: String   |
| - status: Integer   |
| - createTime: Date  |
| - updateTime: Date  |
+---------------------+
```

### 2.2 数据传输对象 (DTO)

#### UserLoginRequest
```
+---------------------+
| UserLoginRequest    |
+---------------------+
| - username: String  |
| - password: String  |
| - checkCode: String |
| - captchaKey: String|
+---------------------+
```

#### UserRequestRequest (注册)
```
+---------------------+
| UserRequestRequest  |
+---------------------+
| - username: String  |
| - password: String  |
| - checkPassword: String |
| - checkCode: String |
| - captchaKey: String|
+---------------------+
```

#### UserQueryWrapper
```
+---------------------+
| UserQueryWrapper    |
+---------------------+
| - id: Long          |
| - username: String  |
| - email: String     |
| - phone: String     |
| - nickname: String  |
| - role: String      |
| - status: Integer   |
| - createTime: Date  |
| - current: long     |
| - pageSize: long    |
| - sortField: String |
| - sortOrder: String |
+---------------------+
```

#### UserEditRequest
```
+---------------------+
| UserEditRequest     |
+---------------------+
| - id: Long          |
| - username: String  |
| - password: String  |
| - nickname: String  |
| - avatar: String    |
| - email: String     |
| - phone: String     |
| - role: String      |
| - status: Integer   |
+---------------------+
```

### 2.3 视图对象 (VO)

#### UserLoginVO
```
+---------------------+
|    UserLoginVO      |
+---------------------+
| - id: Long          |
| - username: String  |
| - nickname: String  |
| - avatar: String    |
| - role: String      |
| - status: Integer   |
+---------------------+
```

#### CheckCodeVO
```
+---------------------+
|    CheckCodeVO      |
+---------------------+
| - captchaKey: String|
| - base64Image: String|
+---------------------+
```

### 2.4 控制器类

#### UserController
```
+---------------------+
|  UserController     |
+---------------------+
| - userService       |
+---------------------+
| + userLogin()       |
| + userRegister()    |
| + checkCodeLogin()  |
| + checkCodeRegister()|
| + getUserList()     |
| + setStatus()       |
| + editUser()        |
+---------------------+
```

### 2.5 服务层类

#### UserService 接口
```
+---------------------+
|  UserService        |
+---------------------+
| + getCheckCode()    |
| + getLoginUser()    |
| + userRegister()    |
| + userLogin()       |
| + newQueryWrapper() |
| + getUserList()     |
| + setStatus()       |
| + editUser()        |
+---------------------+
```

#### UserServiceImpl
```
+---------------------+
|  UserServiceImpl    |
+---------------------+
| - stringRedisTemplate|
| - userMapper        |
+---------------------+
| + getCheckCode()    |
| + getLoginUser()    |
| + userRegister()    |
| + userLogin()       |
| + newQueryWrapper() |
| + getUserList()     |
| + setStatus()       |
| + editUser()        |
+---------------------+
```

### 2.6 类间关系

#### 关联关系 (Association)
- User "1" —— "*" Picture (一个用户拥有多个图片)
- User "1" —— "*" Post (一个用户发布多个帖子)
- User "1" —— "*" Comment (一个用户发表多个评论)
- Post "1" —— "*" Comment (一个帖子有多个评论)

#### 聚合关系 (Aggregation)
- Post ◇—— "1..*" Picture (帖子聚合多个图片，通过pictureIds字段)

#### 依赖关系 (Dependency)
- UserController → UserService (控制器依赖服务层)
- UserServiceImpl → UserMapper (服务实现依赖数据访问层)
- UserServiceImpl → StringRedisTemplate (服务实现依赖Redis)

#### 实现关系 (Realization)
- UserServiceImpl —|> UserService (实现接口)

## 3. 顺序图 (Sequence Diagram)

### 3.1 用户注册流程

```
用户 -> 前端: 填写注册信息+验证码
前端 -> 后端(UserController): GET /user/checkCode/register
后端 -> UserService: getCheckCode()
UserService -> Redis: 生成验证码并存储
Redis --> UserService: 返回验证码
UserService --> 后端: 返回base64图片
后端 --> 前端: 返回CheckCodeVO

前端 -> 后端: POST /user/register
后端 -> UserService: userRegister()
UserService -> Redis: 获取并验证验证码
UserService -> UserMapper: 检查用户名是否存在
UserMapper -> MySQL: SELECT COUNT(*) FROM user
MySQL --> UserMapper: 返回结果
UserService -> Hutool: MD5加密密码(加盐: fish)
UserService -> UserMapper: INSERT INTO user
UserMapper -> MySQL: 插入用户记录
MySQL --> UserMapper: 返回插入结果
UserMapper --> UserService: 返回成功
UserService -> Redis: 删除验证码
UserService --> 后端: 返回注册成功
后端 --> 前端: Response<Boolean>
前端 --> 用户: 显示注册成功
```

### 3.2 用户登录流程

```
用户 -> 前端: 输入用户名密码+验证码
前端 -> 后端(UserController): GET /user/checkCode/login
后端 -> UserService: getCheckCode()
UserService -> Redis: 生成验证码并存储
Redis --> UserService: 返回验证码
UserService --> 后端: 返回base64图片
后端 --> 前端: 返回CheckCodeVO

前端 -> 后端: POST /user/login
后端 -> UserService: userLogin()
UserService -> Hutool: MD5加密密码(加盐: fish)
UserService -> Redis: 获取并验证验证码
UserService -> UserMapper: SELECT * FROM user WHERE username=? AND password=?
UserMapper -> MySQL: 查询用户信息
MySQL --> UserMapper: 返回用户数据
UserMapper --> UserService: 返回User对象
UserService -> Redis: 存储用户登录Token(有效期1天)
Redis --> UserService: 存储成功
UserService --> 后端: 返回UserLoginVO + 设置Authorization头
后端 --> 前端: Response<UserLoginVO> + Header: Authorization
前端 -> localStorage: 保存用户信息
前端 --> 用户: 显示登录成功并跳转
```

### 3.3 管理员获取用户列表流程

```
管理员 -> 前端: 访问用户管理页面
前端 -> 后端(UserController): POST /user/admin/userList
后端 -> UserService: getUserList()
UserService -> UserService: newQueryWrapper() (构造查询条件)
UserService -> UserMapper: selectPage()
UserMapper -> MySQL: SELECT * FROM user LIMIT offset,pageSize
MySQL --> UserMapper: 返回分页数据
UserMapper --> UserService: IPage<User>
UserService --> 后端: 返回分页结果
后端 --> 前端: Response<IPage<User>>
前端 --> 管理员: 渲染用户列表表格
```

### 3.4 管理员封禁/解封用户流程

```
管理员 -> 前端: 点击封禁/解封按钮
前端 -> 后端(UserController): POST /user/admin/setStatus
后端 -> UserService: setStatus()
UserService -> UserMapper: SELECT * FROM user WHERE id=?
UserMapper -> MySQL: 查询用户信息
MySQL --> UserMapper: 返回用户数据
UserMapper --> UserService: 返回User对象
UserService: 切换用户状态(1→0 或 0→1)
UserService -> UserMapper: UPDATE user SET status=?
UserMapper -> MySQL: 更新用户状态
MySQL --> UserMapper: 返回更新结果
UserMapper --> UserService: 返回成功
UserService --> 后端: Response<Boolean>
后端 --> 前端: 返回操作结果
前端 --> 管理员: 显示操作成功并刷新列表
```

### 3.5 管理员编辑用户信息流程

```
管理员 -> 前端: 填写编辑表单
前端 -> 后端(UserController): POST /user/admin/editUser
后端 -> UserService: editUser()
UserService -> UserMapper: SELECT * FROM user WHERE id=?
UserMapper -> MySQL: 查询用户信息
MySQL --> UserMapper: 返回用户数据
UserMapper --> UserService: 返回User对象
UserService: 判断是否需要更新密码
UserService -> Hutool: MD5加密新密码(如需要)
UserService -> BeanUtil: copyProperties() (拷贝非空字段)
UserService -> UserMapper: UPDATE user SET ...
UserMapper -> MySQL: 更新用户信息
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
[正常(1)] --(用户注销)--> [逻辑删除(delete=1)]
```

### 4.2 内容状态图 (图片/帖子/评论通用)

```
[正常(1)] --(禁用操作)--> [禁用(0)]
[禁用(0)] --(启用操作)--> [正常(1)]
[正常(1)] --(删除操作)--> [逻辑删除(delete=1)]
```

## 5. 组件图 (Component Diagram)

### 5.1 前端组件 (FishPic-frontend)
- **React 19**: UI框架
- **Ant Design 6**: UI组件库
- **React Router v7**: 路由管理
- **Context API**: 主题状态管理 (ThemeContext)
- **Axios**: HTTP客户端
- **localStorage**: 用户信息持久化
- **Vite**: 构建工具

### 5.2 后端组件 (AI.Image.Material.Collaboration.Platform)
- **Spring Boot 2.7.6**: Web框架
- **MyBatis-Plus 3.5.15**: ORM框架 + 分页插件
- **MySQL 8+**: 关系型数据库
- **Redis**: 缓存服务 (验证码、登录Token)
- **Hutool**: 工具库 (加密、验证码、JSON)
- **Knife4j**: API文档生成
- **Lombok**: 代码简化

### 5.3 组件依赖关系
```
前端(React 19)
  ↓ HTTP请求
后端控制器(UserController)
  ↓
服务层(UserService/Impl)
  ├─→ Redis (验证码/登录Token)
  └─→ MyBatis-Plus (数据访问)
        ↓
      MySQL数据库
```

## 6. 部署图 (Deployment Diagram)

### 6.1 物理节点
- **客户端**: Web浏览器 (PC/Mobile)
- **开发服务器**: Vite Dev Server (localhost:5173)
- **应用服务器**: Spring Boot (localhost:8080/api)
- **数据库服务器**: MySQL 8+ (localhost:3306)
- **缓存服务器**: Redis (192.168.163.101:6379)

### 6.2 网络连接
- 客户端 ↔ HTTP ↔ Vite Dev Server (开发模式)
- Vite ↔ 代理配置 (/api) ↔ Spring Boot
- Spring Boot ↔ JDBC ↔ MySQL
- Spring Boot ↔ Redis协议 ↔ Redis

### 6.3 端口配置
- 前端开发服务器: 5173 (Vite默认)
- 后端API服务: 8080
- API上下文路径: /api
- MySQL: 3306
- Redis: 6379

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
勾选用户协议
  ↓
前端校验
  ↓ [通过]
POST /user/register
  ↓
后端验证验证码(Redis)
  ↓ [通过]
检查用户名是否已存在(MySQL)
  ↓ [不存在]
密码MD5加密(加盐: fish)
  ↓
生成默认昵称(小鱼籽_+随机6位)
  ↓
插入用户表(MySQL)
  ↓
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
前端校验
  ↓ [通过]
POST /user/login
  ↓
后端获取验证码(校验Redis)
  ↓ [通过]
密码MD5加密(加盐: fish)
  ↓
查询用户表(MySQL)
  ↓ [找到匹配记录]
生成Token(LOGIN-+userId)
  ↓
存储用户信息到Redis(1天)
  ↓
设置Authorization响应头
  ↓
返回UserLoginVO
  ↓
前端保存用户信息到localStorage
  ↓
登录成功
  ↓
结束
```

### 7.3 用户管理活动流
```
开始
  ↓
验证管理员权限(role=admin)
  ↓ [通过]
加载用户管理页面
  ↓
输入搜索条件(可选)
  ↓
POST /user/admin/userList
  ↓
构造查询条件(QueryWrapper)
  ↓
分页查询用户表(MySQL)
  ↓
返回IPage<User>
  ↓
渲染用户列表表格
  ↓
[管理员操作]
  ├─→ 编辑用户: POST /user/admin/editUser
  └─→ 封禁/解封: POST /user/admin/setStatus
  ↓
刷新用户列表
  ↓
结束
```

## 8. 包图 (Package Diagram)

### 8.1 后端包结构
```
hk.ljx.fishpicsbackend
├── common (公共组件)
│   ├── annotation (注解)
│   │   └── AuthCheck (权限校验注解)
│   ├── aop (切面)
│   │   └── AuthInterceptor (权限拦截器)
│   ├── config (配置)
│   │   ├── CorsConfig (跨域配置)
│   │   ├── JsonConfig (JSON配置)
│   │   └── MybatisPlusConfig (MyBatis分页配置)
│   ├── constants (常量)
│   │   ├── RedisConstants (Redis键常量)
│   │   └── UserConstants (用户常量)
│   ├── exception (异常)
│   │   ├── BaseException (基础异常)
│   │   ├── ExcUtils (异常工具类)
│   │   ├── ExceptionCode (异常码)
│   │   └── GlobalExceptionHandler (全局异常处理)
│   └── response (响应)
│       ├── ResUtils (响应工具)
│       └── Response (响应封装类)
├── controller (控制器)
│   └── UserController
├── dto (数据传输对象)
│   ├── base
│   │   ├── DeleteById (删除请求)
│   │   └── PageRequest (分页请求)
│   └── user
│       ├── UserEditRequest (编辑用户)
│       ├── UserIdRequest (用户ID请求)
│       ├── UserLoginRequest (登录请求)
│       ├── UserQueryWrapper (查询条件)
│       └── UserRequestRequest (注册请求)
├── entity (实体类)
│   ├── Comment
│   ├── Picture
│   ├── Post
│   └── User
├── enums (枚举)
│   └── UserRoleEnum (用户角色: admin/user)
├── mapper (数据访问层)
│   ├── CommentMapper
│   ├── PictureMapper
│   ├── PostMapper
│   └── UserMapper
├── service (业务逻辑层)
│   ├── CommentService
│   ├── PictureService
│   ├── PostService
│   ├── UserService
│   └── impl
│       ├── CommentServiceImpl
│       ├── PictureServiceImpl
│       ├── PostServiceImpl
│       └── UserServiceImpl
└── vo (视图对象)
    ├── CheckCodeVO (验证码VO)
    └── UserLoginVO (登录用户VO)
```

### 8.2 前端包结构
```
src/
├── api/
│   └── index.js (API请求封装)
├── assets/ (静态资源)
├── pages/ (页面组件)
│   ├── HomePage.jsx (首页/登录/注册)
│   ├── UserManagement.jsx (用户管理)
│   ├── AdminUserList.jsx (管理员用户列表)
│   ├── NotFound.jsx (404页面)
│   └── *.css (样式文件)
├── utils/ (工具函数)
│   └── storage.js (localStorage操作)
├── App.jsx (主应用组件)
├── App.css (应用样式)
├── main.jsx (入口文件)
└── index.css (全局样式)
```

### 8.3 包依赖关系
```
controller → service → mapper → MySQL
controller → Redis (验证码/登录Token)
common → controller/exception/response
dto → controller/service
entity → mapper/service
enums → service
vo → controller
```

## 9. 设计模式应用

### 9.1 使用的设计模式
- **MVC模式**: 前后端分离架构
- **Repository模式**: MyBatis-Plus数据访问层
- **Service模式**: 业务逻辑封装
- **DTO模式**: 数据传输对象 (Request/Response)
- **Singleton模式**: Spring Bean单例
- **Factory模式**: Spring IoC容器
- **Template Method模式**: MyBatis-Plus BaseMapper
- **Chain of Responsibility模式**: Spring Interceptor链

### 9.2 模式优势
- **高内聚低耦合**: 各层职责清晰
- **可维护性**: 修改某一层不影响其他层
- **可测试性**: 各组件可独立测试
- **可扩展性**: 易于添加新功能
- **类型安全**: 强类型语言和泛型支持

## 10. 性能与安全考虑

### 10.1 性能优化点
- **分页查询**: MyBatis-Plus分页插件优化大数据量查询
- **Redis缓存**: 验证码和登录Token存储在Redis中
- **数据库索引**: userId、username、nickname建立索引
- **按需加载**: 前端按需渲染组件
- **响应式设计**: 移动端适配提升加载速度

### 10.2 安全措施
- **输入验证**: 前后端双重参数校验
- **SQL注入防护**: MyBatis-Plus参数化查询
- **XSS防护**: React自动转义
- **CSRF防护**: 跨域CORS配置
- **密码加密**: MD5 + 盐值(fish)
- **验证码防护**: 图形验证码防暴力破解
- **Token认证**: Redis存储登录状态
- **权限控制**: 基于角色的访问控制(admin/user)
- **逻辑删除**: 数据安全保护

### 10.3 常量管理
- **Redis键常量**: LOGIN_CODE_KEY, REGISTER_CODE_KEY
- **用户常量**: LOGIN_TOKEN, USER, ADMIN, SALT, DEFAULT_NICK_NAME
- **响应格式**: code(1-成功), message, data