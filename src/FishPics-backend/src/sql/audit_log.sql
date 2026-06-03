-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名',
    operation VARCHAR(50) COMMENT '操作类型',
    module VARCHAR(50) COMMENT '操作模块',
    detail VARCHAR(500) COMMENT '操作详情',
    method VARCHAR(10) COMMENT '请求方法',
    url VARCHAR(200) COMMENT '请求URL',
    params TEXT COMMENT '请求参数',
    result TINYINT DEFAULT 1 COMMENT '操作结果（0=失败，1=成功）',
    error_msg VARCHAR(500) COMMENT '错误信息',
    ip VARCHAR(50) COMMENT 'IP地址',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    is_delete TINYINT DEFAULT 0 COMMENT '逻辑删除（0=否，1=是）',
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统审计日志表';
