package hk.ljx.fishpicsbackend.common.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeleteById implements Serializable {
    private Long id;
}
