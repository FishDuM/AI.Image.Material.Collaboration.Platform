package hk.ljx.fishpicsbackend.common.constants;

/**
 * 空间相关常量定义（存储大小单位：字节）
 * 私人空间：普通512MB / VIP 5GB / SVIP 10GB
 * 团队空间：普通512MB / VIP 30GB / SVIP 50GB
 */
public interface SpaceConstants {

    // 普通用户私人空间：512MB
    Long DEFAULT_STORAGE_SIZE = 536870912L;

    // VIP私人空间：5GB
    Long VIP_STORAGE_SIZE = 5368709120L;

    // SVIP私人空间：10GB
    Long SVIP_STORAGE_SIZE = 10737418240L;

    // VIP团队空间：30GB
    Long TEAM_VIP_STORAGE_SIZE = 32212254720L;

    // SVIP团队空间：50GB
    Long TEAM_SVIP_STORAGE_SIZE = 53687091200L;

    // 普通用户团队空间数量上限
    int TEAM_MAX_COUNT_LEVEL0 = 1;

    // VIP团队空间数量上限
    int TEAM_MAX_COUNT_LEVEL1 = 5;

    // SVIP团队空间数量上限
    int TEAM_MAX_COUNT_LEVEL2 = 10;
}
