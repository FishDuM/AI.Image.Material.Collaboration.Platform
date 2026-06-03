create database FishPics;

use FishPics;

create table comment
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint                             not null comment '关联用户表',
    post_id     bigint                             not null comment '关联帖子表',
    content     text                               not null comment '评论内容',
    parent_id   bigint                             null comment '父评论（支持二级评论 / 回复）',
    to_user_id  bigint                             null comment '回复给谁',
    status      tinyint  default 1                 null comment '状态 1-正常 0-禁用 2-待审核',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '评论表';

create index idx_post_id
    on comment (post_id);

create index idx_user_id
    on comment (user_id);

create table pic_system
(
    syskey   varchar(256)  not null,
    sysvalue varchar(1024) null,
    id       bigint auto_increment comment 'id'
        primary key,
    constraint pic_system_pk
        unique (syskey)
)
    comment '系统表';

INSERT INTO pic_system (syskey, sysvalue)
VALUES ('type_list_key',
        '["人物","动物","植物","美食","风景","建筑","物品","服饰","数码","家居","插画","二次元","实拍","文档","表情包"]');

create table picture
(
    id           bigint auto_increment comment '主键'
        primary key,
    user_id      bigint                             not null comment '用户id',
    picture_name varchar(256)                       null comment '图片名称',
    url          varchar(512)                       not null comment '图片地址',
    width        varchar(32)                        null comment '宽度',
    height       varchar(32)                        null comment '高度',
    size         bigint                             null comment '大小',
    status       tinyint  default 2                 null comment '状态 1-正常 0-禁用 2-待审核',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_private   tinyint  default 0                 not null comment '0-不公开到首页，1-公开到首页',
    space_id     bigint                             null comment '空间Id',
    introduction varchar(256)                       null comment '图片介绍',
    tags varchar(512) null comment '标签 (Json)'
)
    comment '图片表';

create index idx_picture_name
    on picture (picture_name);

create index idx_user_id
    on picture (user_id);

create index picture_introduction_index
    on picture (introduction);

create index picture_space_id_index
    on picture (space_id);

create index picture_update_time_index
    on picture (update_time);

create table picture_child
(
    id         bigint auto_increment comment '主键'
        primary key,
    picture_id bigint null comment '关联图片id',
    post_id    bigint null comment '关联帖子id',
    sort_num   int    null comment '在帖子中的顺序',
    constraint picture_child_picture_id_post_id_uindex
        unique (picture_id, post_id)
)
    comment '子图片表';

create index picture_child_picture_id_index
    on picture_child (picture_id);

create index picture_child_post_id_index
    on picture_child (post_id);

create table post
(
    id           bigint auto_increment comment '主键'
        primary key,
    user_id      bigint                             not null comment '关联发帖用户',
    title        varchar(256)                       not null comment '标题',
    content      text                               not null comment '内容',
    status       tinyint  default 1                 not null comment '状态 1-正常 0-禁用 2-待审核',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete    int      default 0                 not null,
    likes_num    bigint   default 0                 not null comment '点赞数',
    collects_num bigint   default 0                 not null comment '收藏数',
    comment_num  int      default 0                 not null comment '评论数',
    is_private   tinyint  default 0                 not null comment '0-公开，1-仅自己可见，',
    cover        bigint                             null comment '封面图片的id（主图）',
    views_num    bigint   default 0                 not null comment '查看数',
    hot          int      default 0                 null comment '热度值'
)
    comment '帖子表';

create index idx_title
    on post (title);

create index idx_user_id
    on post (user_id);

create index post_content_index
    on post (content(100));

create index post_status_index
    on post (status);

create table space
(
    id            bigint auto_increment comment '空间id'
        primary key,
    introduction  varchar(256)              null comment '空间介绍',
    type          tinyint                   null comment '0-私人空间，1-团队空间',
    user_id       bigint                    null comment '创建的用户Id',
    storage_size  bigint  default 536870912 not null comment '空间存储大小(Byte)：512MB-5G-10G-30G-50G',
    level         tinyint default 0         not null comment '空间级别：普通-VIP-SVIP',
    name          varchar(246)              not null comment '空间名',
    size          bigint                    null comment '现在使用大小',
    status        tinyint default 1         not null comment '0=禁用, 1=正常'
)
    comment '空间表';

create index space_type_index
    on space (type);

create index space_user_id_index
    on space (user_id);

