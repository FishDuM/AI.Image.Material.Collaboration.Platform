# AI.Image.Material.Collaboration.Platform

 FishPics 是一个高颜值、功能完善的**图片分享与互动社区平台**，基于前后端分离架构设计，提供图片上传、在线预览、评论互动、权限管理等核心能力。平台采用 React 19 + Spring Boot 技术栈，支持用户注册登录、图片浏览管理、帖子发布评论、后台审核管理等功能，致力于打造简洁高效、安全可靠的图片内容生态。

---

## 项目特色
- 美观流畅的前端界面，支持 PC / 移动端自适应
- 完整的图片生态：上传、查看、编辑、收藏、评论
- 安全可靠的用户体系：登录、注册、权限控制、验证码防护
- 高性能架构：Redis 缓存、接口优化、分页查询
- 明暗主题切换，个性化用户体验
- 管理员后台：用户管理、权限控制、状态管理

---

##  技术栈
### 前端 (FishPic-frontend)
- **React 19** + **Vite** + **Ant Design 6 组件库**
- **React Router v7** 路由管理
- **Context API** 状态管理
- **Axios** 请求封装
- 响应式布局 / 美观 UI
- 暗色模式支持

### 后端 (AI.Image.Material.Collaboration.Platform)
- **Spring Boot 2.7.6**
- **JWT**
- **MyBatis-Plus 3.5.15** ORM框架
- **MySQL 8+** 关系型数据库
- **Redis** 缓存服务
- **Knife4j** API文档生成
- **Hutool** 工具库
- **Lombok** 简化代码

---

## 核心功能
### 用户模块
- 用户注册、登录（带图形验证码）
- 用户信息管理（头像、昵称、邮箱、手机号）
- 权限控制（普通用户、管理员）
- 退出登录、状态管理

### 图片管理模块
- 图片上传与存储
- 图片信息管理（名称、URL、尺寸、大小）
- 图片状态管理（正常、禁用、待审核）

### 帖子管理模块
- 帖子创建（标题、内容、关联图片）
- 帖子展示与浏览
- 帖子状态管理（正常、禁用、待审核、逻辑删除）

### 评论互动模块
- 评论功能（对帖子进行评论）
- 评论状态管理（正常、禁用、待审核）

### 后台管理模块
- 用户管理（查询、编辑、封禁/解封）
- 分页查询用户列表
- 多维度搜索（ID、用户名、手机号、昵称、角色、状态）
- 管理员权限控制

---

## 数据库设计
### 表结构
- **user**: 用户表，包含用户基本信息、权限和状态
- **picture**: 图片表，记录图片信息和关联用户
- **post**: 帖子表，管理帖子内容和关联图片
- **comment**: 评论表，记录用户评论内容

### 索引优化
- 用户表：用户名和昵称唯一索引
- 图片表：用户ID和图片名称索引
- 帖子表：用户ID和标题索引
- 评论表：用户ID和帖子ID索引

---

## 项目结构
```
AI.Image.Material.Collaboration.Platform/
├── doc/                            # 文档目录
│   └── software_requirements_model.md  # 软件需求模型
├── model/                          # 模型目录
│   └── uml_diagrams.md             # UML图说明
└── src/
    ├── FishPic-frontend/           # 前端项目 (React 19)
    │   ├── src/
    │   │   ├── api/                # API请求封装
    │   │   ├── pages/              # 页面组件
    │   │   │   ├── HomePage.jsx    # 首页（登录/注册）
    │   │   │   ├── UserManagement.jsx  # 用户管理
    │   │   │   └── AdminUserList.jsx   # 管理员用户列表
    │   │   ├── utils/              # 工具函数
    │   │   └── App.jsx             # 主应用组件
    │   ├── public/                 # 静态资源
    │   └── package.json
    └── AI.Image.Material.Collaboration.Platform/           # 后端项目 (Spring Boot)
        ├── src/
        │   ├── main/
        │   │   ├── java/hk/ljx/fishpicsbackend/
        │   │   │   ├── common/     # 公共组件（异常、响应、配置）
        │   │   │   ├── controller/ # 控制器
        │   │   │   ├── dto/        # 数据传输对象
        │   │   │   ├── entity/     # 实体类
        │   │   │   ├── enums/      # 枚举类
        │   │   │   ├── mapper/     # 数据访问层
        │   │   │   ├── service/    # 业务逻辑层
        │   │   │   └── vo/         # 视图对象
        │   │   └── resources/
        │   │       ├── mapper/     # MyBatis XML映射
        │   │       └── application.yml
        │   ├── sql/
        │   │   └── create.sql      # 数据库创建脚本
        │   └── test/               # 测试代码
        └── pom.xml
```

---

## 快速启动

### 环境要求
- Java 11+
- MySQL 8+
- Redis 5.0+
- Node.js 18+

### 后端启动
1. 创建 MySQL 数据库，执行 `src/sql/create.sql` 脚本
2. 配置 `src/AI.Image.Material.Collaboration.Platform/src/main/resources/application.yml` 中的数据库和 Redis 连接信息
3. 启动 SpringBoot 应用，访问 `http://localhost:8080/api` 查看API文档

### 前端启动
```bash
cd src/FishPic-frontend
npm install
npm run dev
```

### 访问应用
- 前端应用：`http://localhost:5173`
- 后端API：`http://localhost:8080/api`
- API文档：`http://localhost:8080/api/doc.html`

---

## 效果预览
- 首页：登录/注册界面，带图形验证码
- 用户管理：管理员后台，支持查询、编辑、封禁/解封
- 个人中心：我的发布、收藏、管理
- API文档：Knife4j 自动生成的接口文档

---

## 技术亮点
- **前后端分离架构**，职责清晰，易于维护
- **图形验证码机制**，防止暴力破解
- **Redis 缓存验证码**，提高验证效率
- **统一异常处理**，全局错误捕获和响应
- **分页查询优化**，大数据量处理高效
- **暗色模式支持**，提升用户体验
- **响应式设计**，适配PC和移动端

---