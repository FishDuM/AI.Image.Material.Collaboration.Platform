package hk.ljx.fishpicsbackend.common.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class COSConfig {
    @Value("${cos.secretId}")
    private String secretId;

    @Value("${cos.secretKey}")
    private String secretKey;

    @Value("${cos.region}")
    private String region;

    @Bean(destroyMethod = "shutdown")
    COSClient createCOSClient() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setRegion(new Region(region));
        return new COSClient(cred, clientConfig);
    }

}
