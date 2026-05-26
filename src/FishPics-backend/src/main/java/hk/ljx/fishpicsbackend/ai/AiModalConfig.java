package hk.ljx.fishpicsbackend.ai;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModalConfig {

    private final DashScopeApi dashScopeApi;
    private final DashScopeImageApi dashScopeImageApi;

    public AiModalConfig(DashScopeConnectionProperties properties) {
        this.dashScopeApi = DashScopeApi.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .build();
        this.dashScopeImageApi = DashScopeImageApi.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Value("${modal.tagModal}")
    private String tagModel;
    @Value("${modal.textModal}")
    private String descModel;
    @Value("${modal.baseImageModal}")
    private String baseImageModel;
    @Value("${modal.VIPImageModal}")
    private String vipImageModel;

    @Bean("tagModel")
    public DashScopeChatModel tagModel() {
        DashScopeChatOptions tagModelOptions = DashScopeChatOptions.builder().model(tagModel).build();
        return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).defaultOptions(tagModelOptions).build();
    }

    @Bean("descModel")
    public DashScopeChatModel descModel() {
        DashScopeChatOptions descModelOptions = DashScopeChatOptions.builder().model(descModel).build();
        return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).defaultOptions(descModelOptions).build();
    }

    @Bean("baseImageModel")
    public DashScopeImageModel baseImageModel() {
        DashScopeImageOptions baseImageModelOptions = DashScopeImageOptions.builder().model(baseImageModel).build();
        return DashScopeImageModel.builder().dashScopeApi(dashScopeImageApi).defaultOptions(baseImageModelOptions).build();
    }

    @Bean("vipImageModel")
    public DashScopeImageModel vipImageModel() {
        DashScopeImageOptions vipImageModelOptions = DashScopeImageOptions.builder().model(vipImageModel).build();
        return DashScopeImageModel.builder().dashScopeApi(dashScopeImageApi).defaultOptions(vipImageModelOptions).build();
    }
}
