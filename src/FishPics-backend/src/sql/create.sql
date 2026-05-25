create database FishPics;

use FishPics;

create table ai_task
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint                             not null comment '提交用户id',
    type        tinyint                            not null comment '任务类型: 0=自动标注 1=图片编辑 2=图片生成 3=推荐',
    sub_type    varchar(64)                        null comment '子类型: background_removal, style_transfer, text2image',
    input_data  text                               null comment '输入参数JSON (图片URL、prompt等)',
    output_data text                               null comment '输出结果JSON (tags、新图片URL、推荐列表等)',
    status      tinyint  default 0                 not null comment '状态: 0=处理中 1=成功 2=失败',
    error_msg   varchar(512)                       null comment '失败原因',
    picture_id  bigint                             null comment '关联的图片ID (标注/编辑类任务)',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment 'AI任务表';

create index ai_task_picture_id_index
    on ai_task (picture_id);

create index ai_task_status_index
    on ai_task (status);

create index ai_task_type_index
    on ai_task (type);

create index ai_task_user_id_index
    on ai_task (user_id);

create table comment
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint                             not null comment '关联用户表',
    post_id     bigint                             not null comment '关联帖子表',
    content     text                               not null comment '评论内容',
    parent_id   bigint                             null comment '父评论（支持二级评论 / 回复）',
    to_user_id  int                                null comment '回复给谁',
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
    tags         varchar(512)                       null comment '标签 (逗号分隔)'
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

create index picture_tags_index
    on picture (tags);

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
    team_users_id varchar(1024)             null comment '团队空间的用户id',
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
    role                    varchar(32) default 'user'            null comment '用户的权限',
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
    id      bigint not null
        primary key,
    user_id bigint not null,
    post_id bigint not null
)
    comment '用户帖子收藏表';

create index user_post_collect_user_id_index
    on user_post_collect (user_id);

create table user_post_likes
(
    id      bigint not null
        primary key,
    user_id bigint not null,
    post_id bigint not null
)
    comment '用户点赞帖子表';

create index user_post_likes_user_id_index
    on user_post_likes (user_id);

