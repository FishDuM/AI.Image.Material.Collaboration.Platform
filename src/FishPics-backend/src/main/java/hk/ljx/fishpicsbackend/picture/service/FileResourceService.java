package hk.ljx.fishpicsbackend.picture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;

public interface FileResourceService extends IService<FileResource> {

    FileResource findByMd5AndSize(String md5, Long size);

    FileResource addResource(String md5, Long size, String cosKey);

    int incrementRefCount(Long resourceId);

    int decrementRefCount(Long resourceId);
}
