package hk.ljx.fishpicsbackend.post.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
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
