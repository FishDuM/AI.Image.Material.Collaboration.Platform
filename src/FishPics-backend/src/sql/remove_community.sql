-- ============================================================
-- 社区功能清理脚本
-- 执行前请备份数据库！
-- ============================================================

USE FishPics;

-- 1. 删除社区相关表（按依赖顺序）
DROP TABLE IF EXISTS user_post_collect;
DROP TABLE IF EXISTS user_post_likes;
DROP TABLE IF EXISTS user_interest_profile;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS picture_child;
DROP TABLE IF EXISTS post;

-- 2. 清理 user 表中的社区相关字段（可选）
-- 注意：这些字段删除后不可恢复，建议先确认业务不需要
ALTER TABLE user DROP COLUMN IF EXISTS like_num;
ALTER TABLE user DROP COLUMN IF EXISTS collect_num;
ALTER TABLE user DROP COLUMN IF EXISTS is_private_post_collect;
ALTER TABLE user DROP COLUMN IF EXISTS is_private_likes;

-- 3. 清理权限表中的社区相关权限
DELETE FROM sys_role_permission WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE module IN ('post', 'comment')
);
DELETE FROM sys_permission WHERE module IN ('post', 'comment');

-- 4. 更新角色描述（可选）
-- reviewer 角色不再需要帖子和评论审核权限
-- editor 角色不再需要帖子和评论相关权限

SELECT '社区功能清理完成！' AS message;
