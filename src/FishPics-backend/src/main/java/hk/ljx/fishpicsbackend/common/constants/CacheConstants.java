package hk.ljx.fishpicsbackend.common.constants;

public interface CacheConstants {

    String USER_INFO = "cache:userInfo";
    String USER_PERMISSIONS = "cache:userPerm";
    String SYSTEM_CONFIG = "cache:sysConfig";
    String SPACE_DETAIL = "cache:spaceDetail";
    String TEAM_MEMBER = "cache:teamMember";
    String SHARE = "cache:share";

    long USER_INFO_TTL_MINUTES = 60;
    long USER_PERMISSIONS_TTL_MINUTES = 60;
    long SYSTEM_CONFIG_TTL_MINUTES = 1440;
    long SPACE_DETAIL_TTL_MINUTES = 10;
    long TEAM_MEMBER_TTL_MINUTES = 10;
    long SHARE_TTL_MINUTES = 30;
}
