package com.example.qrlogin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.example.qrlogin.mapper")
@SpringBootApplication
public class QrLoginApplication {
    public static void main(String[] args) {
        SpringApplication.run(QrLoginApplication.class, args);
    }
}
