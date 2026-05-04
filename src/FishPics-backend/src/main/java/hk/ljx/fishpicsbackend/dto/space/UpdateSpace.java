package hk.ljx.fishpicsbackend.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSpace {

    private Long id;

    private String name;

    private String introduction;
}
