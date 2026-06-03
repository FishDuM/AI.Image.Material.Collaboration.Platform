package hk.ljx.fishpicsbackend.common.constants;

// 多级缓存常量
public interface CacheConstants {

    // 缓存名称（L2 Redis key前缀）
    String USER_INFO = "ml:userInfo";
    String USER_PERMISSIONS = "ml:userPerm";
    String POST_LIST = "ml:postList";
    String SYSTEM_CONFIG = "ml:sysConfig";

    // L1 Caffeine TTL（秒）
    long L1_USER_INFO = 30;
    long L1_USER_PERMISSIONS = 300;
    long L1_POST_LIST = 30;
    long L1_SYSTEM_CONFIG = 600;

    // L2 Redis TTL（分钟）
    long L2_USER_INFO = 60;
    long L2_USER_PERMISSIONS = 60;
    long L2_POST_LIST = 3;
    long L2_SYSTEM_CONFIG = 1440;
}
