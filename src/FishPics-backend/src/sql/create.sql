create database if not exists FishPics;

use FishPics;

create table comment
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint                             not null comment '关联用户表',
    post_id     bigint                             not null comment '关联帖子表',
    content     text                               not null comment '评论内容',
    status      tinyint  default 1                 null comment '状态 1-正常 0-禁用 2-待审核',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '评论表';

create index idx_post_id
    on comment (post_id);

create index idx_user_id
    on comment (user_id);

create table picture
(
    id           bigint auto_increment comment '主键'
        primary key,
    user_id      bigint                             not null comment '用户id',
    picture_name bigint                             not null comment '图片名称',
    url          varchar(512)                       not null comment '图片地址',
    width        varchar(32)                        null comment '宽度',
    height       varchar(32)                        null comment '高度',
    size         varchar(32)                        null comment '大小',
    status       tinyint  default 1                 null comment '状态 1-正常 0-禁用 2-待审核',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '图片表';

create index idx_picture_name
    on picture (picture_name);

create index idx_user_id
    on picture (user_id);

create table post
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint                             not null comment '关联用户表',
    title       varchar(256)                       not null comment '标题',
    content     text                               not null comment '内容',
    picture_ids varchar(512)                       null comment '图片id数组',
    status      tinyint  default 1                 null comment '状态 1-正常 0-禁用 2-待审核',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `delete`    int                                null
)
    comment '帖子表';

create index idx_title
    on post (title);

create index idx_user_id
    on post (user_id);

create table user
(
    id          bigint unsigned auto_increment comment '用户ID'
        primary key,
    username    varchar(32)                           null comment '用户名（登录用）',
    password    varchar(128)                          null comment '密码',
    avatar      varchar(256)                          null comment '头像URL',
    email       varchar(64)                           null comment '邮箱',
    phone       varchar(16)                           null comment '手机号',
    nickname    varchar(32)                           null comment '昵称（展示用）',
    status      tinyint     default 1                 null comment '状态 1-正常 0-禁用 2-待审核',
    `delete`    tinyint     default 0                 null comment '0-逻辑未删除, 1-逻辑删除',
    role        varchar(32) default 'user'            null comment '用户的权限',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_nickname
        unique (nickname),
    constraint uk_username
        unique (username)
)
    comment '用户表';