create table user
(
    id                      bigint auto_increment comment '用户ID'
        primary key,
    username                varchar(32)                           null comment '用户名（登录用）',
    password                varchar(128)                          null comment '密码',
    avatar                  varchar(256)                          null comment '头像URL',
    email                   varchar(64)                           null comment '邮箱',
    phone                   varchar(16)                           null comment '手机号',
    nickname                varchar(32)                           null comment '昵称（展示用）',
    status                  tinyint     default 1                 null comment '状态 1-正常 0-禁用 2-待审核',
    is_delete               tinyint     default 0                 not null comment '0-逻辑未删除, 1-逻辑删除',
    create_time             datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time             datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    like_num                bigint                                null,
    collect_num             bigint                                null,
    is_private_follows      tinyint     default 0                 not null comment '0-公开关注列表，1-不公开关注列表',
    is_private_post_collect tinyint     default 0                 not null comment '0-公开帖子列表，1-不公开帖子列表',
    is_private_likes        tinyint     default 0                 not null comment '0-公开点赞帖子列表，1-不公开点赞帖子列表',
    is_private_fans         tinyint     default 0                 not null comment '0-公开粉丝列表，1-不公开粉丝列表',
    level                   tinyint     default 0                 not null comment '0-普通，1-VIP，2-SVIP',
    constraint uk_nickname
        unique (nickname),
    constraint uk_username
        unique (username)
)
    comment '用户表';

create table user_fans
(
    id      bigint not null
        primary key,
    user_id bigint not null,
    fan_id  bigint not null
)
    comment '用户粉丝表';

create index user_fans_user_id_fan_id_index
    on user_fans (user_id, fan_id);

create table user_post_collect
(
    id          bigint                                 not null
        primary key,
    user_id     bigint                                 not null,
    post_id     bigint                                 not null,
    create_time datetime default CURRENT_TIMESTAMP     null comment '创建时间'
)
    comment '用户帖子收藏表';

create index user_post_collect_user_id_index
    on user_post_collect (user_id);

create table user_post_likes
(
    id          bigint                                 not null
        primary key,
    user_id     bigint                                 not null,
    post_id     bigint                                 not null,
    create_time datetime default CURRENT_TIMESTAMP     null comment '创建时间'
)
    comment '用户点赞帖子表';

create index user_post_likes_user_id_index
    on user_post_likes (user_id);

create table task
(
    id          bigint auto_increment comment '主键'
        primary key,
    task_id     varchar(32)                        not null comment '任务唯一标识(UUID)',
    user_id     bigint                             not null comment '发起任务的用户id',
    biz_type    varchar(32)                        not null comment '业务类型: ai_tag / ai_draw / notify / export ...',
    biz_id      varchar(64)                        null comment '业务关联id',
    status      varchar(20) default 'PENDING'      not null comment '状态: PENDING / PROCESSING / DONE / FAILED',
    param       text                               null comment '任务参数JSON',
    result      text                               null comment '任务结果JSON',
    error_msg   text                               null comment '错误信息',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '异步任务表';

create index idx_task_id
    on task (task_id);

create index idx_user_id
    on task (user_id);

create index idx_biz_type
    on task (biz_type);

create index idx_status
    on task (status);

create table user_interest_profile
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    tag         varchar(64)                        not null comment '标签',
    weight      int      default 0                 null comment '兴趣权重',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_tag
        unique (user_id, tag)
)
    comment '用户兴趣画像表';

-- ============================================================
-- 权限体系 (RBAC + ABAC 三级权限)
-- ============================================================

-- 1. 权限点表：定义系统中所有可授予的权限
create table sys_permission
(
    id          bigint auto_increment comment '主键'
        primary key,
    code        varchar(128)                       not null comment '权限码, 如 post:review',
    name        varchar(64)                        not null comment '权限名称, 如 帖子审核',
    module      varchar(32)                        not null comment '所属模块: post/user/space/comment/picture/ai/system',
    scope       tinyint  default 0                 not null comment '0=系统级 1=团队级 2=资源级',
    sort_order  int      default 0                 null comment '排序',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete   tinyint  default 0                 not null comment '0=未删除 1=已删除',
    constraint uk_permission_code
        unique (code)
)
    comment '权限点表';

create index idx_permission_module
    on sys_permission (module);

create index idx_permission_scope
    on sys_permission (scope);

-- 2. 角色表：系统级角色和团队级角色共用
create table sys_role
(
    id               bigint auto_increment comment '主键'
        primary key,
    code             varchar(64)                        not null comment '角色编码, 如 super_admin, team_admin',
    name             varchar(64)                        not null comment '角色显示名称',
    scope            tinyint  default 0                 not null comment '0=系统级 1=团队级',
    is_system        tinyint  default 0                 not null comment '系统预置角色不可删除 0=否 1=是',
    inherit_role_id  bigint                             null comment '继承的角色ID（角色权限合并）',
    create_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete        tinyint  default 0                 not null comment '0=未删除 1=已删除',
    constraint uk_role_code
        unique (code)
)
    comment '角色表';

-- 3. 角色-权限关联表
create table sys_role_permission
(
    id            bigint auto_increment comment '主键'
        primary key,
    role_id       bigint not null comment '角色ID',
    permission_id bigint not null comment '权限ID',
    constraint uk_role_permission
        unique (role_id, permission_id)
)
    comment '角色-权限关联表';

