package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JWT 工具类
 * 功能：签发、解析、续签、黑名单
 *
 * 安全要求：
 * - 密钥必须通过配置文件注入，禁止使用默认值
 * - 密钥长度至少 32 字节
 */
@Slf4j
@Component
public class JwtUtils {

    /**
     * JWT 密钥（从配置读取）
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * JWT 有效期：30 分钟（毫秒）
     */
    private static final long JWT_EXPIRE_MS = 30 * 60 * 1000L;

    /**
     * 续签阈值：超过 15 分钟自动续签（毫秒）
     */
    private static final long RENEW_THRESHOLD_MS = 15 * 60 * 1000L;

    /**
     * JWT 黑名单 Redis Key 前缀（使用共享常量，避免重复定义）
     */
    private static final String JWT_BLACKLIST_PREFIX = RedisConstants.JWT_BLACKLIST_KEY;

    /**
     * 缓存的签名密钥（避免重复创建）
     */
    private SecretKey cachedSecretKey;

    private final StringRedisTemplate stringRedisTemplate;

    public JwtUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 初始化：验证密钥配置并缓存密钥对象
     */
    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("jwt.secret 配置缺失或长度不足 32 字节，请在 application.yml 中配置");
        }
        this.cachedSecretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT 工具初始化完成，密钥长度: {} 字节", secret.length());
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSecretKey() {
        return cachedSecretKey;
    }

    /**
     * 签发 JWT
     *
     * @param userId 用户ID
     * @return JWT 字符串
     */
    public String sign(Long userId) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + JWT_EXPIRE_MS);

        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 解析 JWT（不做黑名单检查，用于拦截器快速解析）
     *
     * @param jwt JWT 字符串
     * @return Claims，解析失败返回 null
     */
    public Claims parse(String jwt) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return null;
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.warn("JWT 签名验证失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JWT 中提取 userId
     *
     * @param jwt JWT 字符串
     * @return userId，失败返回 null
     */
    public Long getUserId(String jwt) {
        Claims claims = parse(jwt);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    /**
     * 从 JWT 中提取 jti
     *
     * @param jwt JWT 字符串
     * @return jti，失败返回 null
     */
    public String getJti(String jwt) {
        Claims claims = parse(jwt);
        return claims != null ? claims.getId() : null;
    }

    /**
     * 从 JWT 中提取 userId（允许过期的 JWT，用于登出场景）
     *
     * @param jwt JWT 字符串
     * @return userId，失败返回 null
     */
    public Long getUserIdAllowExpired(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            Object userId = claims.get("userId");
            return userId instanceof Number ? ((Number) userId).longValue() : null;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            Object userId = e.getClaims().get("userId");
            return userId instanceof Number ? ((Number) userId).longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JWT 中提取 jti（允许过期的 JWT，用于登出场景的黑名单加入）
     *
     * @param jwt JWT 字符串
     * @return jti，失败返回 null
     */
    public String getJtiAllowExpired(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            return claims.getId();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return e.getClaims().getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断 JWT 是否需要续签（超过 15 分钟）
     *
     * @param jwt JWT 字符串
     * @return true = 需要续签
     */
    public boolean shouldRenew(String jwt) {
        Claims claims = parse(jwt);
        if (claims == null) {
            return false;
        }
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - issuedAt.getTime();
        return elapsed >= RENEW_THRESHOLD_MS;
    }

    /**
     * 判断 JWT 是否已过期
     * 仅在 ExpiredJwtException 时返回 true，签名无效等其他错误返回 false
     *
     * @param jwt JWT 字符串
     * @return true = 已过期，false = 未过期或解析失败（非过期原因）
     */
    public boolean isExpired(String jwt) {
        try {
            Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt);
            return false; // 解析成功，未过期
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return true; // 确实过期
        } catch (Exception e) {
            return false; // 签名无效、格式损坏等，不是"过期"
        }
    }

    /**
     * 将 JWT 加入黑名单（登出时调用，支持过期 JWT）
     *
     * @param jwt JWT 字符串
     */
    public void addToBlacklist(String jwt) {
        String jti = getJtiAllowExpired(jwt);
        if (jti == null) {
            return;
        }
        // 对于已过期的 JWT，TTL 设为 1 分钟（确保在 Redis 中短暂记录以防并发使用）
        // 对于未过期的 JWT，TTL = JWT 剩余有效期
        long ttl = 60_000L; // 默认 1 分钟
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            Date exp = claims.getExpiration();
            if (exp != null) {
                ttl = Math.max(exp.getTime() - System.currentTimeMillis(), 60_000L);
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // 已过期，使用默认 1 分钟 TTL
        } catch (Exception e) {
            // 解析失败，使用默认 TTL
        }
        stringRedisTemplate.opsForValue().set(
                JWT_BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.MILLISECONDS);
        log.info("JWT 已加入黑名单: jti={}", jti);
    }

    /**
     * 检查 JWT 是否在黑名单中
     * 注意：使用 getJtiAllowExpired 提取 jti，避免过期 Token 被误判为黑名单
     *
     * @param jwt JWT 字符串
     * @return true = 在黑名单中（已登出），或无法解析 jti（视为无效）
     */
    public boolean isBlacklisted(String jwt) {
        String jti = getJtiAllowExpired(jwt);
        if (jti == null) {
            return true; // 无法提取 jti，视为无效
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(JWT_BLACKLIST_PREFIX + jti));
    }
}
