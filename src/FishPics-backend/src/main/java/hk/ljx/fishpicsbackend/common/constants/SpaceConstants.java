package hk.ljx.fishpicsbackend.common.constants;

/**
 * Space quotas and upload limits. Storage units are bytes.
 */
public interface SpaceConstants {

    int SPACE_TYPE_PRIVATE = 0;
    int SPACE_TYPE_TEAM = 1;
    int SPACE_STATUS_DISABLED = 0;
    int SPACE_STATUS_ENABLED = 1;

    long GB = 1024L * 1024 * 1024;

    // Private space quota: normal 1GB / VIP 5GB / SVIP 10GB.
    Long DEFAULT_STORAGE_SIZE = 1L * GB;
    Long VIP_STORAGE_SIZE = 5L * GB;
    Long SVIP_STORAGE_SIZE = 10L * GB;

    // Team space quota: normal 5GB / VIP 10GB / SVIP 20GB.
    Long TEAM_DEFAULT_STORAGE_SIZE = 5L * GB;
    Long TEAM_VIP_STORAGE_SIZE = 10L * GB;
    Long TEAM_SVIP_STORAGE_SIZE = 20L * GB;

    // Team space count: normal 1 / VIP 3 / SVIP 5.
    int TEAM_MAX_COUNT_LEVEL0 = 1;
    int TEAM_MAX_COUNT_LEVEL1 = 3;
    int TEAM_MAX_COUNT_LEVEL2 = 5;

    // Upload file size limit: normal 10MB / VIP 50MB / SVIP 100MB.
    Long UPLOAD_MAX_SIZE_NORMAL = 10L * 1024 * 1024;
    Long UPLOAD_MAX_SIZE_VIP = 50L * 1024 * 1024;
    Long UPLOAD_MAX_SIZE_SVIP = 100L * 1024 * 1024;
}
