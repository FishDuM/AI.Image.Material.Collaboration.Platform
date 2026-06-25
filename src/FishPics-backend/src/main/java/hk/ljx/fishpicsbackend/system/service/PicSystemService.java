package hk.ljx.fishpicsbackend.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.system.dto.AddSysMarqueeRequest;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicTypeRequest;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;

import java.util.List;

public interface PicSystemService extends IService<PicSystem> {

    List<String> getTypeList();

    void addTypeList(AddSysPicTypeRequest addSysPicType);

    void deleteType(String type);

    List<String> getMarquees();

    void addMarquee(AddSysMarqueeRequest addSysMarquee);

    void deleteMarquee(String url);
}
