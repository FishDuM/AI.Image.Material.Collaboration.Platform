package hk.ljx.fishpicsbackend.common.stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {

    private String eventId;
    private String eventType;
    private long timestamp;
    private int retryCount;
    private Map<String, Object> payload;

    public static StreamEvent of(String eventType, Map<String, Object> payload) {
        StreamEvent e = new StreamEvent();
        e.eventId = UUID.randomUUID().toString();
        e.eventType = eventType;
        e.timestamp = System.currentTimeMillis();
        e.retryCount = 0;
        e.payload = payload != null ? payload : new HashMap<>();
        return e;
    }
}
