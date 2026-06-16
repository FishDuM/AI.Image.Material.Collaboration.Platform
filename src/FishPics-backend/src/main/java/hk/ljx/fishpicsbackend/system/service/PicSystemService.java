package hk.ljx.fishpicsbackend.system.service;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;

import hk.ljx.fishpicsbackend.system.dto.AddSysMarqueeRequest;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicTypeRequest;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PicSystemService extends IService<PicSystem> {

    List<String> getTypeList();

    void addTypeList(AddSysPicTypeRequest addSysPicType);

    void deleteType(String type);

    List<String> getMarquess();

    void addMarquee(AddSysMarqueeRequest addSysMarquee);

    void deleteMarquee(String url);
}
