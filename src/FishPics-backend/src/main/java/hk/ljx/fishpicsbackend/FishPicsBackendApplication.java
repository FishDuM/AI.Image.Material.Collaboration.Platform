package hk.ljx.fishpicsbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("hk.ljx.fishpicsbackend.mapper")
public class FishPicsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishPicsBackendApplication.class, args);
    }

}
