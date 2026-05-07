package hk.ljx.fishpicsbackend.dto.post;

import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetPictureBySpaceRequest extends PageRequest implements Serializable {

    private Long spaceId;

    private List<Long> pictureIds;
}
