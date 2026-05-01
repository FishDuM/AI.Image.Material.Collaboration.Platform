package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.dto.system.AddSysMarquee;
import hk.ljx.fishpicsbackend.dto.system.AddSysPicType;
import hk.ljx.fishpicsbackend.entity.PicSystem;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.PicSystemService;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.SysConstants.MARQUESS_KEY;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.TYPE_LIST_KEY;

/**
* @author abc
* @description 针对表【pic_system(系统表)】的数据库操作Service实现
* @createDate 2026-05-01 14:33:03
*/
@Service
public class PicSystemServiceImpl extends ServiceImpl<PicSystemMapper, PicSystem>
    implements PicSystemService{

    @Resource
    private PicSystemMapper picSystemMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Override
    public List<String> getTypeList() {
        QueryWrapper<PicSystem> queryWrapper = new QueryWrapper<PicSystem>().eq("syskey", TYPE_LIST_KEY);
        List<PicSystem> list = picSystemMapper.selectList(queryWrapper);
        ExcUtils.throwIfTrue(list == null || list.isEmpty(), "标签不存在");
        PicSystem picSystem = list.get(0);
        ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, "标签不存在");
        return JSONUtil.toList(picSystem.getSysvalue(), String.class);
    }

    @Override
    public void addTypeList(AddSysPicType addSysPicType) {
        ExcUtils.throwIfTrue(addSysPicType.getValue() == null || addSysPicType.getValue().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签不能为空");
        QueryWrapper<PicSystem> queryWrapper = new QueryWrapper<PicSystem>().eq("syskey", TYPE_LIST_KEY);
        List<PicSystem> list = picSystemMapper.selectList(queryWrapper);
        PicSystem picSystem;
        if (list == null || list.isEmpty()) {
            picSystem = new PicSystem();
            picSystem.setSyskey(TYPE_LIST_KEY);
            picSystem.setSysvalue(JSONUtil.toJsonStr(addSysPicType.getValue()));
        } else {
            picSystem = list.get(0);
            List<String> typeList = JSONUtil.toList(picSystem.getSysvalue(), String.class);
            typeList.addAll(addSysPicType.getValue());
            picSystem.setSysvalue(JSONUtil.toJsonStr(typeList));
            for (int i = 1; i < list.size(); i++) {
                picSystemMapper.deleteById(list.get(i).getId());
            }
        }
        this.saveOrUpdate(picSystem);
    }

    @Override
    public List<String> getMarquess() {
        QueryWrapper<PicSystem> queryWrapper = new QueryWrapper<PicSystem>().eq("syskey", MARQUESS_KEY);
        List<PicSystem> list = picSystemMapper.selectList(queryWrapper);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        PicSystem picSystem = list.get(0);
        if (picSystem.getSysvalue() == null) {
            return List.of();
        }
        return JSONUtil.toList(picSystem.getSysvalue(), String.class);
    }

    @Override
    public void addMarquee(AddSysMarquee addSysMarquee) {
        ExcUtils.throwIfTrue(addSysMarquee.getPictureId() == null || addSysMarquee.getPictureId().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片id不能为空");

        List<Long> idList = addSysMarquee.getPictureId().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        List<Picture> pictures = pictureMapper.selectList(new QueryWrapper<Picture>().in("id", idList));
        ExcUtils.throwIfTrue(pictures.isEmpty(), ExceptionCode.NOT_FOUND, "未找到对应的图片，请检查图片id是否正确");

        List<String> marquess = pictures.stream().map(Picture::getUrl).collect(Collectors.toList());

        QueryWrapper<PicSystem> queryWrapper = new QueryWrapper<PicSystem>().eq("syskey", MARQUESS_KEY);
        List<PicSystem> list = picSystemMapper.selectList(queryWrapper);

        PicSystem picSystem;
        if (list == null || list.isEmpty()) {
            picSystem = new PicSystem();
            picSystem.setSyskey(MARQUESS_KEY);
            picSystem.setSysvalue(JSONUtil.toJsonStr(marquess));
        } else {
            picSystem = list.get(0);
            List<String> oldMarquess = JSONUtil.toList(picSystem.getSysvalue(), String.class);
            oldMarquess.addAll(marquess);
            picSystem.setSysvalue(JSONUtil.toJsonStr(oldMarquess));
            for (int i = 1; i < list.size(); i++) {
                picSystemMapper.deleteById(list.get(i).getId());
            }
        }
        this.saveOrUpdate(picSystem);
    }
}




