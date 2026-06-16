package hk.ljx.fishpicsbackend.common.constants;

public interface CacheConstants {

    String USER_INFO = "cache:userInfo";
    String USER_PERMISSIONS = "cache:userPerm";
    String SYSTEM_CONFIG = "cache:sysConfig";

    long USER_INFO_TTL_MINUTES = 60;
    long USER_PERMISSIONS_TTL_MINUTES = 60;
    long SYSTEM_CONFIG_TTL_MINUTES = 1440;
}
