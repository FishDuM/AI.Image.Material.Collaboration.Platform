package hk.ljx.fishpicsbackend.picture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;

/**
 * 物理文件去重表 Service
 */
public interface FileResourceService extends IService<FileResource> {

    /**
     * 根据 MD5 和文件大小查找已有资源（秒传校验）
     *
     * @param md5  文件 MD5
     * @param size 文件大小
     * @return 匹配的 FileResource，不存在返回 null
     */
    FileResource findByMd5AndSize(String md5, Long size);

    /**
     * 新增文件资源记录（带引用计数）
     *
     * @param md5    文件 MD5
     * @param size   文件大小
     * @param cosKey COS 存储路径
     * @return 新增的 FileResource
     */
    FileResource addResource(String md5, Long size, String cosKey);

    /**
     * 增加引用计数
     *
     * @param resourceId 文件资源 ID
     * @return 更新后的引用计数，-1 表示记录不存在
     */
    int incrementRefCount(Long resourceId);

    /**
     * 减少引用计数，减到 0 则删除记录
     *
     * @param resourceId 文件资源 ID
     * @return 剩余引用计数；-1 表示记录不存在；0 表示已删除
     */
    int decrementRefCount(Long resourceId);
}
