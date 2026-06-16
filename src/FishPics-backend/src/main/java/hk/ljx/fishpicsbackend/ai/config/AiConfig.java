package hk.ljx.fishpicsbackend.ai.config;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
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

    /** DashScope SDK HTTP 超时配置 */
    private static final int HTTP_CONNECT_TIMEOUT_MS = 10_000;
    private static final int HTTP_READ_TIMEOUT_MS = 190_000;

    @Bean
    public MultiModalConversation multiModalConversation() {
        // 配置 DashScope SDK 底层 HTTP 客户端超时
        // 确保 AI 调用超时后 HTTP 连接也能释放
        // DashScope SDK 当前版本可能不暴露 timeout setter，用反射调用避免编译失败
        try {
            Class<?> constantsClass = com.alibaba.dashscope.utils.Constants.class;
            try {
                constantsClass.getMethod("setHttpConnectTimeout", int.class)
                        .invoke(null, HTTP_CONNECT_TIMEOUT_MS);
                constantsClass.getMethod("setHttpReadTimeout", int.class)
                        .invoke(null, HTTP_READ_TIMEOUT_MS);
                log.info("[AiConfig] DashScope HTTP timeout configured: connect={}ms, read={}ms",
                        HTTP_CONNECT_TIMEOUT_MS, HTTP_READ_TIMEOUT_MS);
            } catch (NoSuchMethodException nsme) {
                // 当前 SDK 版本不暴露这些方法,降级用 SDK 默认
                log.info("[AiConfig] DashScope SDK 当前版本不暴露 HTTP timeout setter, 使用 SDK 默认");
            }
        } catch (Exception e) {
            log.warn("[AiConfig] Failed to configure DashScope HTTP timeout, using SDK defaults", e);
        }
        return new MultiModalConversation();
    }

    /**
     * AI 任务专用线程池
     * 专用线程池隔离 AI 任务，避免占满 ForkJoinPool.commonPool
     */
    @Bean(name = "aiTaskExecutor", destroyMethod = "shutdown")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-task-");
        // CallerRunsPolicy 会阻塞任务提交线程，AI 调用耗时较长时不合适。
        // 改为 AbortPolicy，由调用方捕获 RejectedExecutionException 标记任务 FAILED
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
