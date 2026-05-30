package hk.ljx.fishpicsbackend.user.service.impl;
import hk.ljx.fishpicsbackend.user.entity.UserFans;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.UserFansMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserFansService;
import hk.ljx.fishpicsbackend.user.vo.FollowUserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
* @author 30574
* @description 针对表【user_fans(用户粉丝表)】的数据库操作Service实现
* @createDate 2026-04-26 13:51:33
*/
@Slf4j
@Service
public class UserFansServiceImpl extends ServiceImpl<UserFansMapper, UserFans>
    implements UserFansService {

    @Resource
    private UserMapper userMapper;

    @Override
    public boolean follow(Long targetUserId) {
        User currentUser = UserHolder.getUser();
        Long currentUserId = currentUser.getId();

        ExcUtils.throwIfTrue(currentUserId.equals(targetUserId), "不能关注自己");

        User targetUser = userMapper.selectById(targetUserId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null,
                ExceptionCode.NOT_FOUND, "用户不存在");

        QueryWrapper<UserFans> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", targetUserId);
        queryWrapper.eq("fan_id", currentUserId);
        UserFans existingFans = baseMapper.selectOne(queryWrapper);

        boolean followed;

        if (existingFans != null) {
            baseMapper.deleteById(existingFans.getId());
            followed = false;
        } else {
            UserFans userFans = new UserFans();
            userFans.setUserId(targetUserId);
            userFans.setFanId(currentUserId);
            baseMapper.insert(userFans);
            followed = true;
        }

        return followed;
    }

    @Override
    public IPage<FollowUserVO> getFans(Long userId, int current, int pageSize) {
        checkFansPrivacy(userId);
        Page<?> page = new Page<>(current, pageSize);
        return baseMapper.selectFansPage(page, userId);
    }

    @Override
    public IPage<FollowUserVO> getFollows(Long userId, int current, int pageSize) {
        checkFollowsPrivacy(userId);
        Page<?> page = new Page<>(current, pageSize);
        return baseMapper.selectFollowsPage(page, userId);
    }

    private void checkFansPrivacy(Long userId) {
        User targetUser = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null,
                ExceptionCode.NOT_FOUND, "用户不存在");

        User currentUser = UserHolder.getUser();
        if (targetUser.getIsPrivateFans() != null && targetUser.getIsPrivateFans() == 1) {
            ExcUtils.throwIfTrue(currentUser == null || !currentUser.getId().equals(userId),
                    ExceptionCode.FORBIDDEN, "该用户设置了粉丝列表不公开");
        }
    }

    private void checkFollowsPrivacy(Long userId) {
        User targetUser = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null,
                ExceptionCode.NOT_FOUND, "用户不存在");

        User currentUser = UserHolder.getUser();
        if (targetUser.getIsPrivateFollows() != null && targetUser.getIsPrivateFollows() == 1) {
            ExcUtils.throwIfTrue(currentUser == null || !currentUser.getId().equals(userId),
                    ExceptionCode.FORBIDDEN, "该用户设置了关注列表不公开");
        }
    }
}




