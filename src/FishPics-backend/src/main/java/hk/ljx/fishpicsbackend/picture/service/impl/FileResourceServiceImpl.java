package hk.ljx.fishpicsbackend.picture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.mapper.FileResourceMapper;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Physical file resource service.
 */
@Slf4j
@Service
public class FileResourceServiceImpl extends ServiceImpl<FileResourceMapper, FileResource> implements FileResourceService {

    @Resource
    private CosService cosService;

    public FileResource findByMd5AndSize(String md5, Long size) {
        return getOne(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getMd5, md5)
                .eq(FileResource::getSize, size));
    }

    @Transactional(rollbackFor = Exception.class)
    public FileResource addResource(String md5, Long size, String cosKey) {
        FileResource resource = findByMd5AndSize(md5, size);
        if (resource != null) {
            incrementRefCount(resource.getId());
            return getById(resource.getId());
        }

        resource = new FileResource();
        resource.setMd5(md5);
        resource.setSize(size);
        resource.setCosKey(cosKey);
        resource.setRefCount(1);

        try {
            if (save(resource)) {
                return resource;
            }
        } catch (DuplicateKeyException e) {
            log.info("[FileResource] 并发插入命中唯一键，重试递增引用: md5={}, size={}", md5, size);
            return incrementExistingResource(md5, size);
        }

        throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "文件资源创建失败，请重试");
    }

    private FileResource incrementExistingResource(String md5, Long size) {
        FileResource existing = findByMd5AndSize(md5, size);
        if (existing == null) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "文件资源创建失败，请重试");
        }
        incrementRefCount(existing.getId());
        return getById(existing.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public int incrementRefCount(Long resourceId) {
        update(new LambdaUpdateWrapper<FileResource>()
                .eq(FileResource::getId, resourceId)
                .setSql("ref_count = ref_count + 1"));
        FileResource refreshed = getById(resourceId);
        return refreshed != null ? refreshed.getRefCount() : -1;
    }

    @Transactional(rollbackFor = Exception.class)
    public int decrementRefCount(Long resourceId) {
        FileResource resource = getById(resourceId);
        if (resource == null) {
            return -1;
        }
        if (resource.getRefCount() <= 0) {
            return 0;
        }

        update(new LambdaUpdateWrapper<FileResource>()
                .eq(FileResource::getId, resourceId)
                .gt(FileResource::getRefCount, 0)
                .setSql("ref_count = ref_count - 1"));

        FileResource refreshed = getById(resourceId);
        if (refreshed == null) {
            return -1;
        }

        if (refreshed.getRefCount() == 0) {
            boolean removed = remove(new LambdaQueryWrapper<FileResource>()
                    .eq(FileResource::getId, resourceId)
                    .le(FileResource::getRefCount, 0));
            if (removed) {
                registerCosCleanup(refreshed.getCosKey());
                return 0;
            }
        }

        return refreshed.getRefCount();
    }

    private void registerCosCleanup(String cosKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    cosService.deletePicture(cosKey);
                    log.info("COS 文件已删除: cosKey={}", cosKey);
                } catch (Exception e) {
                    log.error("删除 COS 文件失败: cosKey={}", cosKey, e);
                }
            }
        });
    }
}
