package hk.ljx.fishpicsbackend.vo.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureAdminVO {

    private Long id;

    private String url;

    private String width;

    private String height;

    private Long size;

    private Integer status;

    private Date createTime;

    private Long userId;

    private Integer isPrivate;

    private List<String> tags;
}
