package hk.ljx.fishpicsbackend.common.constants;

/**
 * 空间相关常量定义（存储大小单位：字节）
 * 私人空间：普通512MB / VIP 50GB / SVIP 100GB
 * 团队空间：普通512MB / VIP 50GB / SVIP 100GB
 */
public interface SpaceConstants {

    // ==================== 私人空间存储配额 ====================
    Long DEFAULT_STORAGE_SIZE = 536870912L;       // 普通 512MB
    Long VIP_STORAGE_SIZE     = 53687091200L;     // VIP 50GB
    Long SVIP_STORAGE_SIZE    = 107374182400L;    // SVIP 100GB

    // ==================== 团队空间存储配额（每个空间独立） ====================
    // 与私人空间配额相同，有意重复以保持独立可调
    Long TEAM_DEFAULT_STORAGE_SIZE = 536870912L;       // 普通 512MB
    Long TEAM_VIP_STORAGE_SIZE     = 53687091200L;     // VIP 50GB
    Long TEAM_SVIP_STORAGE_SIZE    = 107374182400L;    // SVIP 100GB

    // ==================== 团队空间数量上限 ====================
    int TEAM_MAX_COUNT_LEVEL0 = 1;     // 普通 1 个
    int TEAM_MAX_COUNT_LEVEL1 = 2;     // VIP 2 个
    int TEAM_MAX_COUNT_LEVEL2 = 5;     // SVIP 5 个

    // ==================== 上传文件大小限制 ====================
    Long UPLOAD_MAX_SIZE_NORMAL = 10485760L;      // 普通 10MB
    Long UPLOAD_MAX_SIZE_VIP    = 1073741824L;    // VIP 1GB
    Long UPLOAD_MAX_SIZE_SVIP   = 10737418240L;   // SVIP 10GB
}
