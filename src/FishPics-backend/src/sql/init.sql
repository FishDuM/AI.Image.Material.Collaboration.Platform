-- 创建数据库
CREATE DATABASE IF NOT EXISTS FishPics DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE FishPics;

-- 1. 系统配置表
CREATE TABLE pic_system (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    syskey   VARCHAR(256)  NOT NULL COMMENT '配置键',
    sysvalue VARCHAR(1024) NULL COMMENT '配置值',
    UNIQUE KEY uk_syskey (syskey)
) COMMENT '系统配置表';

INSERT INTO pic_system (syskey, sysvalue) VALUES
('type_list_key', '["人物","动物","植物","美食","风景","建筑","物品","服饰","数码","家居","插画","二次元","实拍","文档","表情包"]');

-- 2. 用户表
CREATE TABLE user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(32)                        NULL COMMENT '用户名（登录用）',
    password    VARCHAR(128)                       NULL COMMENT '密码（BCrypt 哈希）',
    avatar      VARCHAR(256)                       NULL COMMENT '头像URL',
    email       VARCHAR(64)                        NULL COMMENT '邮箱',
    phone       VARCHAR(16)                        NULL COMMENT '手机号',
    nickname    VARCHAR(32)                        NULL COMMENT '昵称（展示用）',
    level       TINYINT  DEFAULT 0                 NOT NULL COMMENT '用户等级: 0=普通 1=VIP 2=SVIP',
    role        TINYINT  DEFAULT 0                 NOT NULL COMMENT '用户角色: 0=普通 1=管理员',
    status      TINYINT  DEFAULT 1                 NULL COMMENT '状态 1=正常 0=禁用',
    is_delete   TINYINT  DEFAULT 0                 NOT NULL COMMENT '逻辑删除 0=否 1=是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_nickname (nickname)
) COMMENT '用户表';

INSERT INTO user (username, password, nickname, level, role, status) VALUES
('admin', '$2b$10$6owdZSQbVSKuiA4BL7tC/Oii2g4hlrs3U88e.FX41NK1s/kQeERge', '系统管理员', 0, 1, 1);

-- 3. 空间表
CREATE TABLE space (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '空间ID',
    name         VARCHAR(256)              NOT NULL COMMENT '空间名称',
    introduction VARCHAR(256)              NULL COMMENT '空间介绍',
    type         TINYINT  DEFAULT 0        NOT NULL COMMENT '类型 0=私人空间 1=团队空间',
    user_id      BIGINT                    NULL COMMENT '创建者用户ID',
    storage_size BIGINT  DEFAULT 536870912 NOT NULL COMMENT '存储配额(Byte)，默认512MB',
    size         BIGINT  DEFAULT 0         NULL COMMENT '已用大小(Byte)',
    level        TINYINT DEFAULT 0         NOT NULL COMMENT '空间等级 0=普通 1=VIP 2=SVIP',
    status       TINYINT DEFAULT 1         NOT NULL COMMENT '状态 0=禁用 1=正常',
    version      BIGINT  DEFAULT 1         NOT NULL COMMENT '乐观锁版本号',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type (type),
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_type (user_id, type)
) COMMENT '空间表';

-- 4. 图片表
CREATE TABLE picture (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图片ID',
    user_id      BIGINT                             NOT NULL COMMENT '上传者用户ID',
    picture_name VARCHAR(256)                       NULL COMMENT '图片名称',
    url          VARCHAR(512)                       NOT NULL COMMENT '图片URL',
    width        INT UNSIGNED                       NULL COMMENT '宽度(像素)',
    height       INT UNSIGNED                       NULL COMMENT '高度(像素)',
    size         BIGINT                             NULL COMMENT '文件大小(Byte)',
    status       TINYINT  DEFAULT 2                 NULL COMMENT '状态 1=正常 0=禁用 2=待审核',
    is_private   TINYINT  DEFAULT 1                 NOT NULL COMMENT '0=公开 1=私有',
    space_id     BIGINT                             NULL COMMENT '所属空间ID',
    resource_id  BIGINT                             NULL COMMENT '关联file_resource.id（文件去重）',
    introduction VARCHAR(256)                       NULL COMMENT '图片介绍',
    type         VARCHAR(32)                        NULL COMMENT '图片格式',
    is_selected  TINYINT  DEFAULT 0                 NOT NULL COMMENT '是否精选 0=普通 1=精选',
    version      BIGINT   DEFAULT 1                 NOT NULL COMMENT '乐观锁版本号',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_space_id (space_id),
    INDEX idx_picture_name (picture_name),
    INDEX idx_introduction (introduction),
    INDEX idx_status (status),
    INDEX idx_update_time (update_time),
    UNIQUE KEY uk_resource_user_space (resource_id, user_id, space_id)
) COMMENT '图片表';

