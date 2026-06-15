package hk.ljx.fishpicsbackend.collab.model;

import com.lmax.disruptor.EventFactory;
import lombok.Data;

/**
 * 协同编辑事件（Disruptor Ring Buffer 事件对象）
 * 每个槽位预分配此对象，发布时仅写入字段，零 GC
 */
@Data
public class CollabEvent {

    public static final EventFactory<CollabEvent> FACTORY = CollabEvent::new;

    public static final int TYPE_TRANSFORM = 1;
    public static final int TYPE_JOIN = 2;
    public static final int TYPE_LEAVE = 3;
    public static final int TYPE_LOCK = 4;
    public static final int TYPE_UNLOCK = 5;
    public static final int TYPE_REQUEST_EDIT = 6;
    public static final int TYPE_APPROVE = 7;
    public static final int TYPE_DENY = 8;
    /** 文件被替换（由 HTTP /picture/replace 完成后由后端主动推送） */
    public static final int TYPE_FILE_REPLACED = 9;
    /** 用户断连（由 Disruptor 消费者处理锁转移） */
    public static final int TYPE_DISCONNECT = 10;

    /** 事件类型 */
    private int type;

    /** 操作的目标图片 ID */
    private Long pictureId;

    /** 所属团队空间 ID */
    private Long spaceId;

    /** 操作用户 ID */
    private Long userId;
    private String nickname;
    private String avatar;

    /**
     * 目标用户 ID
     */
    private Long targetUserId;

    /** WebSocket session ID（用于 removeSession 时校验，防止误删新 session） */
    private String sessionId;

    /** 缩放比例 */
    private Double scale;

    /** 旋转角度 */
    private Integer rotation;

    /** 裁剪区域（可选，null 表示不裁剪） */
    private Integer cropX;
    private Integer cropY;
    private Integer cropW;
    private Integer cropH;

    /**
     * 清空事件（Ring Buffer 复用槽位时由 Disruptor 调用）
     */
    public void clear() {
        type = 0;
        pictureId = null;
        spaceId = null;
        userId = null;
        nickname = null;
        avatar = null;
        targetUserId = null;
        sessionId = null;
        scale = null;
        rotation = null;
        cropX = null;
        cropY = null;
        cropW = null;
        cropH = null;
    }
}
