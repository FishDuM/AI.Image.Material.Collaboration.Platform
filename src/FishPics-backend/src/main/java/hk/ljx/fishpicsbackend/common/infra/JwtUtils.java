package hk.ljx.fishpicsbackend.common.infra;

import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    private static final long JWT_EXPIRE_MS = 7 * 24 * 60 * 60 * 1000L;       // 7 天
    private static final long RENEW_THRESHOLD_MS = 3 * 24 * 60 * 60 * 1000L;  // 3 天

    private static final String JWT_BLACKLIST_PREFIX = RedisConstants.JWT_BLACKLIST_KEY;
    public static final String ISSUED_AT_MS_CLAIM = "iatMs";

    private SecretKey cachedSecretKey;

    private final StringRedisTemplate stringRedisTemplate;

    public JwtUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("jwt.secret 配置缺失或长度不足 32 字节，请在 application.yml 中配置");
        }
        this.cachedSecretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSecretKey() {
        return cachedSecretKey;
    }

    public String sign(Long userId) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + JWT_EXPIRE_MS);

        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim(ISSUED_AT_MS_CLAIM, now.getTime())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSecretKey())
                .compact();
    }

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

    public String getJti(String jwt) {
        Claims claims = parse(jwt);
        return claims != null ? claims.getId() : null;
    }

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

    public boolean isExpired(String jwt) {
        try {
            Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt);
            return false;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void addToBlacklist(String jwt) {
        String jti;
        long ttl = 60_000L;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            jti = claims.getId();
            Date exp = claims.getExpiration();
            if (exp != null) {
                ttl = Math.max(exp.getTime() - System.currentTimeMillis(), 60_000L);
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            jti = e.getClaims().getId();
        } catch (Exception e) {
            return;
        }
        if (jti == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                JWT_BLACKLIST_PREFIX + jti, "1", ttl, TimeUnit.MILLISECONDS);
        log.debug("JWT 已加入黑名单: jti={}", jti);
    }

    public boolean isBlacklisted(String jwt) {
        String jti = getJtiAllowExpired(jwt);
        if (jti == null) {
            return true;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(JWT_BLACKLIST_PREFIX + jti));
    }

    public static String extractJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isBlank(authHeader)) {
            return null;
        }
        return authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    }
}
