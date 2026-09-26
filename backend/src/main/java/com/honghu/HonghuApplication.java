package com.honghu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.honghu.mapper")
public class HonghuApplication {

    public static void main(String[] args) {
        SpringApplication.run(HonghuApplication.class, args);
    }
}
