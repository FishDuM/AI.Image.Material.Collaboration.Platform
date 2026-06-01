package hk.ljx.fishpicsbackend.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketNotifyMessage implements Serializable {
    private Long userId;
    private String payload;
}
