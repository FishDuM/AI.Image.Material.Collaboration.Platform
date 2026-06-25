package hk.ljx.fishpicsbackend.ai.config;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 模块相关 Bean 配置
 *
 * MultiModalConversation 用单例 Bean 注入，避免每 new 一次导致底层 HTTP 客户端/线程泄漏
 */
@Slf4j
@Configuration
public class AiConfig {

    private static final int HTTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int HTTP_READ_TIMEOUT_MS = 190_000;

    @Bean
    public MultiModalConversation multiModalConversation() {
        try {
            Class<?> constantsClass = Constants.class;
            try {
                constantsClass.getMethod("setHttpConnectTimeout", int.class)
                        .invoke(null, HTTP_CONNECT_TIMEOUT_MS);
                constantsClass.getMethod("setHttpReadTimeout", int.class)
                        .invoke(null, HTTP_READ_TIMEOUT_MS);
                log.info("[AiConfig] DashScope HTTP 超时已配置: connect={}ms, read={}ms",
                        HTTP_CONNECT_TIMEOUT_MS, HTTP_READ_TIMEOUT_MS);
            } catch (NoSuchMethodException nsme) {
                log.info("[AiConfig] DashScope SDK 当前版本不暴露 HTTP timeout setter, 使用 SDK 默认");
            }
        } catch (Exception e) {
            log.warn("[AiConfig] DashScope HTTP 超时配置失败, 使用 SDK 默认值", e);
        }
        return new MultiModalConversation();
    }

    @Bean(name = "aiTaskExecutor", destroyMethod = "shutdown")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
