package hk.ljx.fishpicsbackend.dto.space;

import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class SpacePictureList extends PageRequest implements Serializable {

    private Long spaceId;
}
