package hk.ljx.fishpicsbackend.system;

import hk.ljx.fishpicsbackend.system.dto.AddSysMarquee;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicType;
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
     * 删除帖子标签
     * @param type 标签名
     */
    void deleteType(String type);

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

    /**
     * 删除跑马灯图片
     * @param url 图片 url
     */
    void deleteMarquee(String url);
}
