package hk.ljx.fishpicsbackend.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 统一用户 VO
 * 合并了原来的 UserLoginVO、UserMessageVO、UserPublicProfileVO
 *
 * 设计原则：
 * - 使用 @JsonInclude 控制不同场景返回不同字段
 * - 简化前端接口，一个 VO 适配多种场景
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // 只返回非空字段
public class UserVO implements Serializable {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名（登录用）
     */
    private String username;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 昵称（展示用）
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 用户等级 0=普通 1=VIP 2=SVIP
     */
    private Integer level;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 用户角色（用于权限判断）
     */
    private String role;

    /**
     * 拥有的权限码列表（前端用于控制菜单/按钮显示）
     */
    private List<String> permissions;

    /**
     * JWT Token（仅登录时返回）
     */
    private String token;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 用户角色ID列表（仅管理端查看用）
     */
    private List<Long> roleIds;

    // ==================== 隐私设置字段 ====================

    /**
     * 关注列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivateFollows;

    /**
     * 收藏列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivatePostCollect;

    /**
     * 点赞列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivateLikes;

    /**
     * 粉丝列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivateFans;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建登录响应 VO（包含 token 和 permissions）
     */
    public static UserVO ofLogin(Long id, String username, String nickname, String avatar,
                                  Integer level, String token, List<String> permissions) {
        return UserVO.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .level(level)
                .token(token)
                .permissions(permissions)
                .build();
    }

    /**
     * 创建用户信息 VO（不含敏感信息）
     */
    public static UserVO ofInfo(Long id, String username, String nickname, String avatar,
                                 String email, String phone, Integer level, String role,
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
                .role(role)
                .createTime(createTime)
                .isPrivateFollows(isPrivateFollows)
                .isPrivatePostCollect(isPrivatePostCollect)
                .isPrivateLikes(isPrivateLikes)
                .isPrivateFans(isPrivateFans)
                .build();
    }

    /**
     * 创建公开资料 VO（其他用户可见的信息）
     */
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

    /**
     * 创建管理员查看的 VO（包含状态和角色信息）
     */
    public static UserVO ofAdmin(Long id, String username, String nickname, String avatar,
                                  String email, String phone, Integer status, Integer level,
                                  Date createTime, List<Long> roleIds) {
        return UserVO.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .email(email)
                .phone(phone)
                .status(status)
                .level(level)
                .createTime(createTime)
                .roleIds(roleIds)
                .build();
    }

    public static UserVO ofAdmin(Long id, String username, String nickname, String avatar,
                                  String email, String phone, Integer status, Integer level,
                                  List<Long> roleIds) {
        return ofAdmin(id, username, nickname, avatar, email, phone, status, level, null, roleIds);
    }

    /**
     * 创建搜索结果 VO（最少信息）
     */
    public static UserVO ofSearch(Long id, String nickname, String avatar) {
        return UserVO.builder()
                .id(id)
                .nickname(nickname)
                .avatar(avatar)
                .build();
    }
}
