create database if not exists FishPics;

use FishPics;

-- 创建用户表
create table if not exists `user` (
                                      `id` bigint unsigned not null auto_increment comment '用户ID',
                                      `username` varchar(32) null comment '用户名（登录用）',
                                      `password` varchar(128) null comment '密码',
                                      `avatar` varchar(256) default null comment '头像URL',
                                      `email` varchar(64) default null comment '邮箱',
                                      `phone` varchar(16) default null comment '手机号',
                                      `nickname` varchar(32) default null comment '昵称（展示用）',
                                      `status` tinyint null default 1 comment '状态 1-正常 0-禁用 2-待审核',
                                      `delete` tinyint null default 0 comment '0-逻辑未删除, 1-逻辑删除',
                                      `role` varchar(32) null default 'user' comment '用户的权限',
                                      `create_time` datetime not null default current_timestamp comment '创建时间',
                                      `update_time` datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                                      primary key (`id`),
                                      unique key `uk_username` (`username`) using btree,
                                      unique key `uk_nickname` (`nickname`) using btree
) engine = innodb default charset = utf8mb4 comment = '用户表';

-- 创建图片表
create table `picture`(
                          `id` bigint not null auto_increment comment '主键',
                          `user_id` bigint not null comment '用户id', -- 关联用户表
                          `picture_name` bigint not null comment '图片名称',
                          `url` varchar(512) not null comment '图片地址',
                          `width` varchar(32) null comment '宽度',
                          `height` varchar(32) null comment '高度',
                          `size` varchar(32) null comment '大小',
                          `status` tinyint null default 1 comment '状态 1-正常 0-禁用 2-待审核',
                          `create_time` datetime not null default current_timestamp comment '创建时间',
                          `update_time` datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                          primary key (`id`),
                          key `idx_user_id` (`user_id`) using btree,
                          key `idx_picture_name` (`picture_name`) using btree
) engine = innodb default charset = utf8mb4 comment = '图片表';

-- 创建帖子表
create table `post`(
                       `id` bigint not null auto_increment comment '主键',
                       `user_id` bigint not null comment '关联用户表',
                       `title` varchar(256) not null comment '标题',
                       `content` text not null comment '内容',
                       `picture_ids` varchar(512) null comment '图片id数组',
                       `status` tinyint null default 1 comment '状态 1-正常 0-禁用 2-待审核',
                       `create_time` datetime not null default current_timestamp comment '创建时间',
                       `update_time` datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                       primary key (`id`),
                       key `idx_user_id` (`user_id`) using btree,
                       key `idx_title` (`title`) using btree
) engine = innodb default charset = utf8mb4 comment = '帖子表';

-- 创建评论表
create table `comment`(
                          `id` bigint not null auto_increment comment '主键',
                          `user_id` bigint not null comment '关联用户表',
                          `post_id` bigint not null comment '关联帖子表',
                          `content` text not null comment '评论内容',
                          `status` tinyint null default 1 comment '状态 1-正常 0-禁用 2-待审核',
                          `create_time` datetime not null default current_timestamp comment '创建时间',
                          `update_time` datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                          primary key (`id`),
                          key `idx_user_id` (`user_id`) using btree,
                          key `idx_post_id` (`post_id`) using btree
) engine = innodb default charset = utf8mb4 comment = '评论表';