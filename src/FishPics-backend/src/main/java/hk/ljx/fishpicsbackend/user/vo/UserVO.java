package hk.ljx.fishpicsbackend.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

// 登录/资料/搜索/管理端复用，@JsonInclude(NON_NULL) 按场景区分返回字段
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVO implements Serializable {

    private Long id;
    private String username;
    private String avatar;
    private String nickname;
    private String email;
    private String phone;

    // 0=普通 1=VIP 2=SVIP 3=管理员
    private Integer level;
    // 1=正常 0=禁用
    private Integer status;

    private String role;
    private Integer roleId;
    private List<String> permissions;
    private String token;
    private Date createTime;
    private List<Long> roleIds;

    // 隐私设置
    private Integer isPrivateFollows;
    private Integer isPrivatePostCollect;
    private Integer isPrivateLikes;
    private Integer isPrivateFans;

    // ---- 工厂方法，不同场景返回不同字段 ----

    // 登录响应，带 token 和 permissions
    public static UserVO ofLogin(Long id, String username, String nickname, String avatar,
                                  Integer level, Integer roleId, String token, List<String> permissions) {
        return UserVO.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .level(level)
                .roleId(roleId)
                .token(token)
                .permissions(permissions)
                .build();
    }

    // 个人资料页
    public static UserVO ofInfo(Long id, String username, String nickname, String avatar,
                                 String email, String phone, Integer level, Integer roleId, String role,
                                 Date createTime, Integer isPrivateFollows,
                                 Integer isPrivatePostCollect, Integer isPrivateLikes,
                                 Integer isPrivateFans) {
        return UserVO.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .email(email)
                .phone(phone)
                .level(level)
                .roleId(roleId)
                .role(role)
                .createTime(createTime)
                .isPrivateFollows(isPrivateFollows)
                .isPrivatePostCollect(isPrivatePostCollect)
                .isPrivateLikes(isPrivateLikes)
                .isPrivateFans(isPrivateFans)
                .build();
    }

    // 公开主页（别人看到的）
    public static UserVO ofPublicProfile(Long id, String username, String nickname,
                                          String avatar, Integer level, Date createTime) {
        return UserVO.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .level(level)
                .createTime(createTime)
                .build();
    }

    // 管理端，带脱敏
    public static UserVO ofAdmin(Long id, String username, String nickname, String avatar,
                                  String email, String phone, Integer status, Integer level,
                                  Integer roleId, Date createTime, List<Long> roleIds) {
        return UserVO.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .email(maskEmail(email))
                .phone(maskPhone(phone))
                .status(status)
                .level(level)
                .roleId(roleId)
                .createTime(createTime)
                .roleIds(roleIds)
                .build();
    }

    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        int at = email.indexOf('@');
        if (at <= 0) return email;
        if (at <= 1) return email.substring(0, at) + "***" + email.substring(at);
        return StrUtil.hide(email, 1, at) + email.substring(at);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return StrUtil.hide(phone, 3, phone.length() - 4);
    }

    public static UserVO ofAdmin(Long id, String username, String nickname, String avatar,
                                  String email, String phone, Integer status, Integer level,
                                  Integer roleId, List<Long> roleIds) {
        return ofAdmin(id, username, nickname, avatar, email, phone, status, level, roleId, null, roleIds);
    }

    // 搜索结果
    public static UserVO ofSearch(Long id, String nickname, String avatar) {
        return UserVO.builder()
                .id(id)
                .nickname(nickname)
                .avatar(avatar)
                .build();
    }
}
