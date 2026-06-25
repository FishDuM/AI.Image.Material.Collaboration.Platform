package hk.ljx.fishpicsbackend.space.component;

import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class SpaceAccessResolver {

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpacePermissionChecker spacePermissionChecker;

    public Space resolve(Long spaceId) {
        Space space = spaceMapper.selectById(spaceId);
        Space.validateActive(space);
        Long userId = LoginContextHelper.requireUser().getId();
        spacePermissionChecker.checkAccess(space, userId);
        return space;
    }
}
