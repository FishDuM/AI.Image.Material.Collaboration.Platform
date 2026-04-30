CREATE DATABASE `FishPics`;

USE `FishPics`;

CREATE TABLE `comment` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                           `user_id` bigint NOT NULL COMMENT '关联用户表',
                           `post_id` bigint NOT NULL COMMENT '关联帖子表',
                           `content` text NOT NULL COMMENT '评论内容',
                           `parent_id` bigint DEFAULT NULL COMMENT '父评论（支持二级评论 / 回复）',
                           `to_user_id` int DEFAULT NULL COMMENT '回复给谁',
                           `status` tinyint DEFAULT '1' COMMENT '状态 1-正常 0-禁用 2-待审核',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`) USING BTREE,
                           KEY `idx_post_id` (`post_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论表';

CREATE TABLE `picture` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                           `user_id` bigint NOT NULL COMMENT '用户id',
                           `picture_name` bigint NOT NULL COMMENT '图片名称',
                           `url` varchar(512) NOT NULL COMMENT '图片地址',
                           `width` varchar(32) DEFAULT NULL COMMENT '宽度',
                           `height` varchar(32) DEFAULT NULL COMMENT '高度',
                           `size` varchar(32) DEFAULT NULL COMMENT '大小',
                           `status` tinyint DEFAULT '2' COMMENT '状态 1-正常 0-禁用 2-待审核',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `is_private` tinyint NOT NULL DEFAULT '0' COMMENT '0-不公开到首页，1-公开到首页',
                           `post_id` bigint DEFAULT NULL COMMENT '帖子id',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`) USING BTREE,
                           KEY `idx_picture_name` (`picture_name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片表';

CREATE TABLE `post` (
                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                        `user_id` bigint NOT NULL COMMENT '关联发帖用户',
                        `title` varchar(256) NOT NULL COMMENT '标题',
                        `content` text NOT NULL COMMENT '内容',
                        `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 1-正常 0-禁用 2-待审核',
                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `is_delete` int NOT NULL DEFAULT '0',
                        `likes_num` bigint NOT NULL DEFAULT '0' COMMENT '点赞数',
                        `collects_num` bigint NOT NULL DEFAULT '0' COMMENT '收藏数',
                        `comment_num` int NOT NULL DEFAULT '0' COMMENT '评论数',
                        `is_private` tinyint NOT NULL DEFAULT '0' COMMENT '0-公开，1-仅自己可见，',
                        `cover` bigint DEFAULT NULL COMMENT '封面图片的id',
                        `views_num` bigint NOT NULL DEFAULT '0' COMMENT '查看数',
                        PRIMARY KEY (`id`),
                        KEY `idx_user_id` (`user_id`) USING BTREE,
                        KEY `idx_title` (`title`) USING BTREE,
                        KEY `post_content_index` (`content`(100)),
                        KEY `post_status_index` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2049105134168674306 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子表';

CREATE TABLE `user` (
                        `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `username` varchar(32) DEFAULT NULL COMMENT '用户名（登录用）',
                        `password` varchar(128) DEFAULT NULL COMMENT '密码',
                        `avatar` varchar(256) DEFAULT NULL COMMENT '头像URL',
                        `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
                        `phone` varchar(16) DEFAULT NULL COMMENT '手机号',
                        `nickname` varchar(32) DEFAULT NULL COMMENT '昵称（展示用）',
                        `status` tinyint DEFAULT '1' COMMENT '状态 1-正常 0-禁用 2-待审核',
                        `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '0-逻辑未删除, 1-逻辑删除',
                        `role` varchar(32) DEFAULT 'user' COMMENT '用户的权限',
                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `like_num` bigint DEFAULT NULL,
                        `collect_num` bigint DEFAULT NULL,
                        `is_private_follows` tinyint NOT NULL DEFAULT '0' COMMENT '0-公开关注列表，1-不公开关注列表',
                        `is_private_post_collect` tinyint NOT NULL DEFAULT '0' COMMENT '0-公开帖子列表，1-不公开帖子列表',
                        `is_private_likes` tinyint NOT NULL DEFAULT '0' COMMENT '0-公开点赞帖子列表，1-不公开点赞帖子列表',
                        `is_private_fans` tinyint NOT NULL DEFAULT '0' COMMENT '0-公开粉丝列表，1-不公开粉丝列表',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`) USING BTREE,
                        UNIQUE KEY `uk_nickname` (`nickname`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2047500356888125443 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `user_fans` (
                             `id` bigint NOT NULL,
                             `user_id` bigint NOT NULL,
                             `fan_id` bigint NOT NULL,
                             PRIMARY KEY (`id`),
                             KEY `user_fans_user_id_fan_id_index` (`user_id`,`fan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户粉丝表';

CREATE TABLE `user_follows` (
                                `id` bigint NOT NULL,
                                `user_id` bigint DEFAULT NULL,
                                `be_followed_user_id` bigint DEFAULT NULL,
                                PRIMARY KEY (`id`),
                                KEY `likes_user_by_id_user_id_be_followed_user_id_index` (`user_id`,`be_followed_user_id`) COMMENT '关注关系'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注表';

CREATE TABLE `user_post_collect` (
                                     `id` bigint NOT NULL,
                                     `user_id` bigint NOT NULL,
                                     `post_id` bigint NOT NULL,
                                     PRIMARY KEY (`id`),
                                     KEY `user_post_collect_user_id_index` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户帖子收藏表';

CREATE TABLE `user_post_likes` (
                                   `id` bigint NOT NULL,
                                   `user_id` bigint NOT NULL,
                                   `post_id` bigint NOT NULL,
                                   PRIMARY KEY (`id`),
                                   KEY `user_post_likes_user_id_index` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户点赞帖子表';

