package hk.ljx.fishpicsbackend.common.constants;

public interface SysConstants {

    String TYPE_LIST_KEY = "type_list_key";
    String MARQUESS_KEY = "marquees_key";
    String AI_CONFIG_KEY = "ai_config";

    // 帖子相关
    int POST_MAX_PICTURE_COUNT = 15;
    int POST_STATUS_DRAFT = 0;
    int POST_STATUS_PUBLISHED = 1;
    int POST_STATUS_PENDING = 2;
    int POST_STATUS_REJECTED = 3;
    int POST_DEFAULT_COVER = 0;

    // 缓存击穿重试
    int CACHE_RETRY_MAX_POLLS = 25;
    int CACHE_RETRY_INTERVAL_MS = 100;

    // 分布式锁
    long LOCK_WAIT_SECONDS = 0;
    long LOCK_LEASE_SECONDS = 10;
}
