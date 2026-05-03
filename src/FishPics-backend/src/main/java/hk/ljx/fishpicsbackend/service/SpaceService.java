package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author abc
* @description 针对表【space(空间表)】的数据库操作Service
* @createDate 2026-05-03 15:29:23
*/
public interface SpaceService extends IService<Space> {

    /**
     * 用户创建空间
     *
     * @param createSpace 创建空间参数
     * @return 结果
     */
    Boolean createSpace(CreateSpace createSpace, HttpServletRequest request);

    /**
     * 获取空间列表
     * @param type 空间类型 0:私人空间 1:团队空间
     * @param request 请求
     * @return 空间列表
     */
    List<Space> listSpace(Integer type, HttpServletRequest request);
}
