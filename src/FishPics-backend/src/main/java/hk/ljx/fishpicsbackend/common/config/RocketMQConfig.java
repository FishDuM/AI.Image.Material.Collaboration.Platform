package hk.ljx.fishpicsbackend.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RocketMQConfig {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    @PostConstruct
    public void init() {
        log.info("RocketMQ 配置已加载 — name-server: {}, producer-group: {}", nameServer, producerGroup);
    }
}
