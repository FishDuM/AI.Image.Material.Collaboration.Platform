package hk.ljx.fishpicsbackend;

import hk.ljx.fishpicsbackend.common.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class FishPicsBackendApplicationTests {


    @Test
    void contextLoads() {
        String s = JwtUtil.generateToken("123456");
        System.out.println(s);
        String i = JwtUtil.parseToken(s);
        System.out.println(i);

    }

}
