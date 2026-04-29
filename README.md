# AI.Image.Material.Collaboration.Platform

<div align="center">

![React](https://img.shields.io/badge/React-19.2-61DAFB) ![Vite](https://img.shields.io/badge/Vite-8.0-646CFF) ![Ant Design](https://img.shields.io/badge/Ant_Design-6.3-1677FF) ![React Router](https://img.shields.io/badge/React_Router-7.14-CA4245) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-2.7.6-6DB33F) ![Java](https://img.shields.io/badge/Java-11-007396) ![MySQL](https://img.shields.io/badge/MySQL-8-4479A1) ![Redis](https://img.shields.io/badge/Redis-5.0-DC382D) ![MyBatis Plus](https://img.shields.io/badge/MyBatis_Plus-3.5.15-C6534B) ![Hutool](https://img.shields.io/badge/Hutool-5.8.38-E44D26) ![腾讯云 COS](https://img.shields.io/badge/腾讯云_COS-5.6.227-0052D9) ![Knife4j](https://img.shields.io/badge/Knife4j-4.4.0-009688)

[GitHub](https://github.com/FishDuM/AI.Image.Material.Collaboration.Platform) | [Gitee](https://gitee.com/dumhfdy/AI.Image.Material.Collaboration.Platform) | [头歌](https://code.educoder.net/pfqxsyecz/AI.Image.Material.Collaboration.Platform)

</div>

**FishPics** 是一个基于 React 19 + Spring Boot 的图片分享与互动社区平台，采用前后端分离架构，提供图片管理、社区互动、团队协作、AI 素材管理等核心功能。平台支持用户注册登录、图片浏览管理、帖子发布评论、团队空间协作、后台审核管理等能力，致力于打造简洁高效、安全可靠的图片内容生态。

---

## 项目特色

- 美观流畅的前端界面，支持 PC / 移动端自适应
- 完整的图片生态：上传、查看、收藏、评论、点赞、关注
- 安全可靠的用户体系：登录注册、权限控制、图形验证码防护
- 高性能架构：Spring Session + Redis 会话管理、MyBatis-Plus 分页查询
- 明暗主题切换，个性化用户体验
- 多空间管理：个人空间、团队空间、社区广场
- 管理员后台：用户管理、空间管理、团队管理、AI 管理、权限控制
- 腾讯云 COS 对象存储服务，支持海量图片存储

---

## 技术栈

### 前端 (FishPic-frontend)

- **React 19** + **Vite 8** + **Ant Design 6 组件库**
- **React Router v7** 路由管理与路由守卫
- **Context API** 状态管理 (AuthContext)
- **Axios** 请求封装（拦截器、统一错误处理）
- 响应式布局 / 美观 UI
- ESLint 代码规范检查

### 后端 (FishPics-backend)

- **Spring Boot 2.7.6**
- **Java 11**
- **Spring Session + Redis** 会话管理
- **MyBatis-Plus 3.5.15** ORM框架 + 分页插件
- **MySQL 8+** 关系型数据库
- **Redis** 验证码存储与会话管理
- **Knife4j 4.4.0** API文档生成
- **Hutool 5.8.38** 工具库
- **Lombok** 简化代码
- **AOP** 注解式权限拦截 (AuthCheck)
- **腾讯云 COS** 对象存储服务

---

## 效果预览

- **首页**：登录/注册界面，带图形验证码
  ![首页](doc/picture/p1.gif)
- **用户管理**：管理员后台，支持查询、编辑、封禁/解封用户
  ![用户管理](doc/picture/p2.gif)
- **API 文档**：Knife4j 自动生成的接口文档
  ![API 文档](doc/picture/p3.gif)

---

## 核心功能

### 用户模块

- 用户注册登录，支持图形验证码防护
- 用户信息管理，支持修改头像、昵称、邮箱、手机号
- 隐私设置：关注列表、粉丝列表、点赞/收藏列表可见性控制
- 权限控制，区分普通用户和管理员角色
- 退出登录，安全清除会话状态

### 图片管理模块

- 图片上传至腾讯云 COS 对象存储
- 图片信息记录，包含名称、URL、宽高、大小
- 头像上传，支持用户自定义头像
- 图片状态管控，支持正常、禁用、待审核状态
- 公开性控制，支持图片是否公开到首页

### 帖子管理模块

- 帖子发布，支持标题、内容、关联图片
- 帖子编辑，支持修改已有帖子
- 帖子浏览展示，支持分页查询
- 帖子状态管控，支持正常、禁用、待审核、逻辑删除
- 隐私控制：公开、仅自己可见
- 统计数据：点赞数、收藏数、评论数、查看数
- 封面图设置，提升展示效果

### 评论互动模块

- 帖子评论，支持用户发表评论内容
- 二级评论 / 回复功能，支持回复指定用户
- 评论状态管控，支持正常、禁用、待审核状态

### 收藏与点赞模块

- 帖子收藏，支持用户收藏感兴趣的帖子
- 帖子点赞，支持用户为帖子点赞
- 状态管理，支持取消收藏和取消点赞

### 社交互动模块

- 用户关注，支持关注/取消关注操作
- 粉丝管理，查看粉丝列表和关注列表
- 隐私控制，用户可设置关注/粉丝列表是否公开

### 空间管理模块

- 个人空间，管理个人图片与帖子内容
- 团队空间，支持团队协作与共享素材
- 社区广场，公共内容浏览与互动
- 消息通知，接收系统消息与互动通知

### 后台管理模块

- 用户管理，支持查询、编辑、封禁/解封用户
- 多维度搜索，支持按 ID、用户名、手机号、昵称、角色、状态筛选
- 空间管理，后台统一管理所有空间
- 团队管理，后台统一管理所有团队
- AI 素材管理，后台管理 AI 相关素材
- 权限控制，基于 AOP 注解实现管理员权限拦截

---

## 数据库设计

### 表结构

- **user**: 用户表，包含用户基本信息、权限、状态、隐私设置（账号、密码、头像、邮箱、手机号、昵称、角色、隐私开关）
- **picture**: 图片表，记录图片信息和关联用户（名称、URL、宽高、大小、状态、公开性、帖子关联）
- **post**: 帖子表，管理帖子内容、统计数据和隐私设置（标题、内容、状态、点赞/收藏/评论/查看数、封面）
- **comment**: 评论表，记录用户评论和回复关系（内容、父评论、回复目标用户、状态）
- **user_post_collect**: 用户帖子收藏表
- **user_post_likes**: 用户帖子点赞表
- **user_follows**: 用户关注关系表
- **user_fans**: 用户粉丝关系表

### 索引优化

- 用户表：用户名和昵称唯一索引
- 图片表：用户 ID 和图片名称索引
- 帖子表：用户 ID、标题索引、内容全文索引、状态索引
- 评论表：用户 ID 和帖子 ID 索引
- 关注表：用户 ID 和被关注用户 ID 联合索引
- 粉丝表：用户 ID 和粉丝 ID 联合索引

---

## 项目结构

### 前端项目 (FishPic-frontend)

```
src/
├── api/               # API 请求封装
├── assets/            # 前端静态资源
├── components/        # 公共组件 (ErrorBoundary, FunnyBackground, GlobalLayout, ProtectedRoute)
├── context/           # 状态管理 (AuthContext)
├── pages/             # 页面组件 (HomePage, UserProfile, CommunitySquare, PrivateSpace, TeamSpace, Notifications, Admin 管理等)
├── utils/             # 工具函数 (storage.js)
├── App.jsx            # 路由配置
└── main.jsx           # 应用入口
```

### 后端项目 (FishPics-backend)

```
hk.ljx.fishpicsbackend/
├── common/
│   ├── annotation/    # 权限检查注解
│   ├── aop/           # 权限拦截器
│   ├── config/        # 跨域、COS、JSON、MyBatis Plus 等配置类
│   ├── constants/     # Redis 和用户相关常量定义
│   ├── exception/     # 自定义异常、异常码、全局异常处理器
│   ├── response/      # 统一响应封装工具
│   └── utils/         # 工具类（受限输入流）
├── controller/        # 控制器（图片、帖子、系统、用户接口）
├── dto/
│   ├── base/          # 基础请求参数（删除、分页）
│   ├── picture/       # 图片消息数据传输对象
│   ├── post/          # 帖子编辑、查询、上传请求参数
│   └── user/          # 用户编辑、登录、查询请求参数
├── entity/            # 数据库实体类（评论、图片、帖子、用户、粉丝、收藏、点赞、关注）
├── enums/             # 用户角色枚举
├── mapper/            # MyBatis Plus 数据访问层接口
├── service/
│   ├── impl/          # 业务逻辑接口实现类
└── vo/
    ├── picture/       # 图片帖子视图对象
    ├── post/          # 帖子详情和列表视图对象
    └── user/          # 验证码、登录、用户信息视图对象
```

---

## 快速启动

### 环境要求

- Java 11+
- Maven 3.6+
- MySQL 8+
- Redis 5.0+
- Node.js 18+
- npm 9+ 或 yarn

### 后端启动

1. 创建 MySQL 数据库 `FishPics`，执行 `src/sql/create.sql` 脚本初始化表结构
2. 修改 `src/main/resources/application.yml` 中的数据库和 Redis 连接信息
3. 配置腾讯云 COS 信息（默认注释状态）
4. 启动 Spring Boot 应用（运行 `FishPicsBackendApplication.java`）
5. 访问 API 文档：`http://localhost:8080/api/doc.html`

### 前端启动

```bash
cd src/FishPic-frontend
npm install
npm run dev
```

### 访问应用

- 前端应用：`http://localhost:5173`
- 后端 API：`http://localhost:8080/api`
- API 文档：`http://localhost:8080/api/doc.html`

---

## 技术亮点

- **前后端分离架构**，职责清晰，易于维护
- **Spring Session + Redis** 会话管理，支持分布式部署
- **图形验证码机制**，防止机器暴力破解
- **Redis 存储验证码**，验证码过期自动清理
- **AOP 注解式权限拦截**，基于 @AuthCheck 注解的细粒度权限控制
- **统一异常处理**，全局错误捕获和响应 (GlobalExceptionHandler)
- **统一响应格式**，规范的 API 返回 (Response<T>)
- **分页查询优化**，MyBatis-Plus 分页插件，大数据量处理高效
- **腾讯云 COS 对象存储**，海量图片存储
- **DTO/VO 分层设计**，数据传输与视图分离
- **隐私控制机制**，用户可自定义个人数据可见性
- **二级评论系统**，支持回复指定用户
- **帖子统计功能**，点赞数、收藏数、评论数、查看数实时统计
- **错误边界组件**，前端异常优雅降级
- **路由保护组件**，基于角色的路由守卫 (ProtectedRoute)
- **Context API 状态管理**，轻量级状态管理

---

## 数据库设计详情

### 用户表 (user)

| 字段                    | 类型         | 说明                               |
| ----------------------- | ------------ | ---------------------------------- |
| id                      | bigint       | 主键，自增                         |
| username                | varchar(32)  | 用户名（唯一）                     |
| password                | varchar(128) | 密码                               |
| avatar                  | varchar(256) | 头像 URL                           |
| email                   | varchar(64)  | 邮箱                               |
| phone                   | varchar(16)  | 手机号                             |
| nickname                | varchar(32)  | 昵称（唯一）                       |
| status                  | tinyint      | 状态：1-正常，0-禁用，2-待审核     |
| is_delete               | tinyint      | 逻辑删除：0-未删除，1-已删除       |
| role                    | varchar(32)  | 用户权限角色                       |
| like_num                | bigint       | 收到的点赞数                       |
| collect_num             | bigint       | 收藏数                             |
| is_private_follows      | tinyint      | 关注列表隐私：0-公开，1-不公开     |
| is_private_post_collect | tinyint      | 收藏帖子列表隐私：0-公开，1-不公开 |
| is_private_likes        | tinyint      | 点赞帖子列表隐私：0-公开，1-不公开 |
| is_private_fans         | tinyint      | 粉丝列表隐私：0-公开，1-不公开     |
| create_time             | datetime     | 创建时间                           |
| update_time             | datetime     | 更新时间                           |

### 图片表 (picture)

| 字段         | 类型         | 说明                                 |
| ------------ | ------------ | ------------------------------------ |
| id           | bigint       | 主键，自增                           |
| user_id      | bigint       | 用户 ID                              |
| picture_name | bigint       | 图片名称                             |
| url          | varchar(512) | 图片地址                             |
| width        | varchar(32)  | 宽度                                 |
| height       | varchar(32)  | 高度                                 |
| size         | varchar(32)  | 大小                                 |
| status       | tinyint      | 状态：1-正常，0-禁用，2-待审核       |
| is_private   | tinyint      | 公开性：0-不公开到首页，1-公开到首页 |
| post_id      | bigint       | 关联帖子 ID                          |
| create_time  | datetime     | 创建时间                             |
| update_time  | datetime     | 更新时间                             |

### 帖子表 (post)

| 字段         | 类型         | 说明                           |
| ------------ | ------------ | ------------------------------ |
| id           | bigint       | 主键，自增                     |
| user_id      | bigint       | 发帖用户 ID                    |
| title        | varchar(256) | 标题                           |
| content      | text         | 内容                           |
| status       | tinyint      | 状态：1-正常，0-禁用，2-待审核 |
| is_delete    | int          | 逻辑删除：0-未删除，1-已删除   |
| likes_num    | bigint       | 点赞数                         |
| collects_num | bigint       | 收藏数                         |
| comment_num  | int          | 评论数                         |
| views_num    | bigint       | 查看数                         |
| is_private   | tinyint      | 隐私：0-公开，1-仅自己可见     |
| cover        | bigint       | 封面图片 ID                    |
| create_time  | datetime     | 创建时间                       |
| update_time  | datetime     | 更新时间                       |

### 评论表 (comment)

| 字段        | 类型     | 说明                             |
| ----------- | -------- | -------------------------------- |
| id          | bigint   | 主键，自增                       |
| user_id     | bigint   | 评论用户 ID                      |
| post_id     | bigint   | 帖子 ID                          |
| content     | text     | 评论内容                         |
| parent_id   | bigint   | 父评论 ID（支持二级评论 / 回复） |
| to_user_id  | int      | 回复目标用户 ID                  |
| status      | tinyint  | 状态：1-正常，0-禁用，2-待审核   |
| create_time | datetime | 创建时间                         |

---

## 安全设计

- **Spring Session + Redis**：分布式会话管理，会话过期自动清理
- **图形验证码**：防止机器暴力破解
- **Redis 存储验证码**：验证码过期自动清理
- **AOP 权限拦截**：基于 @AuthCheck 注解的细粒度权限控制
- **逻辑删除**：数据安全，支持恢复
- **状态管理**：用户/内容状态控制，支持封禁/审核
- **隐私控制**：用户可自定义个人数据可见性

---

## 开发规范

- 前端：ESLint 代码规范检查
- 后端：统一响应格式 `Response<T>`
- 异常处理：全局异常捕获，统一错误码
- 数据层：DTO/VO 分层设计，避免实体暴露
- 路由保护：基于角色的路由守卫 (ProtectedRoute)
- 状态管理：Context API 集中管理用户状态
- 权限控制：基于 AOP 注解的管理员权限拦截

---
