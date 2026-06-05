-- 创建数据库
CREATE DATABASE IF NOT EXISTS FishPics DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE FishPics;

-- 第一部分：业务表

-- 1. 系统配置表
CREATE TABLE pic_system (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    syskey   VARCHAR(256)  NOT NULL COMMENT '配置键',
    sysvalue VARCHAR(1024) NULL COMMENT '配置值',
    UNIQUE KEY uk_syskey (syskey)
) COMMENT '系统配置表';

-- 插入默认分类标签
INSERT INTO pic_system (syskey, sysvalue) VALUES
('type_list_key', '["人物","动物","植物","美食","风景","建筑","物品","服饰","数码","家居","插画","二次元","实拍","文档","表情包"]');

-- 2. 用户表
CREATE TABLE user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(32)                        NULL COMMENT '用户名（登录用）',
    password    VARCHAR(128)                       NULL COMMENT '密码（MD5+盐）',
    avatar      VARCHAR(256)                       NULL COMMENT '头像URL',
    email       VARCHAR(64)                        NULL COMMENT '邮箱',
    phone       VARCHAR(16)                        NULL COMMENT '手机号',
    nickname    VARCHAR(32)                        NULL COMMENT '昵称（展示用）',
    level       TINYINT  DEFAULT 0                 NOT NULL COMMENT '用户等级 0=普通 1=VIP 2=SVIP',
    status      TINYINT  DEFAULT 1                 NULL COMMENT '状态 1=正常 0=禁用',
    is_delete   TINYINT  DEFAULT 0                 NOT NULL COMMENT '逻辑删除 0=否 1=是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_nickname (nickname)
) COMMENT '用户表';

-- 3. 空间表
CREATE TABLE space (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '空间ID',
    name         VARCHAR(246)              NOT NULL COMMENT '空间名称',
    introduction VARCHAR(256)              NULL COMMENT '空间介绍',
    type         TINYINT                   NULL COMMENT '类型 0=私人空间 1=团队空间',
    user_id      BIGINT                    NULL COMMENT '创建者用户ID',
    storage_size BIGINT  DEFAULT 536870912 NOT NULL COMMENT '存储配额(Byte)，默认512MB',
    size         BIGINT  DEFAULT 0         NULL COMMENT '已用大小(Byte)',
    level        TINYINT DEFAULT 0         NOT NULL COMMENT '空间等级 0=普通 1=VIP 2=SVIP',
    status       TINYINT DEFAULT 1         NOT NULL COMMENT '状态 0=禁用 1=正常',
    INDEX idx_type (type),
    INDEX idx_user_id (user_id)
) COMMENT '空间表';

-- 4. 图片表
CREATE TABLE picture (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图片ID',
    user_id      BIGINT                             NOT NULL COMMENT '上传者用户ID',
    picture_name VARCHAR(256)                       NULL COMMENT '图片名称',
    url          VARCHAR(512)                       NOT NULL COMMENT '图片URL',
    width        VARCHAR(32)                        NULL COMMENT '宽度',
    height       VARCHAR(32)                        NULL COMMENT '高度',
    size         BIGINT                             NULL COMMENT '文件大小(Byte)',
    status       TINYINT  DEFAULT 2                 NULL COMMENT '状态 1=正常 0=禁用 2=待审核',
    is_private   TINYINT  DEFAULT 0                 NOT NULL COMMENT '是否公开 0=不公开 1=公开',
    space_id     BIGINT                             NULL COMMENT '所属空间ID',
    resource_id  BIGINT                             NULL COMMENT '关联file_resource.id（文件去重）',
    introduction VARCHAR(256)                       NULL COMMENT '图片介绍',
    tags         VARCHAR(512)                       NULL COMMENT '标签(JSON数组)',
    type         VARCHAR(32)                        NULL COMMENT '图片格式',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_space_id (space_id),
    INDEX idx_picture_name (picture_name),
    INDEX idx_introduction (introduction),
    INDEX idx_update_time (update_time)
) COMMENT '图片表';

-- 5. 异步任务表
CREATE TABLE task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_id     VARCHAR(32)                        NOT NULL COMMENT '任务唯一标识(UUID)',
    user_id     BIGINT                             NOT NULL COMMENT '发起者用户ID',
    biz_type    VARCHAR(32)                        NOT NULL COMMENT '业务类型: ai_tag / ai_draw',
    biz_id      VARCHAR(64)                        NULL COMMENT '业务关联ID',
    status      VARCHAR(20) DEFAULT 'PENDING'      NOT NULL COMMENT '状态: PENDING/PROCESSING/DONE/FAILED',
    param       TEXT                               NULL COMMENT '任务参数(JSON)',
    result      TEXT                               NULL COMMENT '任务结果(JSON)',
    error_msg   TEXT                               NULL COMMENT '错误信息',
    create_time DATETIME   DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME   DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_user_id (user_id),
    INDEX idx_biz_type (biz_type),
    INDEX idx_status (status)
) COMMENT '异步任务表';

-- 6. 审计日志表
CREATE TABLE sys_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id     BIGINT       COMMENT '操作者用户ID',
    username    VARCHAR(50)  COMMENT '操作者用户名',
    operation   VARCHAR(50)  COMMENT '操作类型',
    module      VARCHAR(50)  COMMENT '操作模块',
    detail      VARCHAR(500) COMMENT '操作详情',
    method      VARCHAR(10)  COMMENT '请求方法',
    url         VARCHAR(200) COMMENT '请求URL',
    params      TEXT         COMMENT '请求参数',
    result      TINYINT DEFAULT 1 COMMENT '操作结果 0=失败 1=成功',
    error_msg   TEXT COMMENT '错误信息',
    ip          VARCHAR(50)  COMMENT 'IP地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    is_delete   TINYINT DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_operation (operation)
) COMMENT '审计日志表';

