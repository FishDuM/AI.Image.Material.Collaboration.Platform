package hk.ljx.fishpicsbackend.dto.base;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeleteById implements Serializable {
    private Long id;
}
