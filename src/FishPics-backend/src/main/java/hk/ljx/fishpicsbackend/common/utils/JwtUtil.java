package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTException;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;


public class JwtUtil {

    // 统一密钥（一定要统一，生成和解析用同一个！）
    private static final String SECRET_KEY = "fish";
    // 签名器（HMAC256 加密）
    private static final JWTSigner SIGNER = JWTSignerUtil.hs256(SECRET_KEY.getBytes());

    /**
     * 生成 Token（默认 2 小时过期）
     * @param userId 用户ID
     * @return 加密后的token
     */
    public static String generateToken(String userId) {
        return JWT.create()
                .setPayload("userId", userId)       // 存放用户ID
                .sign(SIGNER);                      // 密钥签名（加密）
    }

    /**
     * 解析 Token + 校验签名 + 校验过期时间
     * @param token 前端传的token
     * @return 解析后的userId
     * @throws JWTException 校验失败会抛出异常
     */
    public static String parseToken(String token) {
        try{
            // 1. 先做完整验签（确保Token未被篡改）
            JWT jwt = JWT.of(token).setSigner(SIGNER);
            boolean verify = jwt.verify();
            if (!verify) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "Token 签名验证失败，可能已被篡改");
            }

            // 2. 解析并安全转换userId（解决 NumberWithFormat 异常）
            Object userIdObj = jwt.getPayload("userId");
            return userIdObj.toString();
        } catch (JWTException e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "Token 解析失败，请重新登录");
        }
    }
}