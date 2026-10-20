package com.chunyu.pregnancy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 小程序用户端入口。管理后台将是独立程序，不在此增加管理端登录入口。 */
@SpringBootApplication
@MapperScan("com.chunyu.pregnancy.mapper")
public class PregnancyServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PregnancyServerApplication.class, args);
    }
}
