package hk.ljx.fishpicsbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FishPicsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishPicsBackendApplication.class, args);
    }

}