-- 5. 图片标签关联表
CREATE TABLE picture_tag (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    picture_id  BIGINT      NOT NULL COMMENT '图片ID',
    tag_name    VARCHAR(32) NOT NULL COMMENT '标签名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_picture_tag (picture_id, tag_name),
    INDEX idx_picture_id (picture_id),
    INDEX idx_tag_name (tag_name)
) COMMENT '图片标签关联表';

-- 6. 异步任务表
CREATE TABLE task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_id     VARCHAR(32)                        NOT NULL COMMENT '任务唯一标识(UUID)',
    user_id     BIGINT                             NOT NULL COMMENT '发起者用户ID',
    biz_type    VARCHAR(32)                        NOT NULL COMMENT '业务类型: ai_tag / ai_draw',
    biz_id      VARCHAR(64)                        NULL COMMENT '业务关联ID',
    status      VARCHAR(20) DEFAULT 'PENDING'      NOT NULL COMMENT '状态: PENDING/PROCESSING/DONE/FAILED',
    retry_count INT          DEFAULT 0              NOT NULL COMMENT '已重试次数',
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

-- 7. 审计日志表
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

-- 8. 团队空间成员表
CREATE TABLE space_team_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    space_id    BIGINT  NOT NULL COMMENT '空间ID',
    user_id     BIGINT  NOT NULL COMMENT '用户ID',
    role_id     INT     DEFAULT 2 NOT NULL COMMENT '角色: 1=所有者 2=成员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_space_user (space_id, user_id),
    INDEX idx_space_id (space_id),
    INDEX idx_user_id (user_id)
) COMMENT '团队空间成员表';

-- 9. 物理文件去重表
CREATE TABLE file_resource (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    md5         VARCHAR(32)  NOT NULL COMMENT '文件MD5',
    size        BIGINT       NOT NULL COMMENT '文件大小(Byte)',
    cos_key     VARCHAR(512) NOT NULL COMMENT 'COS存储路径',
    ref_count   INT DEFAULT 1 NOT NULL COMMENT '引用计数',
    version     BIGINT DEFAULT 1 NOT NULL COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_md5_size (md5, size),
    CHECK (ref_count >= 0)
) COMMENT '物理文件去重表';

-- 10. 图片分享表
CREATE TABLE picture_share (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    picture_id        BIGINT      NOT NULL COMMENT '图片ID',
    share_user_id     BIGINT      NOT NULL COMMENT '分享者用户ID',
    share_token       VARCHAR(64) NOT NULL COMMENT '分享链接Token(明文,创建时返回一次)',
    share_token_hash  VARCHAR(64)          NULL COMMENT 'Token SHA-256 哈希',
    expire_time       DATETIME    NOT NULL COMMENT '过期时间',
    allow_download    TINYINT DEFAULT 0 NOT NULL COMMENT '是否允许下载 0=仅预览 1=允许下载',
    status            TINYINT DEFAULT 1 NOT NULL COMMENT '状态 1=有效 0=已取消',
    max_view_count    INT DEFAULT 0 NULL COMMENT '最大访问次数(0=不限)',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_share_token (share_token),
    INDEX idx_share_token_hash (share_token_hash),
    INDEX idx_picture_id (picture_id),
    INDEX idx_share_user_id (share_user_id),
    INDEX idx_expire_time (expire_time)
) COMMENT '图片分享表';

-- 11. 分享图片关联表（支持多图分享）
CREATE TABLE picture_share_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    share_id    BIGINT   NOT NULL COMMENT '分享ID',
    picture_id  BIGINT   NOT NULL COMMENT '图片ID',
    sort_order  INT      NOT NULL DEFAULT 0 COMMENT '排序',
    KEY idx_share_id (share_id),
    KEY idx_picture_id (picture_id)
) COMMENT '分享图片关联表';
