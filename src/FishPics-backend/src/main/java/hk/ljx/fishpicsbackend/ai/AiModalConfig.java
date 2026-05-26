package hk.ljx.fishpicsbackend.ai;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModalConfig {

    @Value("${modal.tagModal}")
    private String tagModel;

    @Value("${modal.textModal}")
    private String descModel;

    @Value("${modal.baseImageModal}")
    private String baseImageModel;

    @Value("${modal.VIPImageModal}")
    private String vipImageModel;

    /**
     * ai 自动打图片标签
     */
    @Bean("tagModel")
    public DashScopeChatModel tagModel() {
        DashScopeChatOptions tagModelOptions = DashScopeChatOptions.builder()
                .model(tagModel).build();
        return DashScopeChatModel.builder().defaultOptions(tagModelOptions).build();
    }

    /**
     * ai 自动生成图片描述
     */
    @Bean("descModel")
    public DashScopeChatModel descModel() {
        DashScopeChatOptions descModelOptions = DashScopeChatOptions.builder()
                .model(descModel).build();
        return DashScopeChatModel.builder().defaultOptions(descModelOptions).build();
    }

    /**
     * ai 自动生成图片
     */
    @Bean("baseImageModel")
    public DashScopeImageModel baseImageModel() {
        DashScopeImageOptions baseImageModelOptions = DashScopeImageOptions.builder()
                .model(baseImageModel).build();
        return DashScopeImageModel.builder().defaultOptions(baseImageModelOptions).build();
    }

    /**
     * ai 自动生成图片（VIP/SVIP用户使用）
     */
    @Bean("vipImageModel")
    public DashScopeImageModel vipImageModel() {
        DashScopeImageOptions vipImageModelOptions = DashScopeImageOptions.builder()
                .model(vipImageModel).build();
        return DashScopeImageModel.builder().defaultOptions(vipImageModelOptions).build();
    }
}
