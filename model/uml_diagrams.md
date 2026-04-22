# AI.Image.Material.Collaboration.Platform UML 模型图

## 1. 用例图 (Use Case Diagram)

### 1.1 系统参与者 (Actors)
- **普通用户 (Regular User)**
- **管理员 (Administrator)**

### 1.2 用例列表

#### 普通用户用例
- 注册账户
- 登录系统
- 浏览图片/帖子
- 上传图片
- 创建帖子
- 编辑个人资料
- 发表评论
- 点赞/收藏内容
- 在线编辑图片
- 查看个人中心

#### 管理员用例
- 用户管理（审核、禁用）
- 图片审核
- 帖子审核
- 评论审核
- 系统监控
- 数据统计

### 1.3 用例关系
- **包含关系 (Include)**:
  - 创建帖子 → 上传图片
  - 编辑个人资料 → 上传头像
  
- **扩展关系 (Extend)**:
  - 浏览内容 ←(可选)← 搜索功能
  - 上传图片 ←(可选)← 批量上传

## 2. 类图 (Class Diagram)

### 2.1 核心类定义

#### User 类
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
| + login(): boolean  |
| + register(): void  |
| + updateProfile(): void |
+---------------------+
```

#### Picture 类
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
| + upload(): void    |
| + edit(): void      |
| + delete(): void    |
+---------------------+
```

#### Post 类
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
| + create(): void    |
| + update(): void    |
| + delete(): void    |
+---------------------+
```

#### Comment 类
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
| + addComment(): void|
| + deleteComment(): void |
+---------------------+
```

### 2.2 类间关系

#### 关联关系 (Association)
- User "1" —— "*" Picture (一个用户拥有多个图片)
- User "1" —— "*" Post (一个用户发布多个帖子)
- User "1" —— "*" Comment (一个用户发表多个评论)
- Post "1" —— "*" Comment (一个帖子有多个评论)

#### 聚合关系 (Aggregation)
- Post ◇—— "1..*" Picture (帖子聚合多个图片，通过pictureIds字段)

#### 继承关系 (Inheritance)
- 无显式继承关系，但可通过角色实现权限差异

## 3. 顺序图 (Sequence Diagram)

### 3.1 用户登录流程

```
用户 -> 前端: 输入用户名密码
前端 -> 后端(UserController): POST /user/login
后端 -> UserService: 验证用户凭证
UserService -> UserMapper: 查询用户信息
UserMapper -> MySQL: SELECT * FROM user WHERE username = ?
MySQL --> UserMapper: 返回用户数据
UserMapper --> UserService: 返回用户对象
UserService -> Sa-Token: 生成JWT令牌
Sa-Token --> UserService: 返回令牌
UserService --> UserController: 返回登录结果
UserController --> 前端: 返回登录成功和令牌
前端 --> 用户: 跳转到首页
```

### 3.2 图片上传流程

```
用户 -> 前端: 选择图片文件
前端 -> 后端(PictureController): POST /picture/upload
后端 -> PictureService: 处理图片上传
PictureService -> 文件系统: 保存图片文件
文件系统 --> PictureService: 返回文件路径
PictureService -> PictureMapper: 插入图片记录
PictureMapper -> MySQL: INSERT INTO picture (...)
MySQL --> PictureMapper: 返回插入结果
PictureMapper --> PictureService: 返回图片ID
PictureService --> PictureController: 返回上传结果
PictureController --> 前端: 返回图片URL和ID
前端 --> 用户: 显示上传成功
```

### 3.3 发布帖子流程

```
用户 -> 前端: 填写帖子信息(标题、内容、图片)
前端 -> 后端(PostController): POST /post/create
后端 -> PostService: 创建帖子
PostService -> PostMapper: 插入帖子记录
PostMapper -> MySQL: INSERT INTO post (...)
MySQL --> PostMapper: 返回帖子ID
PostMapper --> PostService: 返回帖子对象
PostService --> PostController: 返回创建结果
PostController --> 前端: 返回帖子详情
前端 --> 用户: 显示帖子发布成功
```

### 3.4 发表评论流程

```
用户 -> 前端: 输入评论内容
前端 -> 后端(CommentController): POST /comment/add
后端 -> CommentService: 添加评论
CommentService -> CommentMapper: 插入评论记录
CommentMapper -> MySQL: INSERT INTO comment (...)
MySQL --> CommentMapper: 返回评论ID
CommentMapper --> CommentService: 返回评论对象
CommentService --> CommentController: 返回添加结果
CommentController --> 前端: 返回评论详情
前端 --> 用户: 显示评论成功
```

