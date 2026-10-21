package com.chunyu.pregnancy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 小程序用户端入口。管理后台由另一个独立 Java 服务承载，不在此增加管理端登录入口。 */
// 启动注解：启用自动配置，并从当前包向下扫描 Controller、Service 等 Spring 组件。
@SpringBootApplication
// 扫描指定 Mapper 包，交给 MyBatis 创建接口代理；不用手写 Mapper 实现类。
@MapperScan("com.chunyu.pregnancy.mapper")
public class PregnancyServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PregnancyServerApplication.class, args);
    }
}
