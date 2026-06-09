package hk.ljx.fishpicsbackend.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.SysAuditLogMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.system.dto.AuditLogQueryRequest;
import hk.ljx.fishpicsbackend.system.service.AuditLogService;
import hk.ljx.fishpicsbackend.system.vo.SystemStatsVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 审计日志 & 系统统计服务实现
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Resource
    private SysAuditLogMapper sysAuditLogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private SpaceMapper spaceMapper;

    @Override
    public IPage<SysAuditLog> pageQuery(AuditLogQueryRequest request) {
        long current = Math.max(request.getCurrent(), 1);
        long pageSize = Math.min(Math.max(request.getPageSize(), 1), 100);
        Page<SysAuditLog> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<SysAuditLog> qw = new LambdaQueryWrapper<>();

        if (request.getOperation() != null && !request.getOperation().isBlank()) {
            qw.eq(SysAuditLog::getOperation, request.getOperation());
        }
        if (request.getModule() != null && !request.getModule().isBlank()) {
            qw.eq(SysAuditLog::getModule, request.getModule());
        }
        if (request.getResult() != null) {
            qw.eq(SysAuditLog::getResult, request.getResult());
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            qw.like(SysAuditLog::getUsername, request.getUsername());
        }

        qw.orderByDesc(SysAuditLog::getCreateTime);
        return sysAuditLogMapper.selectPage(page, qw);
    }

    @Override
    public SystemStatsVO getStats() {
        long totalUsers = userMapper.selectCount(null);
        long totalPictures = pictureMapper.selectCount(null);
        long totalSpaces = spaceMapper.selectCount(null);

        long todayNewUsers = userMapper.selectCount(
                new QueryWrapper<User>().apply("DATE(create_time) = CURDATE()"));
        long todayNewPictures = pictureMapper.selectCount(
                new QueryWrapper<Picture>().apply("DATE(create_time) = CURDATE()"));

        return new SystemStatsVO(
                totalUsers, totalPictures, totalSpaces,
                todayNewUsers, todayNewPictures
        );
    }
}