## 4. 状态图 (State Diagram)

### 4.1 用户状态图

```
[待审核] --(审核通过)--> [正常]
[待审核] --(审核拒绝)--> [禁用]
[正常] --(违规操作)--> [禁用]
[禁用] --(申诉通过)--> [正常]
[正常] --(主动注销)--> [逻辑删除]
```

### 4.2 内容状态图 (图片/帖子/评论通用)

```
[待审核] --(审核通过)--> [正常]
[待审核] --(审核拒绝)--> [禁用]
[正常] --(举报/违规)--> [禁用]
[禁用] --(申诉通过)--> [正常]
[正常] --(用户删除)--> [逻辑删除]
```

## 5. 组件图 (Component Diagram)

### 5.1 前端组件
- **UI Components**: Ant Design Vue 组件库
- **Routing**: Vue Router
- **State Management**: Pinia
- **HTTP Client**: Axios
- **Build Tool**: Vite

### 5.2 后端组件
- **Web Framework**: SpringBoot
- **ORM**: MyBatis-Plus
- **Authentication**: Sa-Token
- **Cache**: Redis
- **Database**: MySQL
- **Utilities**: Hutool

### 5.3 组件依赖关系
- 前端 ↔ REST API ↔ 后端控制器
- 后端控制器 → 服务层 → 数据访问层 → 数据库
- 服务层 ↔ Redis缓存
- 认证组件 ↔ 用户服务

## 6. 部署图 (Deployment Diagram)

### 6.1 物理节点
- **客户端**: Web浏览器 (PC/Mobile)
- **Web服务器**: 托管前端静态资源
- **应用服务器**: 运行SpringBoot应用
- **数据库服务器**: MySQL实例
- **缓存服务器**: Redis实例
- **文件存储**: 本地存储或云存储

### 6.2 网络连接
- 客户端 ↔ HTTPS ↔ Web服务器
- Web服务器 ↔ API调用 ↔ 应用服务器
- 应用服务器 ↔ JDBC ↔ 数据库服务器
- 应用服务器 ↔ Redis协议 ↔ 缓存服务器
- 应用服务器 ↔ 文件系统 ↔ 文件存储

## 7. 活动图 (Activity Diagram)

### 7.1 用户注册活动流
```
开始 → 填写注册信息 → 验证信息格式 → 
保存用户数据 → 设置状态为"待审核" → 
发送验证邮件(可选) → 结束
```

### 7.2 内容审核活动流
```
开始 → 获取待审核内容 → 
管理员查看内容 → 决定审核结果 → 
更新内容状态 → 通知用户(可选) → 结束
```

## 8. 包图 (Package Diagram)

### 8.1 后端包结构
- **hk.ljx.fishpicsbackend**
  - **common**: 公共组件(注解、配置、异常处理、响应封装)
  - **controller**: 控制器层
  - **dto**: 数据传输对象
  - **entity**: 实体类
  - **enums**: 枚举类
  - **mapper**: 数据访问接口
  - **service**: 服务接口及实现
    - **impl**: 服务实现类

### 8.2 前端包结构 (基于Vue3)
- **src**
  - **assets**: 静态资源
  - **components**: 可复用组件
  - **views**: 页面组件
  - **router**: 路由配置
  - **store**: 状态管理(Pinia)
  - **api**: API请求封装
  - **utils**: 工具函数

## 9. 设计模式应用

### 9.1 使用的设计模式
- **MVC模式**: 前后端分离架构
- **Repository模式**: 数据访问层抽象
- **Service模式**: 业务逻辑封装
- **DTO模式**: 数据传输对象
- **Singleton模式**: 配置类、工具类
- **Observer模式**: 状态变更通知(可扩展)

### 9.2 模式优势
- **高内聚低耦合**: 各层职责清晰
- **可维护性**: 修改某一层不影响其他层
- **可测试性**: 各组件可独立测试
- **可扩展性**: 易于添加新功能

## 10. 性能与安全考虑

### 10.1 性能优化点
- **缓存策略**: Redis缓存热点数据
- **数据库索引**: userId、status等字段建立索引
- **分页查询**: 大数据量分页处理
- **异步处理**: 耗时操作异步化(可扩展)

### 10.2 安全措施
- **输入验证**: 前后端双重验证
- **SQL注入防护**: MyBatis-Plus参数绑定
- **XSS防护**: 内容过滤和转义
- **CSRF防护**: Token验证
- **权限控制**: 基于角色的访问控制
- **敏感数据**: 密码加密存储