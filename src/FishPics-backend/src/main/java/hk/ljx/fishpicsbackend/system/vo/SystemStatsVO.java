package hk.ljx.fishpicsbackend.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统统计概览
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsVO {

    // 用户总数
    private Long totalUsers;

    // 图片总数
    private Long totalPictures;

    // 空间总数
    private Long totalSpaces;

    // 今日新增用户数
    private Long todayNewUsers;

    // 今日新增图片数
    private Long todayNewPictures;
}
