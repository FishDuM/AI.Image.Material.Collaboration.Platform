package hk.ljx.fishpicsbackend.dto.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddSysMarquee implements Serializable {
    List<String> pictureId;
}
