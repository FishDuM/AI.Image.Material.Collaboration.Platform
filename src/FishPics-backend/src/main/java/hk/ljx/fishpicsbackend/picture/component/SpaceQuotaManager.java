package hk.ljx.fishpicsbackend.picture.component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class SpaceQuotaManager {

    @Resource
    private SpaceMapper spaceMapper;

    public boolean reserve(Space space, long size) {
        Long storageSize = space.getStorageSize();
        LambdaUpdateWrapper<Space> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Space::getId, space.getId());
        if (storageSize != null && storageSize > 0) {
            updateWrapper.apply("COALESCE(size, 0) + {0} <= storage_size", size);
        }
        updateWrapper.setSql("size = COALESCE(size, 0) + {0}", size);
        return spaceMapper.update(null, updateWrapper) > 0;
    }

    public boolean release(Space space, long size) {
        LambdaUpdateWrapper<Space> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Space::getId, space.getId())
                .setSql("size = GREATEST(COALESCE(size, 0) - {0}, 0)", size);
        return spaceMapper.update(null, updateWrapper) > 0;
    }

    public boolean adjust(Space space, long diff) {
        if (diff > 0) {
            return reserve(space, diff);
        }
        if (diff < 0) {
            return release(space, -diff);
        }
        return true;
    }
}
