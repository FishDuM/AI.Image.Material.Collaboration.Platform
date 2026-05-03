package hk.ljx.fishpicsbackend.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateSpace {

    private String name;

    private String introduction;

    /**
     * 0-私人空间 1-团队空间
     */
    private Integer type;
}
