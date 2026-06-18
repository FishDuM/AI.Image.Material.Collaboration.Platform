package hk.ljx.fishpicsbackend.user.component;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.RedisAtomicOps;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.util.concurrent.TimeUnit;

@Component
public class CaptchaManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisAtomicOps redisAtomicOps;

    public String getCheckCode(String redisKey, Integer len, Integer minute) {
        int actualLen = len == null ? 4 : len;
        int actualMinute = minute == null ? 5 : minute;

        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, actualLen, 20);
        captcha.setFont(new Font("Monospaced", Font.BOLD, 80));

        stringRedisTemplate.opsForValue().set(redisKey, captcha.getCode(), actualMinute, TimeUnit.MINUTES);
        return captcha.getImageBase64();
    }

    public void verifyRegisterCode(String captchaKey, String checkCode) {
        verifyAndConsume(RedisConstants.getRegisterCodeKey(captchaKey), checkCode);
    }

    public void verifyLoginCode(String captchaKey, String checkCode) {
        verifyAndConsume(RedisConstants.getLoginCodeKey(captchaKey), checkCode);
    }

    private void verifyAndConsume(String redisKey, String checkCode) {
        String cachedCode = redisAtomicOps.getAndDelete(redisKey);
        ExcUtils.throwIfTrue(cachedCode == null || !checkCode.equalsIgnoreCase(cachedCode),
                ExceptionCode.PARAMETER_ERROR, "验证码错误");
    }
}