-- 第二部分：RBAC 三层权限体系

-- 7. 角色表（4个固定角色）
CREATE TABLE role (
    id          INT PRIMARY KEY COMMENT '角色ID: 1=超管 2=团队管理员 3=普通成员 4=只读',
    role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(200) NULL COMMENT '角色描述'
) COMMENT '角色表';

-- 8. 权限表（16个权限，三层分布）
CREATE TABLE permission (
    id        INT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    perm_key  VARCHAR(50)  NOT NULL COMMENT '权限标识，如 system:config',
    perm_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    layer     VARCHAR(20)  NOT NULL COMMENT '所属层级: system/space/resource',
    UNIQUE KEY uk_perm_key (perm_key)
) COMMENT '权限表';

-- 9. 角色-权限绑定表
CREATE TABLE role_permission (
    role_id INT NOT NULL COMMENT '角色ID',
    perm_id INT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, perm_id)
) COMMENT '角色-权限绑定表';

-- 10. 用户-系统角色表（仅超管需要登记）
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id INT    NOT NULL COMMENT '角色ID（仅允许1=超管）',
    PRIMARY KEY (user_id, role_id)
) COMMENT '用户-系统角色表';

-- 11. 团队空间成员表
CREATE TABLE space_team_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    space_id    BIGINT   NOT NULL COMMENT '空间ID',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    role_id     INT      NOT NULL COMMENT '角色ID（仅允许2/3/4）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_space_user (space_id, user_id),
    INDEX idx_space_id (space_id),
    INDEX idx_user_id (user_id)
) COMMENT '团队空间成员表';

-- 12. 物理文件去重表
CREATE TABLE file_resource (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    md5         VARCHAR(32)  NOT NULL COMMENT '文件MD5',
    size        BIGINT       NOT NULL COMMENT '文件大小(Byte)',
    cos_key     VARCHAR(512) NOT NULL COMMENT 'COS存储路径',
    ref_count   INT DEFAULT 1 NOT NULL COMMENT '引用计数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_md5_size (md5, size)
) COMMENT '物理文件去重表';

-- 第三部分：初始数据

-- 4 个角色
INSERT INTO role (id, role_name, description) VALUES
(1, '系统超级管理员', '全平台最高权限，拥有全部 13 个权限'),
(2, '团队管理员',     '团队空间管理权限，拥有 space:* + resource:* 共 8 个权限'),
(3, '普通成员',       '团队内普通操作权限，拥有 resource:* 共 5 个权限'),
(4, '只读成员',       '仅可查看，拥有 resource:view 1 个权限');

-- 16 个权限（含 3 个 VIP 扩展权限）
INSERT INTO permission (id, perm_key, perm_name, layer) VALUES
-- 第一层：系统全局管理（5个）
(1,  'system:config',          '系统基础设置',       'system'),
(2,  'system:user:manage',     '全平台用户管理',     'system'),
(3,  'system:team:manage',     '全平台团队管理',     'system'),
(4,  'system:log:manage',      '系统审计日志管理',   'system'),
(5,  'system:ai:manage',       'AI 功能管理',        'system'),
-- 第二层：团队空间管理（3个）
(6,  'space:setting',          '空间设置',           'space'),
(7,  'space:member',           '管理成员',           'space'),
(8,  'space:recycle',          '管理回收站',         'space'),
-- 第三层：图片资源操作（5个基础 + 3个VIP扩展）
(9,  'resource:view',          '查看图片/文件夹',    'resource'),
(10, 'resource:upload',        '上传图片/ZIP 包',    'resource'),
(11, 'resource:edit',          '编辑图片及元信息',   'resource'),
(12, 'resource:delete',        '删除资源',           'resource'),
(13, 'resource:download',      '下载资源',           'resource'),
(14, 'resource:upload:large',  '大文件上传(>10MB)',  'resource'),
(15, 'resource:storage:expand','扩展存储配额',       'resource'),
(16, 'resource:ai:quota',      'AI 高级配额',        'resource');

-- 角色-权限绑定

-- 超管(1)：全部 16 个权限
INSERT INTO role_permission (role_id, perm_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
(1, 6), (1, 7), (1, 8),
(1, 9), (1, 10), (1, 11), (1, 12), (1, 13),
(1, 14), (1, 15), (1, 16);

-- 团队管理员(2)：space:* + resource:* = 8 个权限
INSERT INTO role_permission (role_id, perm_id) VALUES
(2, 6), (2, 7), (2, 8),
(2, 9), (2, 10), (2, 11), (2, 12), (2, 13);

-- 普通成员(3)：resource:* = 5 个权限
INSERT INTO role_permission (role_id, perm_id) VALUES
(3, 9), (3, 10), (3, 11), (3, 12), (3, 13);

-- 只读成员(4)：resource:view = 1 个权限
INSERT INTO role_permission (role_id, perm_id) VALUES
(4, 9);

-- 验证：查看角色-权限绑定矩阵
SELECT
    r.id AS role_id,
    r.role_name,
    COUNT(rp.perm_id) AS perm_count
FROM role r
LEFT JOIN role_permission rp ON r.id = rp.role_id
GROUP BY r.id, r.role_name
ORDER BY r.id;
