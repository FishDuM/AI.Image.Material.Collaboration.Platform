package hk.ljx.fishpicsbackend.picture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.mapper.FileResourceMapper;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import jakarta.annotation.Resource;
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

    @Resource
    private CosService cosService;

    @Override
    public FileResource findByMd5AndSize(String md5, Long size) {
        return getOne(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getMd5, md5)
                .eq(FileResource::getSize, size));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResource addResource(String md5, Long size, String cosKey) {
        // 原子 upsert：不存在则插入(ref_count=1)，已存在则 ref_count+1
        baseMapper.upsertByMd5Size(md5, size, cosKey);
        // 查回记录后做 null 检查，防止并发 decrement 导致 NPE
        FileResource resource = findByMd5AndSize(md5, size);
        if (resource == null) {
            log.error("[FileResource] addResource 后查询返回 null(可能并发 decrement 删除): md5={}, size={}", md5, size);
            // 重试一次：再 upsert + 查回
            baseMapper.upsertByMd5Size(md5, size, cosKey);
            resource = findByMd5AndSize(md5, size);
            if (resource == null) {
                throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                        hk.ljx.fishpicsbackend.common.exception.ExceptionCode.INTERNAL_SERVER_ERROR,
                        "文件资源创建失败，请重试");
            }
        }
        return resource;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int incrementRefCount(Long resourceId) {
        // 原子递增（SQL 层面），避免并发下 TOCTOU 竞态
        int affected = baseMapper.incrementRefCountAtomic(resourceId);
        if (affected == 0) {
            return -1; // 记录不存在
        }
        // 查回递增后的 ref_count
        FileResource resource = getById(resourceId);
        return resource != null ? resource.getRefCount() : -1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int decrementRefCount(Long resourceId) {
        // 先查回 cosKey（原子删除前需要此信息）
        FileResource resource = getById(resourceId);
        if (resource == null) {
            return -1; // 记录不存在
        }
        // 原子 UPDATE 将 ref_count 减 1
        int affected = baseMapper.decrementRefCountAtomic(resourceId);
        if (affected == 0) {
            return -1; // ref_count 已为 0，无需再减
        }
        // 原子 DELETE：仅当 ref_count <= 0 时删除记录，消除 TOCTOU 竞态
        int deleted = baseMapper.deleteIfZeroRef(resourceId);
        if (deleted > 0) {
            // 引用归零，记录已删除，在事务提交后清理 COS 文件
            final String cosKey = resource.getCosKey();
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                cosService.deletePicture(cosKey);
                                log.info("COS 文件已删除: cosKey={}", cosKey);
                            } catch (Exception e) {
                                log.error("COS 文件删除失败: cosKey={}", cosKey, e);
                            }
                        }
                    });
            return 0;
        }
        // ref_count > 0，查回当前值
        Integer newCount = baseMapper.getRefCount(resourceId);
        return newCount != null ? newCount : 0;
    }
}
