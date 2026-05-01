package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.dto.system.AddSysMarquee;
import hk.ljx.fishpicsbackend.dto.system.AddSysPicType;
import hk.ljx.fishpicsbackend.entity.PicSystem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author abc
* @description 针对表【pic_system(系统表)】的数据库操作Service
* @createDate 2026-05-01 14:33:03
*/
public interface PicSystemService extends IService<PicSystem> {

    /**
     * 获取帖子标签
     * @return 标签列表
     */
    List<String> getTypeList();

    /**
     * 添加帖子标签
     * @param addSysPicType 标签
     */
    void addTypeList(AddSysPicType addSysPicType);

    /**
     * 设置首页跑马灯图片
     * @return url 列表
     */
    List<String> getMarquess();

    /**
     * 添加跑马灯图片
     * @param addSysMarquee 图片 id
     */
    void addMarquee(AddSysMarquee addSysMarquee);
}
