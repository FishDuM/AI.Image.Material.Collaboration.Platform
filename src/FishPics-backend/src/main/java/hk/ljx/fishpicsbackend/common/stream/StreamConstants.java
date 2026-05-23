package hk.ljx.fishpicsbackend.common.stream;

public interface StreamConstants {

    // Stream keys
    String STREAM_AI_TAGGING = "fishpics:stream:ai-tagging";
    String STREAM_AI_TASK = "fishpics:stream:ai-task";
    String STREAM_SOCIAL = "fishpics:stream:social";
    String STREAM_COS_CLEANUP = "fishpics:stream:cos-cleanup";

    // Consumer groups
    String GROUP_AI_TAGGING = "ai-tagging-consumers";
    String GROUP_AI_TASK = "ai-task-consumers";
    String GROUP_SOCIAL = "social-consumers";
    String GROUP_COS_CLEANUP = "cos-cleanup-consumers";

    // Consumer names
    String CONSUMER_AI_TAGGING = "ai-tagging-worker";
    String CONSUMER_AI_TASK = "ai-task-worker";
    String CONSUMER_SOCIAL = "social-worker";
    String CONSUMER_COS = "cos-worker";

    // Event types
    String EVENT_AI_TAGGING = "AI_TAGGING";
    String EVENT_AI_GENERATION = "AI_GENERATION";
    String EVENT_AI_EDITING = "AI_EDITING";
    String EVENT_AI_RECOMMENDATION = "AI_RECOMMENDATION";
    String EVENT_SOCIAL_LIKE = "SOCIAL_LIKE";
    String EVENT_SOCIAL_COLLECT = "SOCIAL_COLLECT";
    String EVENT_SOCIAL_FOLLOW = "SOCIAL_FOLLOW";
    String EVENT_SOCIAL_COMMENT = "SOCIAL_COMMENT";
    String EVENT_COS_CLEANUP = "COS_CLEANUP";

    // Polling config
    int POLL_TIMEOUT_MS = 2000;
    int POLL_COUNT = 5;
    int MAX_RETRIES = 5;
}
