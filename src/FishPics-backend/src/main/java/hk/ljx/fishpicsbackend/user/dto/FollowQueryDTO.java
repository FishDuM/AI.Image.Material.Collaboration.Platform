package hk.ljx.fishpicsbackend.user.dto;

import lombok.Data;

/**
 * 粉丝/关注列表查询请求
 */
@Data
public class FollowQueryDTO {

    /**
     * 用户ID，不传则查当前登录用户
     */
    private Long userId;

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 20;
}
