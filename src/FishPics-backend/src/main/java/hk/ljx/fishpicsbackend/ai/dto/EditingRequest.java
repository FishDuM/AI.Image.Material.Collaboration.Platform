package hk.ljx.fishpicsbackend.ai.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditingRequest {
    private String imageUrl;
    private String editType;
    private Map<String, Object> options;
}
