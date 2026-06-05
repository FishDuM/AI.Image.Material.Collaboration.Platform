package hk.ljx.fishpicsbackend.picture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.mapper.FileResourceMapper;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物理文件去重表 Service 实现
 */
@Slf4j
@Service
public class FileResourceServiceImpl extends ServiceImpl<FileResourceMapper, FileResource>
        implements FileResourceService {

    @Override
    public FileResource findByMd5AndSize(String md5, Long size) {
        return getOne(new QueryWrapper<FileResource>()
                .eq("md5", md5)
                .eq("size", size));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResource addResource(String md5, Long size, String cosKey) {
        // 幂等：先查是否已存在
        FileResource existing = findByMd5AndSize(md5, size);
        if (existing != null) {
            // 已存在，增加引用计数
            existing.setRefCount(existing.getRefCount() + 1);
            updateById(existing);
            return existing;
        }
        FileResource resource = new FileResource();
        resource.setMd5(md5);
        resource.setSize(size);
        resource.setCosKey(cosKey);
        resource.setRefCount(1);
        save(resource);
        return resource;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int incrementRefCount(Long resourceId) {
        FileResource resource = getById(resourceId);
        if (resource == null) {
            return -1;
        }
        resource.setRefCount(resource.getRefCount() + 1);
        updateById(resource);
        return resource.getRefCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int decrementRefCount(Long resourceId) {
        FileResource resource = getById(resourceId);
        if (resource == null) {
            return -1;
        }
        int newCount = resource.getRefCount() - 1;
        if (newCount <= 0) {
            // 引用计数归零，删除记录
            removeById(resourceId);
            return 0;
        }
        resource.setRefCount(newCount);
        updateById(resource);
        return newCount;
    }
}
