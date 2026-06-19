package hk.ljx.fishpicsbackend.user.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class UserQueryWrapper extends PageRequest {

    private Long id;

    private String username;

    private String email;

    private String phone;

    private String nickname;

    /** 状态 1-正常 0-禁用 2-待审核 */
    private Integer status;

    private LocalDateTime createTime;

}