-- 4. 用户-系统角色关联表（用户可拥有多个系统级角色，权限取并集）
create table sys_user_role
(
    id      bigint auto_increment comment '主键'
        primary key,
    user_id bigint not null comment '用户ID',
    role_id bigint not null comment '角色ID',
    constraint uk_user_role
        unique (user_id, role_id)
)
    comment '用户-系统角色关联表';

create index idx_user_role_user_id
    on sys_user_role (user_id);

-- 5. 团队空间成员表（替代 space.team_users_id）
create table space_team_member
(
    id        bigint auto_increment comment '主键'
        primary key,
    space_id  bigint                             not null comment '团队空间ID',
    user_id   bigint                             not null comment '用户ID',
    role_id   bigint                             not null comment '团队内角色ID, 关联 sys_role',
    joined_at datetime default CURRENT_TIMESTAMP null comment '加入时间',
    constraint uk_space_team_member
        unique (space_id, user_id)
)
    comment '团队空间成员表';

create index idx_space_team_member_space_id
    on space_team_member (space_id);

create index idx_space_team_member_user_id
    on space_team_member (user_id);

-- ============================================================
-- 初始权限数据
-- ============================================================

-- 系统级权限点
INSERT INTO sys_permission (code, name, module, scope, sort_order) VALUES
-- 用户管理
('user:list',      '用户列表查看',   'user',    0, 1),
('user:manage',    '用户信息编辑',   'user',    0, 2),
('user:status',    '用户状态变更',   'user',    0, 3),
('user:role',      '用户角色分配',   'user',    0, 4),
-- 帖子管理
('post:list',      '帖子管理列表',   'post',    0, 5),
('post:review',    '帖子审核',       'post',    0, 6),
('post:delete',    '帖子删除',       'post',    0, 7),
-- 图片管理
('picture:list',   '图片管理列表',   'picture', 0, 8),
('picture:review', '图片审核',       'picture', 0, 9),
-- 评论管理
('comment:list',   '评论管理列表',   'comment', 0, 10),
('comment:review', '评论审核',        'comment', 0, 11),
('comment:delete', '评论删除',        'comment', 0, 12),
-- 空间管理
('space:list',     '空间管理列表',   'space',   0, 13),
('space:manage',   '空间编辑删除',   'space',   0, 14),
('space:status',   '空间状态变更',   'space',   0, 15),
-- AI 管理
('ai:tasks',       'AI任务查看',     'ai',      0, 16),
('ai:stats',       'AI统计查看',     'ai',      0, 17),
('ai:config',      'AI配置管理',     'ai',      0, 18),
-- 系统配置
('system:type',    '帖子标签管理',    'system',  0, 19),
('system:marquee', '轮播图管理',      'system',  0, 20);

-- 团队级权限点
INSERT INTO sys_permission (code, name, module, scope, sort_order) VALUES
('team:member_manage', '团队成员管理', 'space', 1, 21),
('team:space_edit',    '空间信息编辑', 'space', 1, 22),
('team:upload',        '空间内上传',   'space', 1, 23),
('team:delete',        '空间内删除',   'space', 1, 24);

-- 资源级权限点
INSERT INTO sys_permission (code, name, module, scope, sort_order) VALUES
('resource:edit',   '编辑他人资源', 'resource', 2, 25),
('resource:delete', '删除他人资源', 'resource', 2, 26);

-- ============================================================
-- 初始角色数据
-- ============================================================

INSERT INTO sys_role (code, name, scope, is_system) VALUES
('super_admin', '超级管理员', 0, 1),
('admin',       '管理员',     0, 1),
('reviewer',    '内容审核员',  0, 1),
('editor',      '内容编辑',    0, 1),
('analyst',     '数据分析员',  0, 1);

INSERT INTO sys_role (code, name, scope, is_system) VALUES
('team_admin',  '团队管理员',  1, 1),
('team_member', '团队成员',    1, 1),
('team_viewer', '团队访客',    1, 1);

-- ============================================================
-- 角色-权限关联
-- ============================================================

-- super_admin: 所有系统级权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'super_admin' AND p.scope = 0;

-- admin: 所有系统级权限（与 super_admin 相同，但后续可被移除）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'admin' AND p.scope = 0;

-- reviewer: 内容审核相关
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'reviewer' AND p.code IN (
    'post:review', 'picture:review', 'comment:review',
    'post:list', 'picture:list', 'comment:list'
);

-- editor: 内容编删
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'editor' AND p.code IN (
    'post:list', 'comment:list', 'comment:delete', 'post:delete',
    'picture:list', 'picture:review'
);

-- analyst: 只读统计
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'analyst' AND p.code IN ('ai:tasks', 'ai:stats');

-- 团队角色权限
-- team_admin: 团队管理权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'team_admin' AND p.code IN (
    'team:member_manage', 'team:space_edit', 'team:upload', 'team:delete'
);

-- team_member: 上传和查看
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.code = 'team_member' AND p.code IN ('team:upload');

-- team_viewer: 无操作权限（纯查看，由代码逻辑控制）
-- 不需要关联任何权限点

